package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public StockTransferResponse createStockTransfer(StockTransferRequest request) {
        // 1. 엔티티 조회
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("To location not found"));

        // 2. 기본 검증
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same location");
        }

        if (!inventory.getLocation().getId().equals(fromLocation.getId())) {
            throw new IllegalArgumentException("Inventory location does not match from location");
        }

        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (inventory.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Insufficient inventory quantity");
        }

        // 3. 실사 동결 로케이션 체크
        if (fromLocation.getIsFrozen()) {
            throw new IllegalArgumentException("From location is frozen for cycle count");
        }

        if (toLocation.getIsFrozen()) {
            throw new IllegalArgumentException("To location is frozen for cycle count");
        }

        // 4. 도착지 용량 체크
        if (toLocation.getCurrentQuantity() + request.getQuantity() > toLocation.getCapacity()) {
            throw new IllegalArgumentException("Destination location capacity exceeded");
        }

        Product product = inventory.getProduct();

        // 5. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 6. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getRequiresExpiryTracking() && inventory.getExpiryDate() != null) {
            double remainingPct = calculateRemainingShelfLifePct(
                    inventory.getManufactureDate(),
                    inventory.getExpiryDate(),
                    LocalDate.now()
            );

            // 유통기한 만료
            if (inventory.getExpiryDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Cannot transfer expired inventory");
            }

            // 잔여 유통기한 < 10%는 SHIPPING zone으로만 이동 가능
            if (remainingPct < 10 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new IllegalArgumentException("Inventory with <10% shelf life can only be moved to SHIPPING zone");
            }
        }

        // 8. 대량 이동 승인 체크 (80% 이상)
        boolean needsApproval = false;
        double transferRatio = (double) request.getQuantity() / inventory.getQuantity();
        if (transferRatio >= 0.80) {
            needsApproval = true;
        }

        // 9. StockTransfer 엔티티 생성
        StockTransfer transfer = new StockTransfer();
        transfer.setInventory(inventory);
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setQuantity(request.getQuantity());
        transfer.setRequestedAt(OffsetDateTime.now());

        if (needsApproval) {
            transfer.setTransferStatus(StockTransfer.TransferStatus.pending_approval);
        } else {
            transfer.setTransferStatus(StockTransfer.TransferStatus.immediate);
            // 즉시 이동 실행
            executeTransfer(transfer);
        }

        transfer = stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID id, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        // 이동 실행
        executeTransfer(transfer);

        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedAt(OffsetDateTime.now());
        transfer.setApprovedBy(approvedBy);
        transfer.setUpdatedAt(OffsetDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID id, String reason) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setRejectionReason(reason);
        transfer.setUpdatedAt(OffsetDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    public StockTransferResponse getTransfer(UUID id) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return StockTransferResponse.from(transfer);
    }

    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private void executeTransfer(StockTransfer transfer) {
        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        int quantity = transfer.getQuantity();

        // 1. 출발지 재고 차감
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setUpdatedAt(OffsetDateTime.now());

        // 2. 출발지 로케이션 수량 차감
        fromLocation.setCurrentQuantity(fromLocation.getCurrentQuantity() - quantity);
        fromLocation.setUpdatedAt(OffsetDateTime.now());

        // 3. 도착지 재고 생성 또는 업데이트
        // 동일 상품, 도착지, 로트, 유통기한이 같은 재고가 있으면 합산, 없으면 신규 생성
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(inventory.getProduct().getId()) &&
                               inv.getLocation().getId().equals(toLocation.getId()) &&
                               (inv.getLotNumber() == null && inventory.getLotNumber() == null ||
                                inv.getLotNumber() != null && inv.getLotNumber().equals(inventory.getLotNumber())) &&
                               (inv.getExpiryDate() == null && inventory.getExpiryDate() == null ||
                                inv.getExpiryDate() != null && inv.getExpiryDate().equals(inventory.getExpiryDate())))
                .collect(Collectors.toList());

        if (!existingInventories.isEmpty()) {
            // 기존 재고에 합산
            Inventory existing = existingInventories.get(0);
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setUpdatedAt(OffsetDateTime.now());
            inventoryRepository.save(existing);
        } else {
            // 신규 재고 생성
            Inventory newInventory = new Inventory();
            newInventory.setProduct(inventory.getProduct());
            newInventory.setLocation(toLocation);
            newInventory.setQuantity(quantity);
            newInventory.setLotNumber(inventory.getLotNumber());
            newInventory.setManufactureDate(inventory.getManufactureDate());
            newInventory.setExpiryDate(inventory.getExpiryDate());
            newInventory.setReceivedAt(inventory.getReceivedAt());
            newInventory.setExpired(inventory.getExpired());
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 수량 증가
        toLocation.setCurrentQuantity(toLocation.getCurrentQuantity() + quantity);
        toLocation.setUpdatedAt(OffsetDateTime.now());

        // 5. 변경사항 저장
        inventoryRepository.save(inventory);
        locationRepository.save(fromLocation);
        locationRepository.save(toLocation);

        // 6. 안전재고 체크 (STORAGE zone만)
        checkSafetyStock(inventory.getProduct());
    }

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move FROZEN products to AMBIENT storage");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move COLD products to AMBIENT storage");
        }

        // AMBIENT 상품 → COLD/FROZEN: 허용 (상위 호환)
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 있는 재고 확인
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()) && inv.getQuantity() > 0)
                .collect(Collectors.toList());

        boolean productIsHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory existing : existingInventories) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (productIsHazmat != existingIsHazmat) {
                throw new IllegalArgumentException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            return 100.0;
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void checkSafetyStock(Product product) {
        // STORAGE zone 내 전체 재고 확인
        int totalStorageStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()) &&
                               inv.getLocation().getZone() == Location.Zone.STORAGE &&
                               !inv.getExpired() &&
                               inv.getQuantity() > 0)
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 확인
        safetyStockRuleRepository.findByProductId(product.getId()).ifPresent(rule -> {
            if (totalStorageStock <= rule.getMinQty()) {
                // 자동 재발주 기록
                AutoReorderLog log = new AutoReorderLog();
                log.setProduct(product);
                log.setTriggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER);
                log.setReorderQty(rule.getReorderQty());
                log.setCurrentStock(totalStorageStock);
                autoReorderLogRepository.save(log);
            }
        });
    }
}
