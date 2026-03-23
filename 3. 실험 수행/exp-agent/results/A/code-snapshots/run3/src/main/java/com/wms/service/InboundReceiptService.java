package com.wms.service;

import com.wms.dto.InboundReceiptLineRequest;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
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

    public InboundReceiptService(
            InboundReceiptRepository inboundReceiptRepository,
            InboundReceiptLineRepository inboundReceiptLineRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderLineRepository purchaseOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SupplierPenaltyRepository supplierPenaltyRepository,
            SeasonalConfigRepository seasonalConfigRepository) {
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.inboundReceiptLineRepository = inboundReceiptLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.supplierPenaltyRepository = supplierPenaltyRepository;
        this.seasonalConfigRepository = seasonalConfigRepository;
    }

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new IllegalArgumentException("PO not found"));

        // 2. 입고 전표 생성 (inspecting 상태)
        InboundReceipt receipt = new InboundReceipt();
        receipt.setReceiptNumber(request.getReceiptNumber());
        receipt.setPurchaseOrder(po);
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receipt.setReceivedAt(request.getReceivedAt());
        receipt = inboundReceiptRepository.save(receipt);

        // 3. 입고 라인 검증 및 생성
        List<InboundReceiptLine> lines = new ArrayList<>();
        boolean needsApproval = false;

        for (InboundReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new IllegalArgumentException("PO Line not found"));

            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));

            // 3-1. 실사 동결 로케이션 체크
            if (location.getIsFrozen()) {
                throw new IllegalArgumentException("Location is frozen for cycle count");
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 검증
            if (product.getRequiresExpiryTracking()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("Expiry date is required for product: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("Manufacture date is required for product: " + product.getSku());
                }

                // 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        LocalDate.now()
                );

                // 성수기 확인
                var seasonOpt = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now());
                boolean isPeakSeason = seasonOpt.isPresent();

                Integer minPct = product.getMinRemainingShelfLifePct() != null
                        ? product.getMinRemainingShelfLifePct()
                        : 30;

                // 성수기에는 유통기한 30% 미만도 입고 허용
                if (!isPeakSeason && remainingPct < minPct) {
                    // 페널티 기록 및 거부
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "Shelf life remaining: " + remainingPct + "%, minimum: " + minPct + "%");
                    throw new IllegalArgumentException("Shelf life remaining is too short: " + remainingPct + "%");
                }

                if (remainingPct >= minPct && remainingPct <= 50) {
                    needsApproval = true;
                }
            }

            // 3-4. 초과입고 체크
            int allowedQty = calculateAllowedQuantity(poLine, po, product);
            int totalReceived = poLine.getReceivedQuantity() + lineReq.getQuantity();

            if (totalReceived > allowedQty) {
                // 페널티 기록 및 거부
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        "Received: " + totalReceived + ", Allowed: " + allowedQty);
                throw new IllegalArgumentException("Over delivery detected. Allowed: " + allowedQty +
                        ", Trying to receive: " + totalReceived);
            }

            // 3-5. 로케이션 용량 체크
            if (location.getCurrentQuantity() + lineReq.getQuantity() > location.getCapacity()) {
                throw new IllegalArgumentException("Location capacity exceeded");
            }

            // 입고 라인 생성
            InboundReceiptLine line = new InboundReceiptLine();
            line.setInboundReceipt(receipt);
            line.setPurchaseOrderLine(poLine);
            line.setProduct(product);
            line.setLocation(location);
            line.setQuantity(lineReq.getQuantity());
            line.setLotNumber(lineReq.getLotNumber());
            line.setManufactureDate(lineReq.getManufactureDate());
            line.setExpiryDate(lineReq.getExpiryDate());

            lines.add(inboundReceiptLineRepository.save(line));
        }

        // 4. 유통기한 경고가 있으면 pending_approval 상태로 변경
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
            receipt = inboundReceiptRepository.save(receipt);
        }

        return buildResponse(receipt, lines);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting) {
            throw new IllegalArgumentException("Receipt is not in inspecting status");
        }

        // 1. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Inventory inventory = new Inventory();
            inventory.setProduct(line.getProduct());
            inventory.setLocation(line.getLocation());
            inventory.setQuantity(line.getQuantity());
            inventory.setLotNumber(line.getLotNumber());
            inventory.setManufactureDate(line.getManufactureDate());
            inventory.setExpiryDate(line.getExpiryDate());
            inventory.setReceivedAt(receipt.getReceivedAt());
            inventoryRepository.save(inventory);

            // 로케이션 현재 수량 업데이트
            Location location = line.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + line.getQuantity());
            location.setUpdatedAt(OffsetDateTime.now());
            locationRepository.save(location);

            // PO Line 입고 수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() + line.getQuantity());
            poLine.setUpdatedAt(OffsetDateTime.now());
            purchaseOrderLineRepository.save(poLine);
        }

        // 2. PO 상태 업데이트
        PurchaseOrder po = receipt.getPurchaseOrder();
        List<PurchaseOrderLine> allPoLines = purchaseOrderLineRepository.findByPurchaseOrderId(po.getId());

        boolean allFullyReceived = allPoLines.stream()
                .allMatch(line -> line.getReceivedQuantity() >= line.getOrderedQuantity());
        boolean anyPartiallyReceived = allPoLines.stream()
                .anyMatch(line -> line.getReceivedQuantity() > 0 && line.getReceivedQuantity() < line.getOrderedQuantity());

        if (allFullyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyPartiallyReceived || allPoLines.stream().anyMatch(line -> line.getReceivedQuantity() > 0)) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        po.setUpdatedAt(OffsetDateTime.now());
        purchaseOrderRepository.save(po);

        // 3. 입고 확정
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = inboundReceiptRepository.save(receipt);

        return buildResponse(receipt, lines);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID id, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receipt.setRejectionReason(reason);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = inboundReceiptRepository.save(receipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        return buildResponse(receipt, lines);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalArgumentException("Receipt is not pending approval");
        }

        // pending_approval -> inspecting으로 변경하여 이후 confirm 가능하도록
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = inboundReceiptRepository.save(receipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        return buildResponse(receipt, lines);
    }

    public InboundReceiptResponse getInboundReceipt(UUID id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        return buildResponse(receipt, lines);
    }

    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(receipt -> {
                    List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(receipt.getId());
                    return buildResponse(receipt, lines);
                })
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품은 FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new IllegalArgumentException("FROZEN products require FROZEN storage");
        }

        // COLD 상품은 COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
                locationType != Product.StorageType.COLD &&
                locationType != Product.StorageType.FROZEN) {
            throw new IllegalArgumentException("COLD products require COLD or FROZEN storage");
        }

        // AMBIENT 상품은 AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("AMBIENT products require AMBIENT storage");
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private int calculateAllowedQuantity(PurchaseOrderLine poLine, PurchaseOrder po, Product product) {
        int orderedQty = poLine.getOrderedQuantity();

        // 카테고리별 기본 허용률
        double categoryRate = switch (product.getCategory()) {
            case GENERAL -> 0.30;
            case FRESH -> 0.05;
            case HAZMAT -> 0.00;
            case HIGH_VALUE -> 0.03;
        };

        // HAZMAT은 무조건 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            return orderedQty;
        }

        // PO 타입별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 성수기 가중치
        double seasonalMultiplier = 1.0;
        var seasonOpt = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now());
        if (seasonOpt.isPresent()) {
            seasonalMultiplier = seasonOpt.get().getMultiplier().doubleValue();
        }

        // 최종 허용률 계산
        double finalRate = categoryRate * poTypeMultiplier * seasonalMultiplier;

        return orderedQty + (int) (orderedQty * finalRate);
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType type, String description) {
        SupplierPenalty penalty = new SupplierPenalty();
        penalty.setSupplier(supplier);
        penalty.setPenaltyType(type);
        penalty.setDescription(description);
        penalty.setOccurredAt(OffsetDateTime.now());
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상이면 PO hold
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            purchaseOrderRepository.holdPendingOrdersBySupplierId(supplier.getId());
        }
    }

    private InboundReceiptResponse buildResponse(InboundReceipt receipt, List<InboundReceiptLine> lines) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setId(receipt.getId());
        response.setReceiptNumber(receipt.getReceiptNumber());
        response.setPurchaseOrderId(receipt.getPurchaseOrder().getId());
        response.setStatus(receipt.getStatus().name());
        response.setReceivedAt(receipt.getReceivedAt());
        response.setConfirmedAt(receipt.getConfirmedAt());
        response.setRejectionReason(receipt.getRejectionReason());

        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    InboundReceiptResponse.InboundReceiptLineResponse lineResp =
                            new InboundReceiptResponse.InboundReceiptLineResponse();
                    lineResp.setId(line.getId());
                    lineResp.setPurchaseOrderLineId(line.getPurchaseOrderLine().getId());
                    lineResp.setProductId(line.getProduct().getId());
                    lineResp.setLocationId(line.getLocation().getId());
                    lineResp.setQuantity(line.getQuantity());
                    lineResp.setLotNumber(line.getLotNumber());
                    lineResp.setManufactureDate(line.getManufactureDate());
                    lineResp.setExpiryDate(line.getExpiryDate());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);
        return response;
    }
}
