package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private final SupplierRepository supplierRepository;
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
            SupplierRepository supplierRepository,
            SeasonalConfigRepository seasonalConfigRepository
    ) {
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.inboundReceiptLineRepository = inboundReceiptLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.supplierPenaltyRepository = supplierPenaltyRepository;
        this.supplierRepository = supplierRepository;
        this.seasonalConfigRepository = seasonalConfigRepository;
    }

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("발주서를 찾을 수 없습니다", "PO_NOT_FOUND"));

        if (po.getStatus() == PurchaseOrder.Status.cancelled) {
            throw new BusinessException("취소된 발주서에는 입고할 수 없습니다", "PO_CANCELLED");
        }

        if (po.getStatus() == PurchaseOrder.Status.hold) {
            throw new BusinessException("보류된 발주서에는 입고할 수 없습니다", "PO_HOLD");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = new InboundReceipt();
        receipt.setPurchaseOrder(po);
        receipt.setReceivedBy(request.getReceivedBy());
        receipt.setStatus(InboundReceipt.Status.inspecting);

        boolean needsApproval = false;
        List<String> validationErrors = new ArrayList<>();

        // 3. 각 라인 검증 및 생성
        for (InboundReceiptRequest.LineItem lineItem : request.getLines()) {
            Product product = productRepository.findById(lineItem.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            Location location = locationRepository.findById(lineItem.getLocationId())
                    .orElseThrow(() -> new BusinessException("로케이션을 찾을 수 없습니다", "LOCATION_NOT_FOUND"));

            // 3-1. 실사 동결 체크 (ALS-WMS-INB-002 Constraint)
            if (location.getIsFrozen()) {
                throw new BusinessException(
                        "실사 중인 로케이션에는 입고할 수 없습니다: " + location.getCode(),
                        "LOCATION_FROZEN"
                );
            }

            // 3-2. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크 (ALS-WMS-INB-002 Constraint)
            if (product.getHasExpiry()) {
                if (lineItem.getExpiryDate() == null) {
                    throw new BusinessException(
                            "유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku(),
                            "EXPIRY_DATE_REQUIRED"
                    );
                }
                if (product.getManufactureDateRequired() && lineItem.getManufactureDate() == null) {
                    throw new BusinessException(
                            "유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku(),
                            "MANUFACTURE_DATE_REQUIRED"
                    );
                }

                // 3-4. 유통기한 잔여율 체크 (ALS-WMS-INB-002 Constraint)
                ShelfLifeCheckResult shelfLifeResult = checkShelfLife(
                        product,
                        lineItem.getManufactureDate(),
                        lineItem.getExpiryDate()
                );

                if (shelfLifeResult == ShelfLifeCheckResult.REJECTED) {
                    // 페널티 기록 및 입고 거부
                    recordSupplierPenalty(
                            po.getSupplier(),
                            po.getPoId(),
                            SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "유통기한 부족: " + product.getSku()
                    );
                    throw new BusinessException(
                            "유통기한 잔여율이 최소 기준 미만입니다: " + product.getSku(),
                            "SHELF_LIFE_INSUFFICIENT"
                    );
                } else if (shelfLifeResult == ShelfLifeCheckResult.NEEDS_APPROVAL) {
                    needsApproval = true;
                }
            } else {
                // 유통기한 비관리 상품은 null로 설정
                lineItem.setExpiryDate(null);
                lineItem.setManufactureDate(null);
            }

            // 3-5. 초과입고 허용률 체크 (ALS-WMS-INB-002 Constraint)
            validateOverReceive(po, product, lineItem.getProductId(), lineItem.getQuantity());

            // 3-6. 입고 라인 생성
            InboundReceiptLine receiptLine = new InboundReceiptLine();
            receiptLine.setInboundReceipt(receipt);
            receiptLine.setProduct(product);
            receiptLine.setLocation(location);
            receiptLine.setQuantity(lineItem.getQuantity());
            receiptLine.setLotNumber(lineItem.getLotNumber());
            receiptLine.setExpiryDate(lineItem.getExpiryDate());
            receiptLine.setManufactureDate(lineItem.getManufactureDate());

            receipt.getLines().add(receiptLine);
        }

        // 4. 유통기한 경고가 있으면 승인 대기 상태로 변경
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.Status.pending_approval);
        }

        // 5. 저장
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting) {
            throw new BusinessException(
                    "검수 중 상태의 입고만 확정할 수 있습니다",
                    "INVALID_STATUS"
            );
        }

        // 1. 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
            updateLocationCurrentQty(line.getLocation(), line.getQuantity());
        }

        // 2. PO 라인 received_qty 누적 갱신
        updatePurchaseOrderLines(receipt);

        // 3. PO 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        // 4. 입고 상태 변경
        receipt.setStatus(InboundReceipt.Status.confirmed);
        receipt.setConfirmedAt(Instant.now());

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() == InboundReceipt.Status.confirmed) {
            throw new BusinessException(
                    "이미 확정된 입고는 거부할 수 없습니다",
                    "ALREADY_CONFIRMED"
            );
        }

        receipt.setStatus(InboundReceipt.Status.rejected);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new BusinessException(
                    "승인 대기 상태의 입고만 승인할 수 있습니다",
                    "INVALID_STATUS"
            );
        }

        // 승인 후 검수 중 상태로 변경 (이후 확정 가능)
        receipt.setStatus(InboundReceipt.Status.inspecting);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002 Constraint)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = location.getStorageType();

        // HAZMAT 상품은 HAZMAT zone에만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException(
                        "위험물(HAZMAT) 상품은 HAZMAT zone에만 입고할 수 있습니다",
                        "HAZMAT_ZONE_REQUIRED"
                );
            }
        }

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN) {
            if (locationType != Location.StorageType.FROZEN) {
                throw new BusinessException(
                        "FROZEN 상품은 FROZEN 로케이션에만 입고할 수 있습니다",
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }
        }

        // COLD 상품 → COLD 또는 FROZEN 로케이션
        if (productType == Product.StorageType.COLD) {
            if (locationType != Location.StorageType.COLD && locationType != Location.StorageType.FROZEN) {
                throw new BusinessException(
                        "COLD 상품은 COLD 또는 FROZEN 로케이션에만 입고할 수 있습니다",
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }
        }

        // AMBIENT 상품 → AMBIENT 로케이션만
        if (productType == Product.StorageType.AMBIENT) {
            if (locationType != Location.StorageType.AMBIENT) {
                throw new BusinessException(
                        "AMBIENT 상품은 AMBIENT 로케이션에만 입고할 수 있습니다",
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }
        }
    }

    /**
     * 유통기한 잔여율 체크 (ALS-WMS-INB-002 Constraint)
     */
    private enum ShelfLifeCheckResult {
        ACCEPTED, NEEDS_APPROVAL, REJECTED
    }

    private ShelfLifeCheckResult checkShelfLife(Product product, LocalDate manufactureDate, LocalDate expiryDate) {
        if (manufactureDate == null || expiryDate == null) {
            return ShelfLifeCheckResult.ACCEPTED;
        }

        LocalDate today = LocalDate.now();
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalShelfLife <= 0) {
            throw new BusinessException("제조일이 유통기한보다 늦습니다", "INVALID_DATES");
        }

        double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

        int minPct = product.getMinRemainingShelfLifePct() != null ? product.getMinRemainingShelfLifePct() : 30;

        if (remainingPct < minPct) {
            return ShelfLifeCheckResult.REJECTED;
        } else if (remainingPct < 50) {
            return ShelfLifeCheckResult.NEEDS_APPROVAL;
        } else {
            return ShelfLifeCheckResult.ACCEPTED;
        }
    }

    /**
     * 초과입고 허용률 체크 (ALS-WMS-INB-002 Constraint)
     */
    private void validateOverReceive(PurchaseOrder po, Product product, UUID productId, int quantity) {
        // PO 라인 조회
        PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoIdAndProductId(po.getPoId(), productId)
                .orElseThrow(() -> new BusinessException(
                        "발주서에 해당 상품이 없습니다",
                        "PRODUCT_NOT_IN_PO"
                ));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int newReceivedQty = receivedQty + quantity;

        // 카테고리별 기본 허용률
        double baseTolerance = getBaseTolerance(product.getCategory());

        // HAZMAT은 무조건 0% (ALS-WMS-INB-002 Constraint)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            baseTolerance = 0.0;
        } else {
            // PO 유형별 가중치
            double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());
            baseTolerance *= poTypeMultiplier;

            // 성수기 가중치
            double seasonalMultiplier = getSeasonalMultiplier();
            baseTolerance *= seasonalMultiplier;
        }

        int maxAllowed = (int) (orderedQty * (1 + baseTolerance));

        if (newReceivedQty > maxAllowed) {
            // 초과입고 페널티 기록
            recordSupplierPenalty(
                    po.getSupplier(),
                    po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("초과입고: %s (주문 %d, 입고 시도 %d, 허용 %d)",
                            product.getSku(), orderedQty, newReceivedQty, maxAllowed)
            );

            throw new BusinessException(
                    String.format("초과입고 허용 수량을 초과했습니다 (주문: %d, 입고 시도: %d, 최대 허용: %d)",
                            orderedQty, newReceivedQty, maxAllowed),
                    "OVER_RECEIVE"
            );
        }
    }

    /**
     * 카테고리별 기본 허용률 (ALS-WMS-INB-002 Constraint)
     */
    private double getBaseTolerance(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 0.10; // 10%
            case FRESH:
                return 0.05; // 5%
            case HAZMAT:
                return 0.00; // 0%
            case HIGH_VALUE:
                return 0.03; // 3%
            default:
                return 0.10;
        }
    }

    /**
     * PO 유형별 가중치 (ALS-WMS-INB-002 Constraint)
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
     * 성수기 가중치 (ALS-WMS-INB-002 Constraint)
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeason(today)
                .map(season -> season.getMultiplier().doubleValue())
                .orElse(1.0);
    }

    /**
     * 공급업체 페널티 기록 및 PO hold 체크 (ALS-WMS-INB-002 Constraint)
     */
    private void recordSupplierPenalty(Supplier supplier, UUID poId, SupplierPenalty.PenaltyType penaltyType, String description) {
        SupplierPenalty penalty = new SupplierPenalty();
        penalty.setSupplier(supplier);
        penalty.setPoId(poId);
        penalty.setPenaltyType(penaltyType);
        penalty.setDescription(description);
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 개수 확인
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndCreatedAtAfter(
                supplier.getSupplierId(),
                thirtyDaysAgo
        );

        // 3회 이상이면 pending PO를 hold로 변경
        if (penaltyCount >= 3) {
            List<PurchaseOrder> pendingPos = purchaseOrderRepository.findPendingBySupplier(supplier.getSupplierId());
            for (PurchaseOrder po : pendingPos) {
                po.setStatus(PurchaseOrder.Status.hold);
                purchaseOrderRepository.save(po);
            }

            // 공급업체 상태도 hold로 변경
            supplier.setStatus(Supplier.Status.hold);
            supplierRepository.save(supplier);
        }
    }

    /**
     * 재고 업데이트 (ALS-WMS-INB-002 Constraint - 확정 시점에만 반영)
     */
    private void updateInventory(InboundReceiptLine line) {
        // 동일한 product + location + lot 조합 찾기
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
        ).orElse(null);

        if (inventory == null) {
            // 신규 재고 생성
            inventory = new Inventory();
            inventory.setProduct(line.getProduct());
            inventory.setLocation(line.getLocation());
            inventory.setLotNumber(line.getLotNumber());
            inventory.setQuantity(line.getQuantity());
            inventory.setExpiryDate(line.getExpiryDate());
            inventory.setManufactureDate(line.getManufactureDate());
            inventory.setReceivedAt(Instant.now());
        } else {
            // 기존 재고에 수량 추가
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
        }

        inventoryRepository.save(inventory);
    }

    /**
     * 로케이션 현재 수량 업데이트 (ALS-WMS-INB-002 Constraint)
     */
    private void updateLocationCurrentQty(Location location, int quantity) {
        location.setCurrentQty(location.getCurrentQty() + quantity);

        if (location.getCurrentQty() > location.getCapacity()) {
            throw new BusinessException(
                    "로케이션 용량을 초과했습니다: " + location.getCode(),
                    "LOCATION_CAPACITY_EXCEEDED"
            );
        }

        locationRepository.save(location);
    }

    /**
     * PO 라인 received_qty 누적 갱신 (ALS-WMS-INB-002 Constraint)
     */
    private void updatePurchaseOrderLines(InboundReceipt receipt) {
        for (InboundReceiptLine line : receipt.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoIdAndProductId(
                    receipt.getPurchaseOrder().getPoId(),
                    line.getProduct().getProductId()
            ).orElseThrow(() -> new BusinessException("PO 라인을 찾을 수 없습니다", "PO_LINE_NOT_FOUND"));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }
    }

    /**
     * PO 상태 업데이트 (ALS-WMS-INB-002 Constraint)
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
            po.setStatus(PurchaseOrder.Status.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.Status.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity to DTO 변환
     */
    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setReceiptId(receipt.getReceiptId());
        response.setPoId(receipt.getPurchaseOrder().getPoId());
        response.setStatus(receipt.getStatus().name());
        response.setReceivedBy(receipt.getReceivedBy());
        response.setReceivedAt(receipt.getReceivedAt());
        response.setConfirmedAt(receipt.getConfirmedAt());

        List<InboundReceiptResponse.LineItemResponse> lineResponses = receipt.getLines().stream()
                .map(line -> {
                    InboundReceiptResponse.LineItemResponse lineResponse = new InboundReceiptResponse.LineItemResponse();
                    lineResponse.setReceiptLineId(line.getReceiptLineId());
                    lineResponse.setProductId(line.getProduct().getProductId());
                    lineResponse.setLocationId(line.getLocation().getLocationId());
                    lineResponse.setQuantity(line.getQuantity());
                    lineResponse.setLotNumber(line.getLotNumber());
                    lineResponse.setExpiryDate(line.getExpiryDate());
                    lineResponse.setManufactureDate(line.getManufactureDate());
                    return lineResponse;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);

        return response;
    }
}
