package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
        // 1. 엔티티 조회 및 기본 검증
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "To location not found"));

        // 2. 동일 로케이션 체크
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new BusinessException("SAME_LOCATION", "From and to locations must be different");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "From location is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "To location is frozen for cycle count");
        }

        // 4. 출발지 재고 조회 및 수량 체크
        Inventory sourceInventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        product.getProductId(), fromLocation.getLocationId(), request.getLotNumber())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    String.format("Insufficient stock. Available: %d, Requested: %d",
                            sourceInventory.getQuantity(), request.getQuantity()));
        }

        // 5. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED",
                    String.format("Destination location capacity exceeded. Available: %d, Required: %d",
                            toLocation.getCapacity() - toLocation.getCurrentQty(), request.getQuantity()));
        }

        // 6. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestrictions(product, sourceInventory, toLocation);
        }

        // 8. 대량 이동 승인 체크 (80% 이상)
        boolean requiresApproval = false;
        double transferRatio = (double) request.getQuantity() / sourceInventory.getQuantity();
        if (transferRatio >= 0.8) {
            requiresApproval = true;
        }

        // 10. 재고 이동 이력 생성
        StockTransfer.TransferStatus status = requiresApproval ?
                StockTransfer.TransferStatus.PENDING_APPROVAL : StockTransfer.TransferStatus.IMMEDIATE;

        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .reason(request.getReason())
                .transferStatus(status)
                .transferredBy(request.getTransferredBy())
                .transferredAt(Instant.now())
                .build();

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        // 11. 승인이 필요하지 않으면 즉시 재고 이동 실행
        if (!requiresApproval) {
            executeTransfer(savedTransfer, sourceInventory);
        }

        log.info("Stock transfer created with ID: {} (status: {})", savedTransfer.getTransferId(), status);
        return convertToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse approveStockTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findByIdWithDetails(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        // 재고 조회 및 이동 실행
        Inventory sourceInventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        transfer.getProduct().getProductId(),
                        transfer.getFromLocation().getLocationId(),
                        transfer.getLotNumber())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        executeTransfer(transfer, sourceInventory);

        transfer.setTransferStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        log.info("Stock transfer {} approved by {}", transferId, approvedBy);
        return convertToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse rejectStockTransfer(UUID transferId, String rejectedBy, String reason) {
        StockTransfer transfer = stockTransferRepository.findByIdWithDetails(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setApprovedBy(rejectedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        log.info("Stock transfer {} rejected by {}. Reason: {}", transferId, rejectedBy, reason);
        return convertToResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getStockTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findByIdWithDetails(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));
        return convertToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public Page<StockTransferResponse> getAllStockTransfers(Pageable pageable) {
        return stockTransferRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ===== Helper Methods =====

    private void executeTransfer(StockTransfer transfer, Inventory sourceInventory) {
        Product product = transfer.getProduct();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        int quantity = transfer.getQuantity();

        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        if (sourceInventory.getQuantity() == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }

        // 2. 출발지 로케이션 current_qty 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (동일 product + lot_number 조합이 있으면 증가, 없으면 신규 생성)
        Inventory destInventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        product.getProductId(), toLocation.getLocationId(), transfer.getLotNumber())
                .orElse(null);

        if (destInventory != null) {
            destInventory.setQuantity(destInventory.getQuantity() + quantity);
            inventoryRepository.save(destInventory);
        } else {
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(toLocation)
                    .quantity(quantity)
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(sourceInventory.getExpiryDate())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지 (FIFO)
                    .isExpired(sourceInventory.getIsExpired())
                    .build();
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 current_qty 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone 기준)
        checkSafetyStockAfterTransfer(product);

        log.info("Transfer executed: {} units of product {} from {} to {}",
                quantity, product.getSku(), fromLocation.getCode(), toLocation.getCode());
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // FROZEN → AMBIENT: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "FROZEN products cannot be moved to AMBIENT locations");
        }

        // COLD → AMBIENT: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "COLD products cannot be moved to AMBIENT locations");
        }

        // AMBIENT → COLD/FROZEN: 허용 (상위 호환)
    }

    private void validateExpiryDateRestrictions(Product product, Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();
        LocalDate today = LocalDate.now();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("EXPIRED_PRODUCT", "Cannot move expired products");
        }

        // 잔여 유통기한 비율 계산
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (totalDays > 0) ? (remainingDays * 100.0 / totalDays) : 0.0;

            // 잔여 유통기한 < 10%: SHIPPING zone으로만 이동 허용
            if (remainingPct < 10.0 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("EXPIRY_RESTRICTION",
                        String.format("Products with less than 10%% shelf life can only be moved to SHIPPING zone. Remaining: %.2f%%", remainingPct));
            }
        }
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        int storageStock = inventoryRepository.sumQuantityByProductAndZone(
                product.getProductId(), Location.Zone.STORAGE);

        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule != null && storageStock <= rule.getMinQty()) {
            // 자동 재발주 로그 생성
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(storageStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock triggered for product {}: current={}, min={}",
                    product.getSku(), storageStock, rule.getMinQty());
        }
    }

    private StockTransferResponse convertToResponse(StockTransfer transfer) {
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
