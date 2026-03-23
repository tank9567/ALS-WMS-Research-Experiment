package com.wms.service;

import com.wms.dto.StockTransferListResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // 1. Inventory 조회
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Inventory not found"));

        Location fromLocation = inventory.getLocation();
        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "To location not found"));

        Product product = inventory.getProduct();

        // 2. 기본 검증
        validateBasicRules(inventory, fromLocation, toLocation, request.getQuantity());

        // 3. 보관 유형 호환성 검증
        validateStorageTypeCompatibility(product, toLocation);

        // 4. HAZMAT 혼적 금지 검증
        validateHazmatSegregation(product, toLocation);

        // 5. 유통기한 임박 상품 이동 제한
        validateExpiryRestrictions(inventory, toLocation);

        // 6. 대량 이동 승인 체크
        StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.IMMEDIATE;
        double transferPct = ((double) request.getQuantity() / inventory.getQuantity()) * 100;
        if (transferPct >= 80) {
            transferStatus = StockTransfer.TransferStatus.PENDING_APPROVAL;
        }

        // 7. StockTransfer 레코드 생성
        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .inventory(inventory)
                .quantity(request.getQuantity())
                .transferStatus(transferStatus)
                .requestedBy(request.getRequestedBy())
                .transferredAt(Instant.now())
                .build();

        // 8. 즉시 이동인 경우 재고 반영
        if (transferStatus == StockTransfer.TransferStatus.IMMEDIATE) {
            executeInventoryTransfer(inventory, fromLocation, toLocation, request.getQuantity());

            // 9. 이동 후 안전재고 체크
            checkSafetyStockAfterTransfer(product);
        }

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);
        return toResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        // 승인 시 재고 반영
        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();

        // 재검증 (승인 대기 중에 재고가 변경되었을 수 있음)
        if (inventory.getQuantity() < transfer.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock for transfer. Current: " + inventory.getQuantity());
        }

        executeInventoryTransfer(inventory, fromLocation, toLocation, transfer.getQuantity());

        transfer.setTransferStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());

        // 안전재고 체크
        checkSafetyStockAfterTransfer(transfer.getProduct());

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);
        return toResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String rejectedBy, String reason) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setApprovedBy(rejectedBy);
        transfer.setRejectionReason(reason);
        transfer.setApprovedAt(Instant.now());

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);
        return toResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferListResponse> getTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    // ========== Private Helper Methods ==========

    private void validateBasicRules(Inventory inventory, Location fromLocation,
                                    Location toLocation, int quantity) {
        // 동일 로케이션 거부
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new BusinessException("SAME_LOCATION", "Cannot transfer to the same location");
        }

        // 재고 부족 체크
        if (inventory.getQuantity() < quantity) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock. Available: " + inventory.getQuantity());
        }

        // 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN",
                    "From location " + fromLocation.getCode() + " is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN",
                    "To location " + toLocation.getCode() + " is frozen for cycle count");
        }

        // 도착지 용량 체크
        if (toLocation.getCurrentQuantity() + quantity > toLocation.getCapacity()) {
            throw new BusinessException("LOCATION_CAPACITY_EXCEEDED",
                    "Destination location capacity would be exceeded");
        }
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot move FROZEN product to AMBIENT location (quality risk)");
        }

        // COLD 상품 → AMBIENT 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot move COLD product to AMBIENT location (quality risk)");
        }

        // HAZMAT 상품은 모든 로케이션 이동 가능 (zone 제약 제거)
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 적재된 재고 확인
        List<Inventory> existingInventories = inventoryRepository.findAvailableInventoryForProduct(
                product.getId()).stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .toList();

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory existing : existingInventories) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat != existingIsHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }
        }
    }

    private void validateExpiryRestrictions(Inventory inventory, Location toLocation) {
        if (inventory.getExpiryDate() == null) {
            return; // 유통기한 관리 대상 아님
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();

        // 유통기한 만료 → 이동 불가
        if (!expiryDate.isAfter(today)) {
            throw new BusinessException("EXPIRED_PRODUCT",
                    "Cannot transfer expired products (expiry: " + expiryDate + ")");
        }

        // 잔여 유통기한 < 10% → SHIPPING zone만 허용
        if (inventory.getManufactureDate() != null) {
            long totalShelfLife = ChronoUnit.DAYS.between(inventory.getManufactureDate(), expiryDate);
            long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = ((double) remainingShelfLife / totalShelfLife) * 100;

            if (remainingPct < 10 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("EXPIRY_RESTRICTION",
                        "Products with <10% shelf life remaining can only be transferred to SHIPPING zone");
            }
        }
    }

    private void executeInventoryTransfer(Inventory inventory, Location fromLocation,
                                          Location toLocation, int quantity) {
        // 출발지 재고 감소
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);

        // 출발지 로케이션 현재 수량 감소
        fromLocation.setCurrentQuantity(fromLocation.getCurrentQuantity() - quantity);
        locationRepository.save(fromLocation);

        // 도착지 재고 증가 (동일 상품+로트가 있는지 확인)
        Inventory toInventory = inventoryRepository.findByProductAndLocationAndLot(
                inventory.getProduct().getId(),
                toLocation.getId(),
                inventory.getLotNumber()
        ).orElse(null);

        if (toInventory != null) {
            toInventory.setQuantity(toInventory.getQuantity() + quantity);
            inventoryRepository.save(toInventory);
        } else {
            // 새 재고 레코드 생성
            Inventory newInventory = Inventory.builder()
                    .product(inventory.getProduct())
                    .location(toLocation)
                    .quantity(quantity)
                    .lotNumber(inventory.getLotNumber())
                    .manufactureDate(inventory.getManufactureDate())
                    .expiryDate(inventory.getExpiryDate())
                    .receivedAt(inventory.getReceivedAt())
                    .expired(inventory.getExpired())
                    .build();
            inventoryRepository.save(newInventory);
        }

        // 도착지 로케이션 현재 수량 증가
        toLocation.setCurrentQuantity(toLocation.getCurrentQuantity() + quantity);
        locationRepository.save(toLocation);
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        int storageZoneStock = inventoryRepository.findAvailableInventoryForProduct(product.getId()).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .mapToInt(Inventory::getQuantity)
                .sum();

        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);
        if (rule != null && storageZoneStock <= rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER_AFTER_TRANSFER")
                    .reorderQty(rule.getReorderQty())
                    .triggeredAt(Instant.now())
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.info("Auto reorder triggered for product {} after transfer. Current STORAGE stock: {}, Min: {}",
                    product.getSku(), storageZoneStock, rule.getMinQty());
        }
    }

    private StockTransferResponse toResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .productId(transfer.getProduct().getId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getId())
                .toLocationCode(transfer.getToLocation().getCode())
                .inventoryId(transfer.getInventory().getId())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus().name())
                .requestedBy(transfer.getRequestedBy())
                .approvedBy(transfer.getApprovedBy())
                .approvedAt(transfer.getApprovedAt())
                .rejectionReason(transfer.getRejectionReason())
                .transferredAt(transfer.getTransferredAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }

    private StockTransferListResponse toListResponse(StockTransfer transfer) {
        return StockTransferListResponse.builder()
                .id(transfer.getId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus().name())
                .transferredAt(transfer.getTransferredAt())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
}
