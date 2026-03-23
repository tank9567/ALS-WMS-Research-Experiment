package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public StockTransferResponse transferStock(StockTransferRequest request) {
        // 1. 기본 데이터 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("To location not found"));

        // 2. 기본 검증
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new IllegalArgumentException("From and to locations cannot be the same");
        }

        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new IllegalStateException("From location is frozen for cycle count");
        }

        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new IllegalStateException("To location is frozen for cycle count");
        }

        // 3. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        product, fromLocation, request.getLotNumber())
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found at from location"));

        // 4. 재고 수량 체크
        if (inventory.getQuantity() < request.getQuantity()) {
            throw new IllegalStateException("Insufficient quantity at from location");
        }

        // 5. 유통기한 체크 (유통기한 관리 상품인 경우)
        if (Boolean.TRUE.equals(product.getHasExpiry()) && inventory.getExpiryDate() != null) {
            LocalDate today = LocalDate.now();

            // 유통기한 만료 체크
            if (inventory.getExpiryDate().isBefore(today)) {
                throw new IllegalStateException("Cannot transfer expired inventory");
            }

            // 잔여 유통기한 비율 계산
            if (inventory.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                        inventory.getManufactureDate(),
                        inventory.getExpiryDate()
                );

                // 잔여 유통기한 < 10%: SHIPPING zone만 허용
                if (remainingPct < 10.0) {
                    if (!"SHIPPING".equals(toLocation.getZone())) {
                        throw new IllegalStateException("Inventory with less than 10% shelf life can only be transferred to SHIPPING zone");
                    }
                }
            }
        }

        // 6. 보관 유형 호환성 체크
        validateStorageCompatibility(product, toLocation);

        // 7. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 8. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new IllegalStateException("To location capacity exceeded");
        }

        // 9. 대량 이동 승인 여부 판단 (80% 이상)
        double transferPercentage = (double) request.getQuantity() / inventory.getQuantity() * 100;
        String transferStatus = transferPercentage >= 80.0 ? "pending_approval" : "immediate";

        // 10. 재고 이동 엔티티 생성
        StockTransfer transfer = new StockTransfer();
        transfer.setProduct(product);
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setQuantity(request.getQuantity());
        transfer.setLotNumber(request.getLotNumber());
        transfer.setReason(request.getReason());
        transfer.setTransferStatus(transferStatus);
        transfer.setTransferredBy(request.getTransferredBy());

        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 이동 실행
        if ("immediate".equals(transferStatus)) {
            executeTransfer(transfer, inventory, fromLocation, toLocation, product);
        }

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (!"pending_approval".equals(transfer.getTransferStatus())) {
            throw new IllegalStateException("Transfer is not pending approval");
        }

        // 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        transfer.getProduct(), transfer.getFromLocation(), transfer.getLotNumber())
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        // 재고 이동 실행
        executeTransfer(transfer, inventory, transfer.getFromLocation(), transfer.getToLocation(), transfer.getProduct());

        // 상태 업데이트
        transfer.setTransferStatus("approved");
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (!"pending_approval".equals(transfer.getTransferStatus())) {
            throw new IllegalStateException("Transfer is not pending approval");
        }

        transfer.setTransferStatus("rejected");
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransferById(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // 재고 이동 실행 (private helper)
    private void executeTransfer(StockTransfer transfer, Inventory fromInventory,
                                  Location fromLocation, Location toLocation, Product product) {
        // 1. 출발지 재고 차감
        fromInventory.setQuantity(fromInventory.getQuantity() - transfer.getQuantity());
        inventoryRepository.save(fromInventory);

        // 2. 출발지 로케이션 수량 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 재고가 있으면 증가, 없으면 신규 생성)
        Inventory toInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                product, toLocation, transfer.getLotNumber()
        ).orElse(null);

        if (toInventory != null) {
            toInventory.setQuantity(toInventory.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(toInventory);
        } else {
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(toLocation);
            newInventory.setQuantity(transfer.getQuantity());
            newInventory.setLotNumber(transfer.getLotNumber());
            newInventory.setExpiryDate(fromInventory.getExpiryDate());
            newInventory.setManufactureDate(fromInventory.getManufactureDate());
            newInventory.setReceivedAt(fromInventory.getReceivedAt()); // 원래 입고일 유지
            newInventory.setIsExpired(fromInventory.getIsExpired());
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 수량 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone)
        if ("STORAGE".equals(fromLocation.getZone())) {
            checkSafetyStock(product);
        }
    }

    // 보관 유형 호환성 검증
    private void validateStorageCompatibility(Product product, Location location) {
        String productStorageType = product.getStorageType();
        String locationStorageType = location.getStorageType();

        // FROZEN 상품 → FROZEN만 허용
        if ("FROZEN".equals(productStorageType) && !"FROZEN".equals(locationStorageType)) {
            throw new IllegalStateException("FROZEN products can only be stored in FROZEN locations");
        }

        // COLD 상품 → COLD 또는 FROZEN 허용
        if ("COLD".equals(productStorageType) &&
            !("COLD".equals(locationStorageType) || "FROZEN".equals(locationStorageType))) {
            throw new IllegalStateException("COLD products can only be stored in COLD or FROZEN locations");
        }

        // AMBIENT 상품 → AMBIENT만 허용
        if ("AMBIENT".equals(productStorageType) && !"AMBIENT".equals(locationStorageType)) {
            throw new IllegalStateException("AMBIENT products can only be stored in AMBIENT locations");
        }

        // HAZMAT 상품 → HAZMAT zone만 허용
        if ("HAZMAT".equals(product.getCategory()) && !"HAZMAT".equals(location.getZone())) {
            throw new IllegalStateException("HAZMAT products can only be stored in HAZMAT zone");
        }
    }

    // HAZMAT 혼적 금지 검증
    private void validateHazmatSegregation(Product product, Location toLocation) {
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        boolean isHazmat = "HAZMAT".equals(product.getCategory());

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = "HAZMAT".equals(inv.getProduct().getCategory());

            if (isHazmat && !existingIsHazmat) {
                throw new IllegalStateException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }

            if (!isHazmat && existingIsHazmat) {
                throw new IllegalStateException("Cannot mix non-HAZMAT and HAZMAT products in the same location");
            }
        }
    }

    // 잔여 유통기한 비율 계산
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (double) remainingDays / totalDays * 100.0;
    }

    // 안전재고 체크
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return;
        }

        // STORAGE zone의 전체 재고 합산
        List<Inventory> storageInventories = inventoryRepository.findByProduct(product).stream()
                .filter(inv -> "STORAGE".equals(inv.getLocation().getZone()))
                .collect(Collectors.toList());

        int totalStock = storageInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalStock <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerType("SAFETY_STOCK_TRIGGER");
            log.setCurrentStock(totalStock);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(log);
        }
    }
}
