package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "To location not found"));

        // 2. 동일 로케이션 체크
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new BusinessException("SAME_LOCATION", "From and to locations must be different");
        }

        // 3. 출발지 재고 조회
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, fromLocation, request.getLotNumber())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Source inventory not found"));

        // 4. 이동 수량 체크
        if (request.getQuantity() > sourceInventory.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock at source location");
        }

        // 5. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "From location is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "To location is frozen for cycle count");
        }

        // 6. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED", "Destination location capacity exceeded");
        }

        // 7. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 8. HAZMAT 혼적 금지 체크
        validateHazmatCompatibility(product, toLocation);

        // 9. 유통기한 임박 상품 이동 제한
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateTransfer(sourceInventory, toLocation);
        }

        // 10. 대량 이동 승인 체크
        StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.immediate;
        double transferPct = (request.getQuantity() * 100.0) / sourceInventory.getQuantity();
        if (transferPct >= 80.0) {
            transferStatus = StockTransfer.TransferStatus.pending_approval;
        }

        // 11. StockTransfer 레코드 생성
        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .reason(request.getReason())
                .transferStatus(transferStatus)
                .transferredBy(request.getTransferredBy())
                .build();

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        // 12. 즉시 이동인 경우 재고 반영
        if (transferStatus == StockTransfer.TransferStatus.immediate) {
            performTransfer(savedTransfer, sourceInventory);
        }

        return mapToResponse(savedTransfer);
    }

    /**
     * 대량 이동 승인
     */
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        // 출발지 재고 재조회
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(
                        transfer.getProduct(),
                        transfer.getFromLocation(),
                        transfer.getLotNumber()
                )
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Source inventory not found"));

        // 재고 이동 실행
        performTransfer(transfer, sourceInventory);

        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        return mapToResponse(savedTransfer);
    }

    /**
     * 대량 이동 거부
     */
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        return mapToResponse(savedTransfer);
    }

    /**
     * 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Transfer not found"));

        return mapToResponse(transfer);
    }

    /**
     * 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        List<StockTransfer> transfers = stockTransferRepository.findAll();
        return transfers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 실제 재고 이동 수행
     */
    private void performTransfer(StockTransfer transfer, Inventory sourceInventory) {
        Product product = transfer.getProduct();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        int quantity = transfer.getQuantity();

        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 current_qty 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 있으면 증가, 없으면 생성)
        Inventory destInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, toLocation, transfer.getLotNumber())
                .orElse(Inventory.builder()
                        .product(product)
                        .location(toLocation)
                        .lotNumber(transfer.getLotNumber())
                        .quantity(0)
                        .expiryDate(sourceInventory.getExpiryDate())
                        .manufactureDate(sourceInventory.getManufactureDate())
                        .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지 (FIFO 보존)
                        .isExpired(sourceInventory.getIsExpired())
                        .build());

        destInventory.setQuantity(destInventory.getQuantity() + quantity);
        inventoryRepository.save(destInventory);

        // 4. 도착지 로케이션 current_qty 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone 기준)
        checkSafetyStockAfterTransfer(product);
    }

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = toLocation.getStorageType();

        // HAZMAT은 일반 로케이션 이동 허용 (HAZMAT zone 우선 권장)
        // 더 이상 HAZMAT zone 강제하지 않음

        // FROZEN 상품 → AMBIENT 로케이션 거부
        if (productType == Product.StorageType.FROZEN && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "FROZEN products cannot be moved to AMBIENT location");
        }

        // COLD 상품 → AMBIENT 로케이션 거부
        if (productType == Product.StorageType.COLD && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "COLD products cannot be moved to AMBIENT location");
        }

        // AMBIENT 상품 → COLD/FROZEN 허용 (상위 호환)
    }

    /**
     * HAZMAT 혼적 금지 검증
     */
    private void validateHazmatCompatibility(Product product, Location toLocation) {
        boolean isHazmat = (product.getCategory() == Product.ProductCategory.HAZMAT);

        // 도착지에 기존 재고가 있는지 확인
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        for (Inventory inv : existingInventories) {
            if (inv.getQuantity() > 0) {
                boolean existingIsHazmat = (inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT);

                // HAZMAT과 비-HAZMAT 혼적 금지
                if (isHazmat != existingIsHazmat) {
                    throw new BusinessException("HAZMAT_MIXING_FORBIDDEN",
                            "HAZMAT and non-HAZMAT products cannot be stored in the same location");
                }
            }
        }
    }

    /**
     * 유통기한 임박 상품 이동 제한 검증
     */
    private void validateExpiryDateTransfer(Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();

        if (expiryDate == null || manufactureDate == null) {
            return;
        }

        // 유통기한 만료 체크
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("EXPIRED_PRODUCT", "Expired products cannot be transferred");
        }

        // 잔여 유통기한 비율 계산
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return;
        }

        double remainingPct = (remainingDays * 100.0) / totalDays;

        // 잔여 유통기한 < 10% → SHIPPING zone만 허용
        if (remainingPct < 10) {
            if (toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("EXPIRY_TRANSFER_RESTRICTED",
                        "Products with less than 10% shelf life can only be moved to SHIPPING zone");
            }
        }
    }

    /**
     * 이동 후 안전재고 체크
     */
    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        List<Location> storageLocations = locationRepository.findByZone(Location.Zone.STORAGE);
        int totalStorageQty = 0;

        for (Location loc : storageLocations) {
            List<Inventory> inventories = inventoryRepository.findByProductAndLocation(product, loc);
            for (Inventory inv : inventories) {
                if (!inv.getIsExpired()) {
                    totalStorageQty += inv.getQuantity();
                }
            }
        }

        // 안전재고 규칙 조회
        safetyStockRuleRepository.findByProduct(product).ifPresent(rule -> {
            if (totalStorageQty <= rule.getMinQty()) {
                // 자동 재발주 요청 기록
                AutoReorderLog log = AutoReorderLog.builder()
                        .product(product)
                        .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                        .currentStock(totalStorageQty)
                        .minQty(rule.getMinQty())
                        .reorderQty(rule.getReorderQty())
                        .triggeredBy("SYSTEM")
                        .build();

                autoReorderLogRepository.save(log);
            }
        });
    }

    /**
     * Entity -> Response DTO 변환
     */
    private StockTransferResponse mapToResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .transferId(transfer.getTransferId())
                .productId(transfer.getProduct().getProductId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getLocationId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getLocationId())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .lotNumber(transfer.getLotNumber())
                .reason(transfer.getReason())
                .transferStatus(transfer.getTransferStatus().name())
                .transferredBy(transfer.getTransferredBy())
                .approvedBy(transfer.getApprovedBy())
                .transferredAt(transfer.getTransferredAt())
                .build();
    }
}
