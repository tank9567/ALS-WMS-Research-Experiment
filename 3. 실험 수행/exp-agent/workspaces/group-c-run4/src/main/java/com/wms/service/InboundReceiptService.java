package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundReceiptService {

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
     * 입고 등록 (inspecting 상태로 생성)
     */
    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        log.info("입고 등록 시작: PO ID = {}", request.getPoId());

        // 1. PO 조회 및 검증
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
            .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다: " + request.getPoId()));

        if (po.getStatus() == PurchaseOrder.PoStatus.cancelled) {
            throw new IllegalStateException("취소된 발주서에는 입고할 수 없습니다");
        }

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new IllegalStateException("보류된 발주서에는 입고할 수 없습니다");
        }

        // 2. InboundReceipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
            .purchaseOrder(po)
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .build();

        // 3. 각 라인별 검증 및 생성
        List<InboundReceiptLine> lines = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineReq.getLocationId()));

            // 3-1. 실사 동결 체크 (ALS-WMS-INB-002 Constraint)
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("실사 동결 중인 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3-2. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크 (ALS-WMS-INB-002 Constraint)
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 대상 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (Boolean.TRUE.equals(product.getManufactureDateRequired()) && lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 대상 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3-4. 유통기한 잔여율 체크 (ALS-WMS-INB-002 Constraint)
                double remainingPct = calculateRemainingShelfLife(
                    lineReq.getManufactureDate(),
                    lineReq.getExpiryDate(),
                    today
                );

                log.info("상품 {} 유통기한 잔여율: {}%", product.getSku(), remainingPct);

                if (remainingPct < product.getMinRemainingShelfLifePct()) {
                    // 유통기한 부족 -> 입고 거부 예정 (페널티 기록)
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE, po.getPoId(),
                        String.format("유통기한 부족: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, product.getMinRemainingShelfLifePct()));

                    throw new IllegalStateException(
                        String.format("유통기한 잔여율 부족으로 입고 거부: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, product.getMinRemainingShelfLifePct()));
                }

                // 30% ~ 50% 구간: 경고 + 관리자 승인 필요
                if (remainingPct >= 30.0 && remainingPct <= 50.0) {
                    log.warn("유통기한 경고: {} (잔여율 {}% - 관리자 승인 필요)", product.getSku(), remainingPct);
                    receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
                }
            } else {
                // 유통기한 관리 비대상은 null로 저장
                if (lineReq.getExpiryDate() != null || lineReq.getManufactureDate() != null) {
                    throw new IllegalArgumentException("유통기한 관리 비대상 상품에는 유통기한을 입력할 수 없습니다: " + product.getSku());
                }
            }

            // 3-5. 초과입고 검증 (ALS-WMS-INB-002 Constraint)
            validateOverReceive(po, product, lineReq.getQuantity(), today);

            // 3-6. InboundReceiptLine 생성
            InboundReceiptLine line = InboundReceiptLine.builder()
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineReq.getQuantity())
                .lotNumber(lineReq.getLotNumber())
                .expiryDate(lineReq.getExpiryDate())
                .manufactureDate(lineReq.getManufactureDate())
                .build();

            lines.add(line);
        }

        receipt.setLines(lines);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("입고 등록 완료: Receipt ID = {}, Status = {}", savedReceipt.getReceiptId(), savedReceipt.getStatus());

        return convertToResponse(savedReceipt);
    }

    /**
     * 입고 확정 (재고 반영)
     */
    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        log.info("입고 확정 시작: Receipt ID = {}", receiptId);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 확정할 수 있습니다");
        }

        // 1. 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
        }

        // 2. PO 누적 수량 갱신 및 상태 변경
        updatePurchaseOrderStatus(receipt.getPurchaseOrder(), receipt.getLines());

        // 3. Receipt 상태 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("입고 확정 완료: Receipt ID = {}", receiptId);

        return convertToResponse(savedReceipt);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        log.info("입고 거부 시작: Receipt ID = {}, Reason = {}", receiptId, reason);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 거부할 수 있습니다");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("입고 거부 완료: Receipt ID = {}", receiptId);

        return convertToResponse(savedReceipt);
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> confirmed)
     */
    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        log.info("유통기한 경고 승인 시작: Receipt ID = {}", receiptId);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다");
        }

        // 승인 후 확정 처리
        return confirmInboundReceipt(receiptId);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        return convertToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
            .map(this::convertToResponse)
            .toList();
    }

    // ========== Private Helper Methods ==========

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002 Constraint)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
        };

        // HAZMAT 상품은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                    String.format("HAZMAT 상품은 HAZMAT zone에만 입고할 수 있습니다: %s -> %s",
                        product.getSku(), location.getCode()));
            }
        }

        if (!compatible) {
            throw new IllegalStateException(
                String.format("보관 유형이 호환되지 않습니다: 상품(%s) %s -> 로케이션(%s) %s",
                    product.getSku(), productType, location.getCode(), locationType));
        }
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLife(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            throw new IllegalArgumentException("제조일과 유통기한은 필수입니다");
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            throw new IllegalArgumentException("유통기한이 제조일보다 빠릅니다");
        }

        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 초과입고 검증 (ALS-WMS-INB-002 Constraint)
     */
    private void validateOverReceive(PurchaseOrder po, Product product, int inboundQty, LocalDate today) {
        // PO Line 조회
        PurchaseOrderLine poLine = purchaseOrderLineRepository
            .findByPurchaseOrder_PoIdAndProduct_ProductId(po.getPoId(), product.getProductId())
            .orElseThrow(() -> new IllegalArgumentException(
                "발주서에 해당 상품이 없습니다: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalAfterInbound = receivedQty + inboundQty;

        // 카테고리별 기본 허용률
        double baseTolerance = switch (product.getCategory()) {
            case GENERAL -> 0.10;
            case FRESH -> 0.05;
            case HAZMAT -> 0.0;  // HAZMAT은 항상 0%
            case HIGH_VALUE -> 0.03;
        };

        // HAZMAT은 무조건 0%, 가중치 무시
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (totalAfterInbound > orderedQty) {
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                    String.format("HAZMAT 초과입고: %s (발주 %d, 입고예정 %d)",
                        product.getSku(), orderedQty, totalAfterInbound));

                throw new IllegalStateException(
                    String.format("HAZMAT 상품은 초과입고가 불가능합니다: %s (발주 %d, 입고예정 %d)",
                        product.getSku(), orderedQty, totalAfterInbound));
            }
            return;  // HAZMAT은 여기서 종료
        }

        // PO 유형별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 성수기 가중치
        BigDecimal seasonMultiplier = seasonalConfigRepository.findActiveSeasonByDate(today)
            .map(SeasonalConfig::getMultiplier)
            .orElse(BigDecimal.ONE);

        // 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonMultiplier.doubleValue();
        int maxAllowedQty = (int) Math.floor(orderedQty * (1.0 + finalTolerance));

        log.info("초과입고 검증: 상품={}, 카테고리={}, 기본허용률={}%, PO유형={}, 성수기배수={}, 최종허용률={}%, 최대허용={}",
            product.getSku(), product.getCategory(), baseTolerance * 100, po.getPoType(),
            seasonMultiplier, finalTolerance * 100, maxAllowedQty);

        if (totalAfterInbound > maxAllowedQty) {
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                String.format("초과입고: %s (발주 %d, 최대허용 %d, 입고예정 %d)",
                    product.getSku(), orderedQty, maxAllowedQty, totalAfterInbound));

            throw new IllegalStateException(
                String.format("초과입고 한도를 초과했습니다: %s (발주 %d, 최대허용 %d, 입고예정 %d)",
                    product.getSku(), orderedQty, maxAllowedQty, totalAfterInbound));
        }
    }

    /**
     * 공급업체 페널티 기록 및 자동 보류 처리
     */
    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType type, UUID poId, String description) {
        log.warn("공급업체 페널티 기록: Supplier={}, Type={}, PO={}, Desc={}",
            supplier.getName(), type, poId, description);

        // 페널티 기록
        SupplierPenalty penalty = SupplierPenalty.builder()
            .supplier(supplier)
            .penaltyType(type)
            .poId(poId)
            .description(description)
            .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 조회
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        List<SupplierPenalty> recentPenalties = supplierPenaltyRepository
            .findBySupplierIdAndCreatedAtAfter(supplier.getSupplierId(), thirtyDaysAgo);

        // 3회 이상이면 해당 공급업체의 모든 pending PO를 hold로 변경
        if (recentPenalties.size() >= 3) {
            log.warn("공급업체 페널티 3회 이상: Supplier={}, 최근 30일 페널티 {}회 - 모든 pending PO를 hold로 변경",
                supplier.getName(), recentPenalties.size());

            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findPendingOrdersBySupplierId(supplier.getSupplierId());
            for (PurchaseOrder pendingPO : pendingPOs) {
                pendingPO.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(pendingPO);
                log.info("PO 보류 처리: PO Number={}", pendingPO.getPoNumber());
            }

            // 공급업체 상태도 hold로 변경
            supplier.setStatus(Supplier.SupplierStatus.hold);
        }
    }

    /**
     * 재고 반영 (inventory 테이블 및 location.current_qty 갱신)
     */
    private void updateInventory(InboundReceiptLine line) {
        Product product = line.getProduct();
        Location location = line.getLocation();

        // 로케이션 용량 체크
        if (location.getCurrentQty() + line.getQuantity() > location.getCapacity()) {
            throw new IllegalStateException(
                String.format("로케이션 용량 초과: %s (현재 %d, 추가 %d, 최대 %d)",
                    location.getCode(), location.getCurrentQty(), line.getQuantity(), location.getCapacity()));
        }

        // Inventory 레코드 조회 또는 생성
        Inventory inventory = inventoryRepository
            .findByProduct_ProductIdAndLocation_LocationIdAndLotNumber(
                product.getProductId(),
                location.getLocationId(),
                line.getLotNumber()
            )
            .orElse(Inventory.builder()
                .product(product)
                .location(location)
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .receivedAt(OffsetDateTime.now())
                .quantity(0)
                .build());

        inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
        inventoryRepository.save(inventory);

        // 로케이션 현재 수량 갱신
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);

        log.info("재고 반영 완료: 상품={}, 로케이션={}, 수량={}", product.getSku(), location.getCode(), line.getQuantity());
    }

    /**
     * PO 상태 갱신 (received_qty 누적 및 상태 변경)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po, List<InboundReceiptLine> lines) {
        for (InboundReceiptLine line : lines) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrder_PoIdAndProduct_ProductId(po.getPoId(), line.getProduct().getProductId())
                .orElseThrow(() -> new IllegalStateException("PO Line을 찾을 수 없습니다"));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 모든 라인 완납 여부 체크
        boolean allFullyReceived = po.getLines().stream()
            .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());

        boolean anyPartialReceived = po.getLines().stream()
            .anyMatch(line -> line.getReceivedQty() > 0 && line.getReceivedQty() < line.getOrderedQty());

        if (allFullyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
            log.info("PO 완료 처리: PO Number={}", po.getPoNumber());
        } else if (anyPartialReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
            log.info("PO 부분입고 처리: PO Number={}", po.getPoNumber());
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity -> DTO 변환
     */
    private InboundReceiptResponse convertToResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
            .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                .receiptLineId(line.getReceiptLineId())
                .productId(line.getProduct().getProductId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .locationId(line.getLocation().getLocationId())
                .locationCode(line.getLocation().getCode())
                .quantity(line.getQuantity())
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .build())
            .toList();

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPurchaseOrder().getPoId())
            .poNumber(receipt.getPurchaseOrder().getPoNumber())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .lines(lineResponses)
            .build();
    }
}
