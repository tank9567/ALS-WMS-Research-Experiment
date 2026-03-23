package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
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
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Purchase order not found"));

        // 2. 입고 receipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .receivedBy(request.getReceivedBy())
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedAt(OffsetDateTime.now())
                .build();

        // 3. 입고 라인 검증 및 추가
        List<InboundReceiptLine> lines = new ArrayList<>();
        boolean requiresApproval = false;

        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Location not found: " + lineReq.getLocationId()));

            // 3-1. 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                if (lineReq.getExpiryDate() == null || lineReq.getManufactureDate() == null) {
                    throw new BusinessException("VALIDATION_ERROR", "Expiry date and manufacture date are required for product: " + product.getSku());
                }

                // 잔여 유통기한 비율 체크
                double remainingPct = calculateRemainingShelfLifePct(lineReq.getExpiryDate(), lineReq.getManufactureDate());

                // 성수기 정책: 30% 미만도 입고 가능, 30~50%만 승인 필요
                if (remainingPct >= 30 && remainingPct < 50) {
                    // 승인 필요
                    requiresApproval = true;
                }
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 실사 동결 로케이션 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count: " + location.getCode());
            }

            // 3-4. 초과입고 검증 (확정 시 체크하지만 미리 검증)
            validateOverDelivery(po, product, lineReq.getQuantity());

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

        // 승인이 필요한 경우 상태 변경
        if (requiresApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 확정
     */
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be confirmed in current status: " + receipt.getStatus());
        }

        PurchaseOrder po = receipt.getPurchaseOrder();

        // 각 라인별 처리
        for (InboundReceiptLine line : receipt.getLines()) {
            Product product = line.getProduct();
            Location location = line.getLocation();

            // 1. 초과입고 재검증
            validateOverDelivery(po, product, line.getQuantity());

            // 2. 재고 반영
            Inventory inventory = inventoryRepository
                    .findByProductAndLocationAndLotNumber(product, location, line.getLotNumber())
                    .orElse(Inventory.builder()
                            .product(product)
                            .location(location)
                            .lotNumber(line.getLotNumber())
                            .quantity(0)
                            .expiryDate(line.getExpiryDate())
                            .manufactureDate(line.getManufactureDate())
                            .receivedAt(OffsetDateTime.now())
                            .isExpired(false)
                            .build());

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());

            if (inventory.getExpiryDate() == null && line.getExpiryDate() != null) {
                inventory.setExpiryDate(line.getExpiryDate());
            }
            if (inventory.getManufactureDate() == null && line.getManufactureDate() != null) {
                inventory.setManufactureDate(line.getManufactureDate());
            }

            inventoryRepository.save(inventory);

            // 3. 로케이션 current_qty 증가
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 4. PO line received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderAndProduct(po, product)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "PO line not found for product: " + product.getSku()));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 5. PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 6. 입고 상태 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 거부
     */
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() == InboundReceipt.ReceiptStatus.confirmed ||
            receipt.getStatus() == InboundReceipt.ReceiptStatus.rejected) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be rejected in current status: " + receipt.getStatus());
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 승인 (pending_approval 상태에서)
     */
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in pending_approval status");
        }

        // 승인 후 inspecting으로 변경하여 확정 가능하게 함
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        return mapToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 초과입고 검증
     */
    private void validateOverDelivery(PurchaseOrder po, Product product, int incomingQty) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderAndProduct(po, product)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not in PO: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalAfterReceipt = receivedQty + incomingQty;

        // 카테고리별 기본 허용률
        double allowanceRate = getCategoryAllowanceRate(product.getCategory());

        // 발주 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 성수기 가중치
        double seasonalMultiplier = getSeasonalMultiplier();

        // HAZMAT은 어떤 경우에도 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            allowanceRate = 0.0;
        } else {
            allowanceRate = allowanceRate * poTypeMultiplier * seasonalMultiplier;
        }

        double maxAllowed = orderedQty * (1 + allowanceRate / 100.0);

        if (totalAfterReceipt > maxAllowed) {
            // 초과입고 페널티 기록
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                    "Over delivery: ordered=" + orderedQty + ", total=" + totalAfterReceipt + ", allowed=" + maxAllowed);

            throw new BusinessException("OVER_DELIVERY", "Exceeded maximum allowed quantity. Ordered: " + orderedQty +
                    ", Total after receipt: " + totalAfterReceipt + ", Max allowed: " + maxAllowed);
        }
    }

    /**
     * 카테고리별 초과입고 허용률 (기본값)
     */
    private double getCategoryAllowanceRate(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 30.0;
            case FRESH:
                return 5.0;
            case HAZMAT:
                return 0.0;
            case HIGH_VALUE:
                return 3.0;
            default:
                return 30.0;
        }
    }

    /**
     * 발주 유형별 가중치
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        switch (poType) {
            case NORMAL:
                return 1.0;
            case URGENT:
                return 2.0;
            case IMPORT:
                return 1.5;
            default:
                return 1.0;
        }
    }

    /**
     * 성수기 가중치
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeasonByDate(today)
                .map(season -> season.getMultiplier().doubleValue())
                .orElse(1.0);
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = location.getStorageType();

        // HAZMAT은 일반 로케이션 적재 허용 (HAZMAT zone 우선 권장)
        // 더 이상 HAZMAT zone 강제하지 않음

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Location.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "FROZEN products require FROZEN location");
        }

        // COLD 상품 → COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
            locationType != Location.StorageType.COLD &&
            locationType != Location.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "COLD products require COLD or FROZEN location");
        }

        // AMBIENT 상품 → AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "AMBIENT products require AMBIENT location");
        }
    }

    /**
     * 공급업체 페널티 기록 및 PO hold 처리
     */
    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType, UUID poId, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .description(description)
                .poId(poId)
                .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierAndCreatedAtAfter(supplier, thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 pending PO를 모두 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findBySupplierAndStatus(supplier, PurchaseOrder.PoStatus.pending);
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(po);
            }

            // 공급업체 상태도 hold로
            supplier.setStatus(Supplier.SupplierStatus.hold);
        }
    }

    /**
     * PO 상태 업데이트
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQty() > 0) {
                anyReceived = true;
            }
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allCompleted = false;
            }
        }

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity -> Response DTO 변환
     */
    private InboundReceiptResponse mapToResponse(InboundReceipt receipt) {
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
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .receiptId(receipt.getReceiptId())
                .poId(receipt.getPurchaseOrder().getPoId())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedBy(receipt.getReceivedBy())
                .receivedAt(receipt.getReceivedAt())
                .confirmedAt(receipt.getConfirmedAt())
                .createdAt(receipt.getCreatedAt())
                .lines(lineResponses)
                .build();
    }
}
