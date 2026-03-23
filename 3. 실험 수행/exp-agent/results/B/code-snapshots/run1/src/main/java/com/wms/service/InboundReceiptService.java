package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.repository.*;
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
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    public InboundReceiptService(
            InboundReceiptRepository inboundReceiptRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderLineRepository purchaseOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SupplierPenaltyRepository supplierPenaltyRepository,
            SeasonalConfigRepository seasonalConfigRepository) {
        this.inboundReceiptRepository = inboundReceiptRepository;
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
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new IllegalArgumentException("PO not found"));

        if ("hold".equals(po.getStatus())) {
            throw new IllegalStateException("PO is on hold");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = new InboundReceipt();
        receipt.setPurchaseOrder(po);
        receipt.setReceivedBy(request.getReceivedBy());
        receipt.setStatus("inspecting");

        boolean needsApproval = false;
        List<InboundReceiptLine> lines = new ArrayList<>();

        // 3. 라인별 검증
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));

            // 실사 동결 체크
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("Location is frozen for cycle count");
            }

            // 보관 유형 호환성 체크
            validateStorageCompatibility(product, location);

            // 유통기한 관리 상품 체크
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineReq.getExpiryDate() == null || lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("Expiry date and manufacture date are required for expiry-managed products");
                }

                // 잔여 유통기한 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate()
                );

                int minPct = product.getMinRemainingShelfLifePct() != null ? product.getMinRemainingShelfLifePct() : 30;

                // 성수기 여부 확인
                SeasonalConfig activeSeason = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now()).orElse(null);
                boolean isPeakSeason = (activeSeason != null);

                // 성수기가 아닐 때만 유통기한 최소값 검증
                if (!isPeakSeason && remainingPct < minPct) {
                    // 페널티 기록
                    recordPenalty(po.getSupplier(), "SHORT_SHELF_LIFE", po.getPoId(), "Remaining shelf life is below minimum");
                    throw new IllegalArgumentException("Remaining shelf life is below minimum threshold");
                }

                if (remainingPct >= minPct && remainingPct < 50) {
                    needsApproval = true;
                }
            }

            // 초과입고 체크
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderIdAndProductId(po.getPoId(), product.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not in PO"));

            int newTotalReceived = poLine.getReceivedQty() + lineReq.getQuantity();
            double allowedPct = calculateOverReceivePct(product.getCategory(), po.getPoType());

            if (newTotalReceived > poLine.getOrderedQty() * (1 + allowedPct / 100.0)) {
                // 페널티 기록
                recordPenalty(po.getSupplier(), "OVER_DELIVERY", po.getPoId(), "Over-delivery exceeds allowed tolerance");
                throw new IllegalArgumentException("Over-delivery exceeds allowed tolerance");
            }

            // 라인 생성
            InboundReceiptLine line = new InboundReceiptLine();
            line.setInboundReceipt(receipt);
            line.setProduct(product);
            line.setLocation(location);
            line.setQuantity(lineReq.getQuantity());
            line.setLotNumber(lineReq.getLotNumber());
            line.setExpiryDate(lineReq.getExpiryDate());
            line.setManufactureDate(lineReq.getManufactureDate());

            lines.add(line);
        }

        receipt.setLines(lines);

        if (needsApproval) {
            receipt.setStatus("pending_approval");
        }

        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (!"inspecting".equals(receipt.getStatus()) && !"pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt cannot be confirmed in current status");
        }

        if ("pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt requires approval before confirmation");
        }

        // 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line, receipt.getReceivedAt());
            updateLocation(line.getLocation(), line.getQuantity());
            updatePurchaseOrderLine(line.getProduct(), receipt.getPurchaseOrder(), line.getQuantity());
        }

        receipt.setStatus("confirmed");
        receipt.setConfirmedAt(OffsetDateTime.now());

        // PO 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (!"inspecting".equals(receipt.getStatus()) && !"pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt cannot be rejected in current status");
        }

        receipt.setStatus("rejected");
        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (!"pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt is not pending approval");
        }

        receipt.setStatus("inspecting");
        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Helper methods
    private void validateStorageCompatibility(Product product, Location location) {
        String productType = product.getStorageType();
        String locationType = location.getStorageType();
        String zone = location.getZone();

        if ("HAZMAT".equals(product.getCategory()) && !"HAZMAT".equals(zone)) {
            throw new IllegalArgumentException("HAZMAT products must be stored in HAZMAT zone");
        }

        if ("FROZEN".equals(productType) && !"FROZEN".equals(locationType)) {
            throw new IllegalArgumentException("FROZEN products require FROZEN storage");
        }

        if ("COLD".equals(productType) && !"COLD".equals(locationType) && !"FROZEN".equals(locationType)) {
            throw new IllegalArgumentException("COLD products require COLD or FROZEN storage");
        }

        if ("AMBIENT".equals(productType) && !"AMBIENT".equals(locationType)) {
            throw new IllegalArgumentException("AMBIENT products require AMBIENT storage");
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private double calculateOverReceivePct(String category, String poType) {
        // 카테고리별 기본 허용률
        double basePct;
        switch (category) {
            case "GENERAL":
                basePct = 30.0;
                break;
            case "FRESH":
                basePct = 5.0;
                break;
            case "HAZMAT":
                return 0.0; // HAZMAT은 항상 0%
            case "HIGH_VALUE":
                basePct = 3.0;
                break;
            default:
                basePct = 10.0;
        }

        // 발주 유형별 가중치
        double multiplier = 1.0;
        switch (poType) {
            case "URGENT":
                multiplier = 2.0;
                break;
            case "IMPORT":
                multiplier = 1.5;
                break;
            default:
                multiplier = 1.0;
        }

        // 성수기 가중치
        SeasonalConfig season = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now()).orElse(null);
        if (season != null) {
            multiplier *= season.getMultiplier().doubleValue();
        }

        return basePct * multiplier;
    }

    private void recordPenalty(Supplier supplier, String penaltyType, UUID poId, String description) {
        SupplierPenalty penalty = new SupplierPenalty();
        penalty.setSupplier(supplier);
        penalty.setPenaltyType(penaltyType);
        penalty.setPoId(poId);
        penalty.setDescription(description);
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndCreatedAtAfter(supplier.getSupplierId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // PO를 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findBySupplierIdAndStatus(supplier.getSupplierId(), "pending");
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus("hold");
                purchaseOrderRepository.save(po);
            }
        }
    }

    private void updateInventory(InboundReceiptLine line, OffsetDateTime receivedAt) {
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
        ).orElse(null);

        if (inventory != null) {
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
        } else {
            inventory = new Inventory();
            inventory.setProduct(line.getProduct());
            inventory.setLocation(line.getLocation());
            inventory.setQuantity(line.getQuantity());
            inventory.setLotNumber(line.getLotNumber());
            inventory.setExpiryDate(line.getExpiryDate());
            inventory.setManufactureDate(line.getManufactureDate());
            inventory.setReceivedAt(receivedAt);
        }

        inventoryRepository.save(inventory);
    }

    private void updateLocation(Location location, int quantity) {
        location.setCurrentQty(location.getCurrentQty() + quantity);
        locationRepository.save(location);
    }

    private void updatePurchaseOrderLine(Product product, PurchaseOrder po, int quantity) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderIdAndProductId(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("PO line not found"));

        poLine.setReceivedQty(poLine.getReceivedQty() + quantity);
        purchaseOrderLineRepository.save(poLine);
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(po.getPoId());

        boolean allFulfilled = lines.stream().allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus("completed");
        } else if (anyReceived) {
            po.setStatus("partial");
        }

        purchaseOrderRepository.save(po);
    }

    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setReceiptId(receipt.getReceiptId());
        response.setPoId(receipt.getPurchaseOrder().getPoId());
        response.setPoNumber(receipt.getPurchaseOrder().getPoNumber());
        response.setStatus(receipt.getStatus());
        response.setReceivedBy(receipt.getReceivedBy());
        response.setReceivedAt(receipt.getReceivedAt());
        response.setConfirmedAt(receipt.getConfirmedAt());

        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
                .map(line -> {
                    InboundReceiptResponse.InboundReceiptLineResponse lineResp = new InboundReceiptResponse.InboundReceiptLineResponse();
                    lineResp.setReceiptLineId(line.getReceiptLineId());
                    lineResp.setProductId(line.getProduct().getProductId());
                    lineResp.setProductSku(line.getProduct().getSku());
                    lineResp.setProductName(line.getProduct().getName());
                    lineResp.setLocationId(line.getLocation().getLocationId());
                    lineResp.setLocationCode(line.getLocation().getCode());
                    lineResp.setQuantity(line.getQuantity());
                    lineResp.setLotNumber(line.getLotNumber());
                    lineResp.setExpiryDate(line.getExpiryDate());
                    lineResp.setManufactureDate(line.getManufactureDate());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);

        return response;
    }
}
