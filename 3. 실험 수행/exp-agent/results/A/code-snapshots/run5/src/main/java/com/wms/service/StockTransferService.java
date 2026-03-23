package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
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
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        Location fromLocation = inventory.getLocation();
        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found"));

        Product product = inventory.getProduct();

        // 2. 기본 검증
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new BusinessException("Cannot transfer to the same location");
        }

        if (request.getQuantity() <= 0) {
            throw new BusinessException("Transfer quantity must be positive");
        }

        if (inventory.getQuantity() < request.getQuantity()) {
            throw new BusinessException("Insufficient inventory quantity");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("Source location is frozen for cycle count");
        }

        if (toLocation.getIsFrozen()) {
            throw new BusinessException("Destination location is frozen for cycle count");
        }

        // 4. 용량 체크
        if (toLocation.getCurrentQuantity() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("Destination location capacity exceeded");
        }

        // 5. 보관 유형 호환성 체크
        if (!isStorageTypeCompatible(product, toLocation)) {
            throw new BusinessException("Storage type incompatible: product=" + product.getStorageType() +
                    ", location=" + toLocation.getStorageType());
        }

        // 6. HAZMAT 혼적 금지
        if (!isHazmatMixingAllowed(product, toLocation)) {
            throw new BusinessException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
        }

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getRequiresExpiryManagement() && inventory.getExpiryDate() != null) {
            validateExpiryDateForTransfer(inventory, toLocation);
        }

        // 8. 대량 이동 승인 체크
        boolean requiresApproval = false;
        double transferPercentage = (double) request.getQuantity() / inventory.getQuantity() * 100;
        if (transferPercentage >= 80) {
            requiresApproval = true;
        }

        StockTransfer.TransferStatus status = requiresApproval
                ? StockTransfer.TransferStatus.pending_approval
                : StockTransfer.TransferStatus.immediate;

        // 9. StockTransfer 생성
        StockTransfer transfer = StockTransfer.builder()
                .inventory(inventory)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .transferStatus(status)
                .requestedBy(request.getRequestedBy())
                .reason(request.getReason())
                .build();

        // 10. 즉시 이동인 경우 재고 변경
        if (status == StockTransfer.TransferStatus.immediate) {
            executeInventoryTransfer(inventory, fromLocation, toLocation, request.getQuantity());
            transfer.setTransferredAt(OffsetDateTime.now());
        }

        transfer = stockTransferRepository.save(transfer);

        // 11. 안전재고 체크 (STORAGE zone 대상)
        if (status == StockTransfer.TransferStatus.immediate) {
            checkSafetyStockAfterTransfer(product);
        }

        return convertToResponse(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("Transfer is not pending approval");
        }

        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();

        // 재검증 (승인 대기 중에 상태가 변경되었을 수 있음)
        if (inventory.getQuantity() < transfer.getQuantity()) {
            throw new BusinessException("Insufficient inventory quantity");
        }

        if (toLocation.getCurrentQuantity() + transfer.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("Destination location capacity exceeded");
        }

        // 재고 이동 실행
        executeInventoryTransfer(inventory, fromLocation, toLocation, transfer.getQuantity());

        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setTransferredAt(OffsetDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        // 안전재고 체크
        checkSafetyStockAfterTransfer(inventory.getProduct());

        return convertToResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        transfer = stockTransferRepository.save(transfer);

        return convertToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransferById(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));
        return convertToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 비즈니스 로직 헬퍼 메서드

    private void executeInventoryTransfer(Inventory inventory, Location fromLocation,
                                          Location toLocation, Integer quantity) {
        // 출발지 재고 감소
        inventory.setQuantity(inventory.getQuantity() - quantity);
        fromLocation.setCurrentQuantity(fromLocation.getCurrentQuantity() - quantity);
        inventoryRepository.save(inventory);
        locationRepository.save(fromLocation);

        // 도착지 재고 증가 (동일한 상품+로트+유통기한 조합이 있으면 합산, 없으면 신규 생성)
        Inventory toInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                inventory.getProduct().getId(),
                toLocation.getId(),
                inventory.getLotNumber(),
                inventory.getExpiryDate()
        ).orElse(null);

        if (toInventory != null) {
            // 기존 재고에 합산
            toInventory.setQuantity(toInventory.getQuantity() + quantity);
            inventoryRepository.save(toInventory);
        } else {
            // 신규 재고 생성
            Inventory newInventory = Inventory.builder()
                    .product(inventory.getProduct())
                    .location(toLocation)
                    .lotNumber(inventory.getLotNumber())
                    .quantity(quantity)
                    .manufactureDate(inventory.getManufactureDate())
                    .expiryDate(inventory.getExpiryDate())
                    .receivedAt(inventory.getReceivedAt())
                    .build();
            inventoryRepository.save(newInventory);
        }

        toLocation.setCurrentQuantity(toLocation.getCurrentQuantity() + quantity);
        locationRepository.save(toLocation);
    }

    private boolean isStorageTypeCompatible(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT은 이제 모든 구역 허용 (일반 로케이션에도 보관 가능)
        if (productType == Product.StorageType.HAZMAT) {
            return true;
        }

        // FROZEN → FROZEN만 허용
        if (productType == Product.StorageType.FROZEN) {
            return locationType == Product.StorageType.FROZEN;
        }

        // COLD → COLD, FROZEN 허용
        if (productType == Product.StorageType.COLD) {
            return locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
        }

        // AMBIENT → AMBIENT만 허용 (하위 호환 불가, 상위 호환만 가능)
        if (productType == Product.StorageType.AMBIENT) {
            return locationType == Product.StorageType.AMBIENT;
        }

        return true;
    }

    private boolean isHazmatMixingAllowed(Product product, Location toLocation) {
        List<Inventory> existingInventories = inventoryRepository.findByProductId(product.getId());

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        // 도착지에 이미 있는 재고 확인
        List<Inventory> toLocationInventories = existingInventories.stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .collect(Collectors.toList());

        for (Inventory inv : toLocationInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT;
            if (isHazmat != existingIsHazmat) {
                return false; // HAZMAT과 비-HAZMAT 혼적 금지
            }
        }

        return true;
    }

    private void validateExpiryDateForTransfer(Inventory inventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();
        LocalDate manufactureDate = inventory.getManufactureDate();

        // 유통기한 만료
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("Cannot transfer expired products");
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

            // 잔여 유통기한 < 10%: SHIPPING zone만 허용
            if (remainingPct < 10) {
                if (toLocation.getZone() != Location.Zone.SHIPPING) {
                    throw new BusinessException("Products with <10% remaining shelf life can only be transferred to SHIPPING zone");
                }
            }
        }
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);
        if (rule == null) {
            return; // 안전재고 규칙이 없으면 체크 안 함
        }

        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventories = inventoryRepository.findByProductId(product.getId()).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .collect(Collectors.toList());

        int totalStorageQuantity = storageInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalStorageQuantity <= rule.getMinQty()) {
            // 자동 재발주 요청 생성
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .currentQty(totalStorageQuantity)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .reason("SAFETY_STOCK_TRIGGER")
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    private StockTransferResponse convertToResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .inventoryId(transfer.getInventory().getId())
                .productSku(transfer.getInventory().getProduct().getSku())
                .productName(transfer.getInventory().getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getId())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus())
                .requestedBy(transfer.getRequestedBy())
                .approvedBy(transfer.getApprovedBy())
                .reason(transfer.getReason())
                .transferredAt(transfer.getTransferredAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }
}
