package com.wms.service;

import com.wms.dto.StockTransferApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse createStockTransfer(StockTransferRequest request) {
        // 1. 기본 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "To location not found"));

        // 2. 기본 검증
        validateBasicRules(request, fromLocation, toLocation);

        // 3. 재고 조회
        Inventory sourceInventory = findInventory(request.getProductId(), request.getFromLocationId(), request.getLotNumber())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        // 4. 재고 부족 체크
        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    String.format("Insufficient stock. Available: %d, Requested: %d",
                            sourceInventory.getQuantity(), request.getQuantity()));
        }

        // 5. 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED",
                    String.format("Destination location capacity exceeded. Capacity: %d, Current: %d, Transfer: %d",
                            toLocation.getCapacity(), toLocation.getCurrentQty(), request.getQuantity()));
        }

        // 6. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 7. HAZMAT 혼적 금지
        validateHazmatSegregation(product, toLocation, request.getProductId());

        // 8. 유통기한 체크
        validateExpiryConstraints(product, toLocation, sourceInventory);

        // 9. 대량 이동 승인 체크
        StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.IMMEDIATE;
        if (isLargeTransfer(sourceInventory.getQuantity(), request.getQuantity())) {
            transferStatus = StockTransfer.TransferStatus.PENDING_APPROVAL;
        }

        // 10. 이동 기록 생성
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
        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 이동 실행
        if (transferStatus == StockTransfer.TransferStatus.IMMEDIATE) {
            executeTransfer(transfer, sourceInventory);
        }

        return buildTransferResponse(transfer);
    }

    private void validateBasicRules(StockTransferRequest request, Location fromLocation, Location toLocation) {
        // 동일 로케이션 체크
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new BusinessException("SAME_LOCATION", "From location and to location cannot be the same");
        }

        // 실사 동결 체크
        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new BusinessException("LOCATION_FROZEN", "From location is frozen for cycle count");
        }
        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new BusinessException("LOCATION_FROZEN", "To location is frozen for cycle count");
        }
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot transfer FROZEN product to AMBIENT location");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot transfer COLD product to AMBIENT location");
        }
    }

    private void validateHazmatSegregation(Product product, Location toLocation, UUID productId) {
        // 도착지에 이미 적재된 재고 조회
        List<Inventory> existingInventory = inventoryRepository.findByLocationLocationId(toLocation.getLocationId());

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory inv : existingInventory) {
            // 자기 자신은 제외
            if (inv.getProduct().getProductId().equals(productId)) {
                continue;
            }

            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot mix HAZMAT product with non-HAZMAT products");
            }
            if (!isHazmat && existingIsHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot mix non-HAZMAT product with HAZMAT products");
            }
        }
    }

    private void validateExpiryConstraints(Product product, Location toLocation, Inventory sourceInventory) {
        if (!Boolean.TRUE.equals(product.getHasExpiry()) || sourceInventory.getExpiryDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("EXPIRED_PRODUCT", "Cannot transfer expired product");
        }

        // 잔여 유통기한 비율 계산
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (totalDays > 0) ? (remainingDays * 100.0 / totalDays) : 0;

            // 잔여 유통기한 < 10%: SHIPPING zone으로만 허용
            if (remainingPct < 10 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("NEAR_EXPIRY_RESTRICTION",
                        String.format("Product with %.1f%% remaining shelf life can only be transferred to SHIPPING zone",
                                remainingPct));
            }
        }
    }

    private boolean isLargeTransfer(int totalQuantity, int transferQuantity) {
        return transferQuantity >= (totalQuantity * 0.8);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, StockTransferApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        // 승인 처리
        transfer.setTransferStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        // 재고 이동 실행
        Inventory sourceInventory = findInventory(
                transfer.getProduct().getProductId(),
                transfer.getFromLocation().getLocationId(),
                transfer.getLotNumber()
        ).orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        executeTransfer(transfer, sourceInventory);

        return buildTransferResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, StockTransferApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        // 거부 처리
        transfer.setTransferStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        return buildTransferResponse(transfer);
    }

    private void executeTransfer(StockTransfer transfer, Inventory sourceInventory) {
        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - transfer.getQuantity());
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 current_qty 차감
        Location fromLocation = transfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 재고가 있으면 증가, 없으면 새로 생성)
        Optional<Inventory> destInventoryOpt = findInventory(
                transfer.getProduct().getProductId(),
                transfer.getToLocation().getLocationId(),
                transfer.getLotNumber()
        );

        if (destInventoryOpt.isPresent()) {
            Inventory destInventory = destInventoryOpt.get();
            destInventory.setQuantity(destInventory.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(destInventory);
        } else {
            Inventory newInventory = Inventory.builder()
                    .product(transfer.getProduct())
                    .location(transfer.getToLocation())
                    .quantity(transfer.getQuantity())
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(sourceInventory.getExpiryDate())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지
                    .isExpired(sourceInventory.getIsExpired())
                    .build();
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 current_qty 증가
        Location toLocation = transfer.getToLocation();
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone)
        checkSafetyStockAfterTransfer(transfer.getProduct());
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventory = inventoryRepository.findByProductProductId(product.getProductId()).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .collect(Collectors.toList());

        int totalStorageStock = storageInventory.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductProductId(product.getProductId());
        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();
            if (totalStorageStock <= rule.getMinQty()) {
                // 자동 재발주 로그 기록
                AutoReorderLog log = AutoReorderLog.builder()
                        .product(product)
                        .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                        .currentStock(totalStorageStock)
                        .minQty(rule.getMinQty())
                        .reorderQty(rule.getReorderQty())
                        .triggeredBy("SYSTEM")
                        .build();
                autoReorderLogRepository.save(log);
                log.info("Safety stock triggered for product: {}, current stock: {}, min qty: {}",
                        product.getSku(), totalStorageStock, rule.getMinQty());
            }
        }
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getStockTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
        return buildTransferResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllStockTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::buildTransferResponse)
                .collect(Collectors.toList());
    }

    private Optional<Inventory> findInventory(UUID productId, UUID locationId, String lotNumber) {
        return inventoryRepository.findByProductProductIdAndLocationLocationIdAndLotNumber(
                productId, locationId, lotNumber);
    }

    private StockTransferResponse buildTransferResponse(StockTransfer transfer) {
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
