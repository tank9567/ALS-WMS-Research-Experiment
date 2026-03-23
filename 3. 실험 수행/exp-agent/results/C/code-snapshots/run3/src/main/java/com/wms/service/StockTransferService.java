package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행
     * ALS-WMS-STK-002: 재고 이동 시 모든 검증 수행
     */
    @Transactional
    public StockTransfer transferStock(UUID fromLocationId, UUID toLocationId,
                                       UUID productId, String lotNumber,
                                       Integer quantity, String requestedBy) {
        // 1. 기본 검증
        Location fromLocation = locationRepository.findById(fromLocationId)
                .orElseThrow(() -> new IllegalArgumentException("출발지 로케이션을 찾을 수 없습니다: " + fromLocationId));

        Location toLocation = locationRepository.findById(toLocationId)
                .orElseThrow(() -> new IllegalArgumentException("도착지 로케이션을 찾을 수 없습니다: " + toLocationId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        // 2. 동일 로케이션 체크
        if (fromLocationId.equals(toLocationId)) {
            throw new IllegalStateException("출발지와 도착지가 동일합니다.");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new IllegalStateException("실사 중인 로케이션에서 이동할 수 없습니다: " + fromLocation.getCode());
        }
        if (toLocation.getIsFrozen()) {
            throw new IllegalStateException("실사 중인 로케이션으로 이동할 수 없습니다: " + toLocation.getCode());
        }

        // 4. 출발지 재고 확인
        Inventory fromInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        product, fromLocation, lotNumber)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("출발지에 재고가 없습니다 (상품: %s, 로케이션: %s, 로트: %s)",
                                product.getSku(), fromLocation.getCode(), lotNumber)));

        if (fromInventory.getQuantity() < quantity) {
            throw new IllegalStateException(
                    String.format("출발지 재고가 부족합니다 (요청: %d, 가용: %d)",
                            quantity, fromInventory.getQuantity()));
        }

        // 5. 보관 유형 호환성 검증
        validateStorageTypeCompatibility(product, toLocation);

        // 6. 위험물 혼적 금지 검증
        validateHazmatSegregation(product, toLocation);

        // 7. 유통기한 이동 제한 검증
        validateExpiryDateRestriction(product, toLocation, fromInventory);

        // 8. 도착지 용량 체크
        if (toLocation.getCurrentQty() + quantity > toLocation.getCapacity()) {
            throw new IllegalStateException(
                    String.format("도착지 로케이션 용량 초과 (현재: %d, 이동: %d, 용량: %d)",
                            toLocation.getCurrentQty(), quantity, toLocation.getCapacity()));
        }

        // 9. 대량 이동 체크 (80% 이상)
        double transferRatio = (double) quantity / fromInventory.getQuantity();
        boolean requiresApproval = transferRatio >= 0.8;

        StockTransfer.TransferStatus status = requiresApproval
                ? StockTransfer.TransferStatus.pending_approval
                : StockTransfer.TransferStatus.immediate;

        // 10. StockTransfer 이력 생성
        StockTransfer transfer = StockTransfer.builder()
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .product(product)
                .lotNumber(lotNumber)
                .quantity(quantity)
                .transferStatus(status)
                .requestedBy(requestedBy)
                .build();
        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 반영
        if (status == StockTransfer.TransferStatus.immediate) {
            executeTransfer(transfer);
        }

        return transfer;
    }

    /**
     * 보관 유형 호환성 검증
     * ALS-WMS-STK-002: 도착지 로케이션의 보관 유형이 상품과 호환되어야 함
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // HAZMAT 상품 -> HAZMAT zone만 허용
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (toLocation.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                        "HAZMAT 상품은 HAZMAT zone으로만 이동할 수 있습니다: " + product.getSku());
            }
            return;
        }

        // 보관 유형 호환성
        boolean compatible = switch (productType) {
            case FROZEN -> {
                if (locationType == Product.StorageType.AMBIENT) {
                    yield false;
                }
                yield true;
            }
            case COLD -> {
                if (locationType == Product.StorageType.AMBIENT) {
                    yield false;
                }
                yield true;
            }
            case AMBIENT -> true; // AMBIENT는 모든 로케이션 허용
        };

        if (!compatible) {
            throw new IllegalStateException(
                    String.format("%s 상품은 %s 로케이션으로 이동할 수 없습니다",
                            productType, locationType));
        }
    }

    /**
     * 위험물 혼적 금지 검증
     * ALS-WMS-STK-002: HAZMAT과 비-HAZMAT의 동일 로케이션 혼적 전면 금지
     */
    private void validateHazmatSegregation(Product product, Location toLocation) {
        boolean isHazmat = product.getCategory() == Product.Category.HAZMAT;

        // 도착지에 이미 적재된 상품 확인
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.Category.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 체크
            if (isHazmat != existingIsHazmat) {
                throw new IllegalStateException(
                        "비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다");
            }
        }
    }

    /**
     * 유통기한 이동 제한 검증
     * ALS-WMS-STK-002: 유통기한 임박/만료 상품의 이동 제한
     */
    private void validateExpiryDateRestriction(Product product, Location toLocation,
                                                Inventory fromInventory) {
        if (!product.getHasExpiry() || fromInventory.getExpiryDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = fromInventory.getExpiryDate();
        LocalDate manufactureDate = fromInventory.getManufactureDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new IllegalStateException("유통기한이 만료된 재고는 이동할 수 없습니다");
        }

        // 잔여 유통기한 비율 계산
        if (manufactureDate != null) {
            double remainingPct = calculateRemainingShelfLifePct(manufactureDate, expiryDate, today);

            // 잔여 유통기한 < 10% -> SHIPPING zone만 허용
            if (remainingPct < 10.0) {
                if (toLocation.getZone() != Location.Zone.SHIPPING) {
                    throw new IllegalStateException(
                            String.format("잔여 유통기한이 %.1f%%인 재고는 SHIPPING zone으로만 이동 가능합니다",
                                    remainingPct));
                }
            }
        }
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate,
                                                   LocalDate expiryDate,
                                                   LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 대량 이동 승인
     * ALS-WMS-STK-002: 80% 이상 대량 이동 승인 처리
     */
    @Transactional
    public StockTransfer approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("재고 이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다");
        }

        // 상태 변경
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        // 재고 이동 실행
        executeTransfer(transfer);

        return transfer;
    }

    /**
     * 대량 이동 거부
     * ALS-WMS-STK-002: 80% 이상 대량 이동 거부 처리
     */
    @Transactional
    public StockTransfer rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("재고 이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 거부할 수 있습니다");
        }

        // 상태 변경
        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        return transfer;
    }

    /**
     * 재고 이동 실행 (단일 트랜잭션)
     * ALS-WMS-STK-002: 출발지 차감 + 도착지 증가를 원자적으로 처리
     */
    private void executeTransfer(StockTransfer transfer) {
        // 1. 출발지 재고 차감
        Inventory fromInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        transfer.getProduct(), transfer.getFromLocation(), transfer.getLotNumber())
                .orElseThrow(() -> new IllegalStateException("출발지 재고를 찾을 수 없습니다"));

        int remainingQty = fromInventory.getQuantity() - transfer.getQuantity();
        if (remainingQty < 0) {
            throw new IllegalStateException("출발지 재고가 부족합니다");
        }

        if (remainingQty == 0) {
            // 재고가 0이면 삭제
            inventoryRepository.delete(fromInventory);
        } else {
            fromInventory.setQuantity(remainingQty);
            inventoryRepository.save(fromInventory);
        }

        // 출발지 로케이션 current_qty 갱신
        Location fromLocation = transfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 2. 도착지 재고 증가
        var toInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                transfer.getProduct(), transfer.getToLocation(), transfer.getLotNumber());

        if (toInventory.isPresent()) {
            // 기존 재고 증가
            Inventory inv = toInventory.get();
            inv.setQuantity(inv.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(inv);
        } else {
            // 신규 재고 생성 (received_at은 원래 값 유지)
            Inventory inv = Inventory.builder()
                    .product(transfer.getProduct())
                    .location(transfer.getToLocation())
                    .quantity(transfer.getQuantity())
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(fromInventory.getExpiryDate())
                    .manufactureDate(fromInventory.getManufactureDate())
                    .receivedAt(fromInventory.getReceivedAt()) // 원래 received_at 유지
                    .build();
            inventoryRepository.save(inv);
        }

        // 도착지 로케이션 current_qty 갱신
        Location toLocation = transfer.getToLocation();
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 3. 이동 완료 시각 기록
        transfer.setTransferDate(OffsetDateTime.now());
        stockTransferRepository.save(transfer);

        // 4. 안전재고 체크
        checkSafetyStock(transfer.getProduct());
    }

    /**
     * 안전재고 체크
     * ALS-WMS-STK-002: 이동 후 STORAGE zone 안전재고 체크
     */
    private void checkSafetyStock(Product product) {
        // 안전재고 규칙 확인
        var safetyRule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId());
        if (safetyRule.isEmpty()) {
            return;
        }

        SafetyStockRule rule = safetyRule.get();

        // STORAGE zone의 전체 재고 합산
        int totalStorageQty = inventoryRepository.findByProduct(product).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주 로그 생성
        if (totalStorageQty < rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .reorderQty(rule.getReorderQty())
                    .reason(AutoReorderLog.Reason.SAFETY_STOCK_TRIGGER)
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 재고 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransfer getTransfer(UUID transferId) {
        return stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("재고 이동 이력을 찾을 수 없습니다: " + transferId));
    }

    /**
     * 재고 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public List<StockTransfer> getAllTransfers() {
        return stockTransferRepository.findAll();
    }
}
