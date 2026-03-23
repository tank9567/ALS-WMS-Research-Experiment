package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.entity.Location.Zone;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.StockTransfer.TransferStatus;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행
     */
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // 1. 기본 검증
        validateBasicRequest(request);

        // 2. Entity 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("From location not found", "FROM_LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("To location not found", "TO_LOCATION_NOT_FOUND"));

        // 3. 동일 로케이션 체크
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new BusinessException("Cannot transfer to the same location", "SAME_LOCATION");
        }

        // 4. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("From location is frozen for cycle count", "FROM_LOCATION_FROZEN");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("To location is frozen for cycle count", "TO_LOCATION_FROZEN");
        }

        // 5. 재고 조회
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                product.getId(),
                fromLocation.getId(),
                request.getLotNumber(),
                request.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        // 6. 재고 부족 체크
        if (sourceInventory.getQuantity() < request.getTransferQty()) {
            throw new BusinessException(
                    String.format("Insufficient inventory: available %d, requested %d",
                            sourceInventory.getQuantity(), request.getTransferQty()),
                    "INSUFFICIENT_INVENTORY"
            );
        }

        // 7. 보관 유형 호환성 체크
        if (!isStorageTypeCompatible(product.getStorageType(), toLocation.getStorageType(), product.getCategory(), toLocation.getZone())) {
            throw new BusinessException(
                    String.format("Storage type incompatible: product %s cannot be moved to location %s",
                            product.getStorageType(), toLocation.getStorageType()),
                    "STORAGE_TYPE_INCOMPATIBLE"
            );
        }

        // 8. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 9. 유통기한 임박 상품 이동 제한
        if (product.getManagesExpiry() && request.getExpiryDate() != null) {
            validateExpiryConstraints(request.getExpiryDate(), sourceInventory.getManufactureDate(), toLocation.getZone());
        }

        // 10. 도착지 로케이션 용량 체크
        int newToLocationQty = toLocation.getCurrentQty() + request.getTransferQty();
        if (newToLocationQty > toLocation.getCapacity()) {
            throw new BusinessException(
                    String.format("To location capacity exceeded: current %d + transfer %d > capacity %d",
                            toLocation.getCurrentQty(), request.getTransferQty(), toLocation.getCapacity()),
                    "TO_LOCATION_CAPACITY_EXCEEDED"
            );
        }

        // 11. 대량 이동 승인 체크 (80% 이상)
        boolean requiresApproval = isLargeTransfer(sourceInventory.getQuantity(), request.getTransferQty());

        // 12. StockTransfer 엔티티 생성
        TransferStatus status = requiresApproval ? TransferStatus.PENDING_APPROVAL : TransferStatus.IMMEDIATE;

        StockTransfer stockTransfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .transferQty(request.getTransferQty())
                .transferStatus(status)
                .reason(request.getReason())
                .requestedBy(request.getRequestedBy())
                .transferredAt(OffsetDateTime.now())
                .build();

        stockTransferRepository.save(stockTransfer);

        // 13. 즉시 이동인 경우 재고 반영
        if (status == TransferStatus.IMMEDIATE) {
            executeInventoryMovement(stockTransfer, sourceInventory);
            checkSafetyStockAfterTransfer(product);
        }

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 승인
     */
    public StockTransferResponse approveTransfer(UUID id, String approvedBy) {
        StockTransfer stockTransfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Stock transfer not found", "TRANSFER_NOT_FOUND"));

        if (stockTransfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only approve transfers in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        // 재고 조회 (승인 시점)
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                stockTransfer.getProduct().getId(),
                stockTransfer.getFromLocation().getId(),
                stockTransfer.getLotNumber(),
                stockTransfer.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        // 재고 부족 재확인
        if (sourceInventory.getQuantity() < stockTransfer.getTransferQty()) {
            throw new BusinessException(
                    String.format("Insufficient inventory at approval time: available %d, requested %d",
                            sourceInventory.getQuantity(), stockTransfer.getTransferQty()),
                    "INSUFFICIENT_INVENTORY"
            );
        }

        // 재고 반영
        executeInventoryMovement(stockTransfer, sourceInventory);

        // 상태 업데이트
        stockTransfer.setTransferStatus(TransferStatus.APPROVED);
        stockTransfer.setApprovedBy(approvedBy);
        stockTransfer.setApprovedAt(OffsetDateTime.now());
        stockTransferRepository.save(stockTransfer);

        // 안전재고 체크
        checkSafetyStockAfterTransfer(stockTransfer.getProduct());

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 거부
     */
    public StockTransferResponse rejectTransfer(UUID id, String approvedBy) {
        StockTransfer stockTransfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Stock transfer not found", "TRANSFER_NOT_FOUND"));

        if (stockTransfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only reject transfers in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        stockTransfer.setTransferStatus(TransferStatus.REJECTED);
        stockTransfer.setApprovedBy(approvedBy);
        stockTransfer.setApprovedAt(OffsetDateTime.now());
        stockTransferRepository.save(stockTransfer);

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID id) {
        StockTransfer stockTransfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Stock transfer not found", "TRANSFER_NOT_FOUND"));

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public Page<StockTransferResponse> getTransfers(Pageable pageable) {
        Page<StockTransfer> transfers = stockTransferRepository.findAll(pageable);
        return transfers.map(this::buildResponse);
    }

    // ===== Private Helper Methods =====

    /**
     * 기본 요청 검증
     */
    private void validateBasicRequest(StockTransferRequest request) {
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", "PRODUCT_ID_REQUIRED");
        }
        if (request.getFromLocationId() == null) {
            throw new BusinessException("From location ID is required", "FROM_LOCATION_ID_REQUIRED");
        }
        if (request.getToLocationId() == null) {
            throw new BusinessException("To location ID is required", "TO_LOCATION_ID_REQUIRED");
        }
        if (request.getTransferQty() == null || request.getTransferQty() <= 0) {
            throw new BusinessException("Transfer quantity must be positive", "INVALID_TRANSFER_QTY");
        }
    }

    /**
     * 보관 유형 호환성 체크
     */
    private boolean isStorageTypeCompatible(
            StorageType productType,
            StorageType locationType,
            ProductCategory productCategory,
            Zone locationZone
    ) {
        // FROZEN → AMBIENT/COLD: 거부 (품질 위험)
        if (productType == StorageType.FROZEN) {
            return locationType == StorageType.FROZEN;
        }

        // COLD → AMBIENT: 거부
        if (productType == StorageType.COLD) {
            return locationType == StorageType.COLD || locationType == StorageType.FROZEN;
        }

        // AMBIENT → COLD/FROZEN: 허용 (상위 호환)
        if (productType == StorageType.AMBIENT) {
            return true;
        }

        return false;
    }

    /**
     * HAZMAT 혼적 금지 검증
     */
    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지 로케이션에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findByLocationId(toLocation.getId());

        boolean isHazmat = product.getCategory() == ProductCategory.HAZMAT;

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT의 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new BusinessException(
                        "Cannot mix HAZMAT with non-HAZMAT products in the same location",
                        "HAZMAT_SEGREGATION_VIOLATION"
                );
            }
            if (!isHazmat && existingIsHazmat) {
                throw new BusinessException(
                        "Cannot mix non-HAZMAT with HAZMAT products in the same location",
                        "HAZMAT_SEGREGATION_VIOLATION"
                );
            }
        }
    }

    /**
     * 유통기한 제약 검증
     */
    private void validateExpiryConstraints(LocalDate expiryDate, LocalDate manufactureDate, Zone toLocationZone) {
        LocalDate today = LocalDate.now();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("Cannot transfer expired products", "EXPIRED_PRODUCT");
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

            if (totalShelfLife > 0) {
                double remainingPct = (remainingShelfLife * 100.0) / totalShelfLife;

                // 잔여 유통기한 < 10%: SHIPPING zone만 허용
                if (remainingPct < 10) {
                    if (toLocationZone != Zone.SHIPPING) {
                        throw new BusinessException(
                                String.format("Products with <10%% shelf life can only be moved to SHIPPING zone (current: %.1f%%)",
                                        remainingPct),
                                "SHELF_LIFE_CONSTRAINT"
                        );
                    }
                }
            }
        }
    }

    /**
     * 대량 이동 여부 확인 (80% 이상)
     */
    private boolean isLargeTransfer(int currentQty, int transferQty) {
        return transferQty >= (currentQty * 0.8);
    }

    /**
     * 재고 이동 실행 (출발지 감소, 도착지 증가)
     */
    private void executeInventoryMovement(StockTransfer stockTransfer, Inventory sourceInventory) {
        // 1. 출발지 재고 감소
        int remainingQty = sourceInventory.getQuantity() - stockTransfer.getTransferQty();
        sourceInventory.setQuantity(remainingQty);
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 적재량 감소
        Location fromLocation = stockTransfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - stockTransfer.getTransferQty());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 재고가 있으면 증가, 없으면 신규 생성)
        Inventory targetInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                stockTransfer.getProduct().getId(),
                stockTransfer.getToLocation().getId(),
                stockTransfer.getLotNumber(),
                stockTransfer.getExpiryDate()
        ).orElse(null);

        if (targetInventory == null) {
            targetInventory = Inventory.builder()
                    .product(stockTransfer.getProduct())
                    .location(stockTransfer.getToLocation())
                    .lotNumber(stockTransfer.getLotNumber())
                    .quantity(stockTransfer.getTransferQty())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .expiryDate(stockTransfer.getExpiryDate())
                    .receivedAt(sourceInventory.getReceivedAt())
                    .isExpired(false)
                    .build();
        } else {
            targetInventory.setQuantity(targetInventory.getQuantity() + stockTransfer.getTransferQty());
        }
        inventoryRepository.save(targetInventory);

        // 4. 도착지 로케이션 적재량 증가
        Location toLocation = stockTransfer.getToLocation();
        toLocation.setCurrentQty(toLocation.getCurrentQty() + stockTransfer.getTransferQty());
        locationRepository.save(toLocation);
    }

    /**
     * 이동 후 안전재고 체크
     */
    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventories = inventoryRepository.findByProductIdAndZone(
                product.getId(),
                Zone.STORAGE
        );

        int totalStorageQty = storageInventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule != null && totalStorageQty <= rule.getMinQty()) {
            // 자동 재발주 요청 기록
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStorageQty)
                    .reorderQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    /**
     * Response 빌드
     */
    private StockTransferResponse buildResponse(StockTransfer stockTransfer) {
        return StockTransferResponse.builder()
                .id(stockTransfer.getId())
                .productId(stockTransfer.getProduct().getId())
                .productSku(stockTransfer.getProduct().getSku())
                .productName(stockTransfer.getProduct().getName())
                .fromLocationId(stockTransfer.getFromLocation().getId())
                .fromLocationCode(stockTransfer.getFromLocation().getCode())
                .toLocationId(stockTransfer.getToLocation().getId())
                .toLocationCode(stockTransfer.getToLocation().getCode())
                .lotNumber(stockTransfer.getLotNumber())
                .expiryDate(stockTransfer.getExpiryDate())
                .transferQty(stockTransfer.getTransferQty())
                .transferStatus(stockTransfer.getTransferStatus())
                .reason(stockTransfer.getReason())
                .requestedBy(stockTransfer.getRequestedBy())
                .approvedBy(stockTransfer.getApprovedBy())
                .transferredAt(stockTransfer.getTransferredAt())
                .approvedAt(stockTransfer.getApprovedAt())
                .createdAt(stockTransfer.getCreatedAt())
                .updatedAt(stockTransfer.getUpdatedAt())
                .build();
    }
}
