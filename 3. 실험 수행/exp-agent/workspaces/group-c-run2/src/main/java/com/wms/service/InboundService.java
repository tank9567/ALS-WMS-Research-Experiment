package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.InboundException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundService {

    private final InboundReceiptRepository receiptRepository;
    private final InboundReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository poLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository penaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태)
     * ALS-WMS-INB-002 규칙 준수
     */
    @Transactional
    public InboundReceiptResponse createReceipt(InboundReceiptRequest request) {
        // 1. PO 조회 및 검증
        PurchaseOrder po = poRepository.findById(request.getPoId())
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found", "PO_NOT_FOUND"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new InboundException("Purchase order is on hold", "PO_ON_HOLD");
        }

        // 2. 각 라인별 검증 수행
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            validateReceiptLine(po, lineReq);
        }

        // 3. InboundReceipt 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
            .poId(request.getPoId())
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .build();

        // 4. 유통기한 잔여율 체크하여 상태 결정
        boolean needsApproval = checkIfNeedsApproval(request.getLines());
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        receiptRepository.save(receipt);

        // 5. InboundReceiptLine 생성
        List<InboundReceiptLine> lines = request.getLines().stream()
            .map(lineReq -> {
                InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .productId(lineReq.getProductId())
                    .locationId(lineReq.getLocationId())
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
                return line;
            })
            .collect(Collectors.toList());

        receiptLineRepository.saveAll(lines);
        receipt.setLines(lines);

        return toResponse(receipt);
    }

    /**
     * 입고 라인별 검증 (ALS-WMS-INB-002 Constraints 준수)
     */
    private void validateReceiptLine(PurchaseOrder po, InboundReceiptRequest.InboundReceiptLineRequest lineReq) {
        // 1. Product 조회
        Product product = productRepository.findById(lineReq.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        // 2. Location 조회
        Location location = locationRepository.findById(lineReq.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. PO Line 조회
        PurchaseOrderLine poLine = poLineRepository.findByPoIdAndProductId(po.getPoId(), product.getProductId())
            .orElseThrow(() -> new InboundException("Product not found in purchase order", "PRODUCT_NOT_IN_PO"));

        // 4. 유통기한 관리 상품 체크
        if (product.getHasExpiry()) {
            if (lineReq.getExpiryDate() == null) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    "Expiry date is required for products with expiry management");
                throw new InboundException("Expiry date is required", "EXPIRY_DATE_REQUIRED");
            }
            if (lineReq.getManufactureDate() == null) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    "Manufacture date is required for products with expiry management");
                throw new InboundException("Manufacture date is required", "MANUFACTURE_DATE_REQUIRED");
            }

            // 유통기한 잔여율 체크
            double remainingPct = calculateRemainingShelfLifePct(lineReq.getManufactureDate(), lineReq.getExpiryDate());
            if (remainingPct < product.getMinRemainingShelfLifePct()) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, product.getMinRemainingShelfLifePct()));
                throw new InboundException(
                    String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, product.getMinRemainingShelfLifePct()),
                    "SHORT_SHELF_LIFE");
            }
        }

        // 5. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
        validateStorageTypeCompatibility(product, location);

        // 6. 실사 동결 로케이션 체크
        if (location.getIsFrozen()) {
            throw new InboundException("Cannot receive to frozen location", "LOCATION_FROZEN");
        }

        // 7. 초과입고 허용률 체크
        validateOverReceiveLimit(po, poLine, lineReq.getQuantity(), product);
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 승인 필요 여부 체크 (유통기한 30~50%)
     */
    private boolean checkIfNeedsApproval(List<InboundReceiptRequest.InboundReceiptLineRequest> lines) {
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : lines) {
            if (lineReq.getManufactureDate() != null && lineReq.getExpiryDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(lineReq.getManufactureDate(), lineReq.getExpiryDate());
                if (remainingPct >= 30 && remainingPct <= 50) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new InboundException("FROZEN products can only be received to FROZEN locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // COLD 상품 → COLD 또는 FROZEN 로케이션
        if (productType == Product.StorageType.COLD &&
            locationType != Product.StorageType.COLD && locationType != Product.StorageType.FROZEN) {
            throw new InboundException("COLD products can only be received to COLD or FROZEN locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // AMBIENT 상품 → AMBIENT 로케이션만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new InboundException("AMBIENT products can only be received to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // HAZMAT 상품 → HAZMAT zone 로케이션만
        if (product.getCategory() == Product.ProductCategory.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new InboundException("HAZMAT products can only be received to HAZMAT zone", "HAZMAT_ZONE_REQUIRED");
        }
    }

    /**
     * 초과입고 허용률 검증 (ALS-WMS-INB-002 카테고리별/PO유형별/성수기 가중치 적용)
     */
    private void validateOverReceiveLimit(PurchaseOrder po, PurchaseOrderLine poLine, int receiveQty, Product product) {
        int orderedQty = poLine.getOrderedQty();
        int alreadyReceivedQty = poLine.getReceivedQty();
        int totalReceiveQty = alreadyReceivedQty + receiveQty;

        // 1. 카테고리별 기본 허용률
        double baseTolerance = getCategoryTolerance(product.getCategory());

        // 2. HAZMAT은 항상 0% (예외 없음)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (totalReceiveQty > orderedQty) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("HAZMAT over-delivery not allowed: ordered=%d, receiving=%d", orderedQty, totalReceiveQty));
                throw new InboundException(
                    String.format("HAZMAT over-delivery not allowed: ordered=%d, total receiving=%d", orderedQty, totalReceiveQty),
                    "HAZMAT_OVER_DELIVERY");
            }
            return;
        }

        // 3. PO 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 4. 성수기 가중치
        double seasonalMultiplier = getSeasonalMultiplier();

        // 5. 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonalMultiplier;
        int maxAllowed = (int) (orderedQty * (1 + finalTolerance));

        log.debug("Over-receive check: category={}, poType={}, baseTolerance={}%, poMultiplier={}, seasonMultiplier={}, finalTolerance={}%, maxAllowed={}",
            product.getCategory(), po.getPoType(), baseTolerance * 100, poTypeMultiplier, seasonalMultiplier, finalTolerance * 100, maxAllowed);

        if (totalReceiveQty > maxAllowed) {
            recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                String.format("Over-delivery: ordered=%d, max allowed=%d, receiving=%d", orderedQty, maxAllowed, totalReceiveQty));
            throw new InboundException(
                String.format("Over-delivery: ordered=%d, max allowed=%d, total receiving=%d", orderedQty, maxAllowed, totalReceiveQty),
                "OVER_DELIVERY");
        }
    }

    /**
     * 카테고리별 초과입고 허용률 (ALS-WMS-INB-002)
     */
    private double getCategoryTolerance(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 0.10;    // 10%
            case FRESH -> 0.05;      // 5%
            case HAZMAT -> 0.00;     // 0%
            case HIGH_VALUE -> 0.03; // 3%
        };
    }

    /**
     * PO 유형별 가중치 (ALS-WMS-INB-002)
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    /**
     * 성수기 가중치 조회 (ALS-WMS-INB-002)
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeasonByDate(today)
            .map(season -> season.getMultiplier().doubleValue())
            .orElse(1.0);
    }

    /**
     * 공급업체 페널티 기록 및 hold 처리 (ALS-WMS-INB-002)
     */
    private void recordPenaltyAndReject(UUID supplierId, UUID poId, SupplierPenalty.PenaltyType penaltyType, String description) {
        // 페널티 기록
        SupplierPenalty penalty = SupplierPenalty.builder()
            .supplierId(supplierId)
            .poId(poId)
            .penaltyType(penaltyType)
            .description(description)
            .build();
        penaltyRepository.save(penalty);

        // 최근 30일 페널티 카운트
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = penaltyRepository.countBySupplierIdAndCreatedAtAfter(supplierId, thirtyDaysAgo);

        // 3회 이상이면 해당 공급업체의 모든 pending PO를 hold로 변경
        if (penaltyCount >= 3) {
            int updatedCount = poRepository.updateStatusToHoldBySupplierId(supplierId);
            log.warn("Supplier {} has {} penalties in last 30 days. {} pending POs set to hold.",
                supplierId, penaltyCount, updatedCount);
        }
    }

    /**
     * 입고 확정 (confirmed) - 재고 반영
     * ALS-WMS-INB-002: confirmed 시점에만 재고 증가
     */
    @Transactional
    public InboundReceiptResponse confirmReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in inspecting or pending_approval status", "INVALID_STATUS");
        }

        // 1. 상태를 confirmed로 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        receiptRepository.save(receipt);

        // 2. 재고 반영
        PurchaseOrder po = poRepository.findById(receipt.getPoId())
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found", "PO_NOT_FOUND"));

        for (InboundReceiptLine line : receipt.getLines()) {
            // 2-1. Inventory 증가
            Inventory inventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                    line.getProductId(), line.getLocationId(), line.getLotNumber())
                .orElseGet(() -> {
                    Inventory newInv = Inventory.builder()
                        .productId(line.getProductId())
                        .locationId(line.getLocationId())
                        .lotNumber(line.getLotNumber())
                        .expiryDate(line.getExpiryDate())
                        .manufactureDate(line.getManufactureDate())
                        .receivedAt(receipt.getReceivedAt())
                        .quantity(0)
                        .build();
                    return newInv;
                });

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);

            // 2-2. Location.current_qty 증가
            Location location = locationRepository.findById(line.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found", "LOCATION_NOT_FOUND"));
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 2-3. PO Line received_qty 누적 갱신
            PurchaseOrderLine poLine = poLineRepository.findByPoIdAndProductId(po.getPoId(), line.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("PO line not found", "PO_LINE_NOT_FOUND"));
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            poLineRepository.save(poLine);
        }

        // 3. PO 상태 갱신 (모든 라인 완납 여부 체크)
        updatePurchaseOrderStatus(po);

        return toResponse(receipt);
    }

    /**
     * PO 상태 갱신 (completed / partial)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = poLineRepository.findByPoId(po.getPoId());
        boolean allFulfilled = lines.stream().allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        poRepository.save(po);
    }

    /**
     * 입고 거부 (rejected)
     */
    @Transactional
    public InboundReceiptResponse rejectReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in inspecting or pending_approval status", "INVALID_STATUS");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     */
    @Transactional
    public InboundReceiptResponse approveReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in pending_approval status", "INVALID_STATUS");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));
        return toResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getReceipts(Pageable pageable) {
        return receiptRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Entity -> DTO 변환
     */
    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
            .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                .receiptLineId(line.getReceiptLineId())
                .productId(line.getProductId())
                .locationId(line.getLocationId())
                .quantity(line.getQuantity())
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .build())
            .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPoId())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .createdAt(receipt.getCreatedAt())
            .lines(lineResponses)
            .build();
    }
}
