package com.wms.service;

import com.wms.dto.InboundLineRequest;
import com.wms.dto.InboundReceiptRequest;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboundService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태)
     * ALS-WMS-INB-002: 입고 등록 시 모든 검증 수행
     */
    @Transactional
    public InboundReceipt createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 확인
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다: " + request.getPoId()));

        if (po.getStatus() == PurchaseOrder.Status.hold) {
            throw new IllegalStateException("보류된 발주서입니다. 입고가 불가능합니다.");
        }

        // 2. InboundReceipt 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.Status.inspecting)
                .receivedBy(request.getReceivedBy())
                .build();
        inboundReceiptRepository.save(receipt);

        // 3. 각 라인별 검증 및 저장
        boolean requiresApproval = false;

        for (InboundLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineReq.getLocationId()));

            // 3.1. 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new IllegalStateException("실사 중인 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3.2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3.3. 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3.4. 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        LocalDate.now()
                );

                int minPct = product.getMinRemainingShelfLifePct();

                if (remainingPct < minPct) {
                    // 유통기한 부족 -> 거부 및 페널티
                    recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                            SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            String.format("유통기한 잔여율 %.1f%% (기준: %d%%)", remainingPct, minPct));
                    throw new IllegalStateException(
                            String.format("유통기한 잔여율이 부족합니다 (%.1f%% < %d%%): %s",
                                    remainingPct, minPct, product.getSku()));
                }

                if (remainingPct >= 30 && remainingPct <= 50) {
                    // 경고 범위 -> 승인 필요
                    requiresApproval = true;
                }
            }

            // 3.5. 초과입고 체크
            validateOverReceive(po, product, lineReq.getQuantity());

            // 3.6. InboundReceiptLine 생성
            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
            inboundReceiptLineRepository.save(line);
        }

        // 4. 유통기한 경고가 있으면 pending_approval 상태로 변경
        if (requiresApproval) {
            receipt.setStatus(InboundReceipt.Status.pending_approval);
            inboundReceiptRepository.save(receipt);
        }

        // 5. 공급업체 페널티 누적 체크 (30일 내 3회 이상 -> PO hold)
        checkSupplierPenaltyThreshold(po.getSupplier());

        return receipt;
    }

    /**
     * 초과입고 검증
     * ALS-WMS-INB-002: 카테고리별 허용률 + PO유형 가중치 + 성수기 가중치
     */
    private void validateOverReceive(PurchaseOrder po, Product product, int incomingQty) {
        // PO Line 찾기
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);
        PurchaseOrderLine poLine = poLines.stream()
                .filter(line -> line.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("발주서에 해당 상품이 없습니다: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalReceiving = receivedQty + incomingQty;

        // 1. 카테고리별 기본 허용률
        double baseTolerance = switch (product.getCategory()) {
            case GENERAL -> 0.10;
            case FRESH -> 0.05;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 0.03;
        };

        // 2. HAZMAT은 무조건 0% (가중치 무시)
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (totalReceiving > orderedQty) {
                recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                        SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        String.format("HAZMAT 초과입고 시도: %d > %d", totalReceiving, orderedQty));
                throw new IllegalStateException(
                        String.format("HAZMAT 상품은 초과입고가 불가능합니다 (발주: %d, 입고시도: %d)",
                                orderedQty, totalReceiving));
            }
            return;
        }

        // 3. PO 유형별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 4. 성수기 가중치
        double seasonalMultiplier = 1.0;
        var activeSeason = seasonalConfigRepository.findActiveSeasonForDate(LocalDate.now());
        if (activeSeason.isPresent()) {
            seasonalMultiplier = activeSeason.get().getMultiplier().doubleValue();
        }

        // 5. 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonalMultiplier;
        int maxAllowed = (int) (orderedQty * (1.0 + finalTolerance));

        // 6. 검증
        if (totalReceiving > maxAllowed) {
            recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("초과입고: %d > %d (허용률: %.1f%%)",
                            totalReceiving, maxAllowed, finalTolerance * 100));
            throw new IllegalStateException(
                    String.format("초과입고 한도를 초과했습니다 (발주: %d, 최대허용: %d, 입고시도: %d)",
                            orderedQty, maxAllowed, totalReceiving));
        }
    }

    /**
     * 보관 유형 호환성 검증
     * ALS-WMS-INB-002: 상품과 로케이션의 보관 유형 호환성 체크
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT 상품 -> HAZMAT zone만 허용
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                        String.format("HAZMAT 상품은 HAZMAT zone에만 입고할 수 있습니다: %s", product.getSku()));
            }
            return;
        }

        // 보관 유형 호환성
        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD ||
                        locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
        };

        if (!compatible) {
            throw new IllegalStateException(
                    String.format("보관 유형이 호환되지 않습니다 (상품: %s, 로케이션: %s): %s",
                            productType, locationType, product.getSku()));
        }
    }

    /**
     * 유통기한 잔여율 계산
     * (만료일 - 오늘) / (만료일 - 제조일) * 100
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
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(Supplier supplier, UUID poId,
                                       SupplierPenalty.PenaltyType type,
                                       String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .poId(poId)
                .penaltyType(type)
                .description(description)
                .build();
        supplierPenaltyRepository.save(penalty);
    }

    /**
     * 공급업체 페널티 임계치 체크
     * 최근 30일 내 3회 이상 -> 모든 pending PO를 hold로 변경
     */
    private void checkSupplierPenaltyThreshold(Supplier supplier) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        List<SupplierPenalty> recentPenalties =
                supplierPenaltyRepository.findBySupplierAndCreatedAtAfter(supplier, thirtyDaysAgo);

        if (recentPenalties.size() >= 3) {
            // 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPOs =
                    purchaseOrderRepository.findBySupplierAndStatus(supplier, PurchaseOrder.Status.pending);

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.Status.hold);
                purchaseOrderRepository.save(po);
            }
        }
    }

    /**
     * 입고 확정
     * ALS-WMS-INB-002: confirmed 상태로 변경하고 재고 반영
     */
    @Transactional
    public InboundReceipt confirmReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting &&
            receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 확정할 수 있습니다.");
        }

        // 1. 상태 변경
        receipt.setStatus(InboundReceipt.Status.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(receipt);

        // 2. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceipt(receipt);
        for (InboundReceiptLine line : lines) {
            updateInventory(line);
            updatePurchaseOrderLine(receipt.getPurchaseOrder(), line.getProduct(), line.getQuantity());
        }

        // 3. PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        return receipt;
    }

    /**
     * 재고 반영
     */
    private void updateInventory(InboundReceiptLine line) {
        // 기존 inventory 찾기 (product + location + lot)
        var existingInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                line.getProduct(), line.getLocation(), line.getLotNumber());

        if (existingInventory.isPresent()) {
            // 기존 재고 증가
            Inventory inv = existingInventory.get();
            inv.setQuantity(inv.getQuantity() + line.getQuantity());
            inventoryRepository.save(inv);
        } else {
            // 신규 재고 생성
            Inventory inv = Inventory.builder()
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(OffsetDateTime.now())
                    .build();
            inventoryRepository.save(inv);
        }

        // Location current_qty 갱신
        Location location = line.getLocation();
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);
    }

    /**
     * PO Line received_qty 누적 갱신
     */
    private void updatePurchaseOrderLine(PurchaseOrder po, Product product, int quantity) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);
        PurchaseOrderLine poLine = poLines.stream()
                .filter(line -> line.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow();

        poLine.setReceivedQty(poLine.getReceivedQty() + quantity);
        purchaseOrderLineRepository.save(poLine);
    }

    /**
     * PO 상태 갱신 (모든 라인 완납 -> completed, 일부 -> partial)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);

        boolean allCompleted = poLines.stream()
                .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());

        boolean anyReceived = poLines.stream()
                .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            po.setStatus(PurchaseOrder.Status.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.Status.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceipt rejectReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting &&
            receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 거부할 수 있습니다.");
        }

        receipt.setStatus(InboundReceipt.Status.rejected);
        inboundReceiptRepository.save(receipt);

        return receipt;
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     */
    @Transactional
    public InboundReceipt approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다.");
        }

        receipt.setStatus(InboundReceipt.Status.inspecting);
        inboundReceiptRepository.save(receipt);

        return receipt;
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceipt getReceipt(UUID receiptId) {
        return inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceipt> getAllReceipts() {
        return inboundReceiptRepository.findAll();
    }
}
