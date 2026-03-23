package com.wms.service;

import com.wms.dto.InboundReceiptCreateRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptCreateRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase order not found"));

        // 2. 입고 전표 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedBy(request.getReceivedBy())
                .build();
        inboundReceiptRepository.save(receipt);

        // 3. 입고 라인 검증 및 생성
        for (InboundReceiptCreateRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            validateAndCreateReceiptLine(receipt, po, lineReq);
        }

        return buildReceiptResponse(receipt);
    }

    private void validateAndCreateReceiptLine(InboundReceipt receipt, PurchaseOrder po,
                                               InboundReceiptCreateRequest.InboundReceiptLineRequest lineReq) {
        // 상품 조회
        Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        // 로케이션 조회
        Location location = locationRepository.findById(lineReq.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 1. 실사 동결 로케이션 체크
        if (Boolean.TRUE.equals(location.getIsFrozen())) {
            throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count");
        }

        // 2. 유통기한 관리 상품 검증
        if (Boolean.TRUE.equals(product.getHasExpiry())) {
            if (lineReq.getExpiryDate() == null) {
                throw new BusinessException("MISSING_EXPIRY_DATE", "Expiry date is required for this product");
            }
            if (lineReq.getManufactureDate() == null) {
                throw new BusinessException("MISSING_MANUFACTURE_DATE", "Manufacture date is required for this product");
            }

            // 잔여 유통기한 비율 체크
            double remainingPct = calculateRemainingShelfLifePct(
                    lineReq.getManufactureDate(), lineReq.getExpiryDate());

            int minPct = product.getMinRemainingShelfLifePct() != null
                    ? product.getMinRemainingShelfLifePct() : 30;

            // 성수기 여부 확인
            boolean isSeason = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now()).isPresent();

            if (!isSeason && remainingPct < minPct) {
                // 비성수기에만 유통기한 부족 시 거부 -> 페널티 부과
                recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                        SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                        String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, minPct));
                throw new BusinessException("SHORT_SHELF_LIFE",
                        String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, minPct));
            }

            // 30~50% 범위: 경고 + 승인 필요 (성수기에는 30% 미만도 통과하므로 이 체크도 수정)
            if (!isSeason && remainingPct >= 30 && remainingPct <= 50) {
                receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
            } else if (isSeason && remainingPct < 30) {
                // 성수기에 30% 미만인 경우 승인 필요
                receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
            } else if (remainingPct >= 30 && remainingPct <= 50) {
                // 30~50%는 항상 승인 필요
                receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
            }
        }

        // 3. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, location);

        // 4. 초과입고 체크
        validateOverDelivery(po, product, lineReq.getQuantity());

        // 5. 입고 라인 생성
        InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineReq.getQuantity())
                .lotNumber(lineReq.getLotNumber())
                .expiryDate(lineReq.getExpiryDate())
                .manufactureDate(lineReq.getManufactureDate())
                .build();
        inboundReceiptLineRepository.save(receiptLine);
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        return (double) remainingDays / totalDays * 100.0;
    }

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productStorage = product.getStorageType();
        Location.StorageType locationStorage = location.getStorageType();

        // FROZEN 상품 -> FROZEN 로케이션만
        if (productStorage == Product.StorageType.FROZEN) {
            if (locationStorage != Location.StorageType.FROZEN) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "FROZEN products require FROZEN location");
            }
        }

        // COLD 상품 -> COLD 또는 FROZEN 로케이션
        if (productStorage == Product.StorageType.COLD) {
            if (locationStorage != Location.StorageType.COLD &&
                    locationStorage != Location.StorageType.FROZEN) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "COLD products require COLD or FROZEN location");
            }
        }

        // AMBIENT 상품 -> AMBIENT 로케이션만
        if (productStorage == Product.StorageType.AMBIENT) {
            if (locationStorage != Location.StorageType.AMBIENT) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "AMBIENT products require AMBIENT location");
            }
        }
    }

    private void validateOverDelivery(PurchaseOrder po, Product product, int quantity) {
        // PO 라인 조회
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderIdAndProductId(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND",
                        "Product not found in purchase order"));

        int orderedQty = poLine.getOrderedQty();
        int alreadyReceivedQty = poLine.getReceivedQty();
        int totalReceivingQty = alreadyReceivedQty + quantity;

        // 카테고리별 기본 허용률
        double baseAllowancePct = getCategoryAllowancePct(product.getCategory());

        // 발주 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 성수기 가중치
        BigDecimal seasonalMultiplier = getSeasonalMultiplier();

        // 최종 허용률 계산 (HAZMAT은 항상 0%)
        double finalAllowancePct;
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            finalAllowancePct = 0.0;
        } else {
            finalAllowancePct = baseAllowancePct * poTypeMultiplier * seasonalMultiplier.doubleValue();
        }

        double maxAllowedQty = orderedQty * (1 + finalAllowancePct / 100.0);

        if (totalReceivingQty > maxAllowedQty) {
            // 초과입고 거부 -> 페널티 부과
            recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("Over delivery: receiving %d, allowed %.0f (ordered %d, allowance %.1f%%)",
                            totalReceivingQty, maxAllowedQty, orderedQty, finalAllowancePct));
            throw new BusinessException("OVER_DELIVERY",
                    String.format("Over delivery: receiving %d exceeds allowed %.0f (ordered %d, allowance %.1f%%)",
                            totalReceivingQty, maxAllowedQty, orderedQty, finalAllowancePct));
        }
    }

    private double getCategoryAllowancePct(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 30.0;
            case FRESH -> 5.0;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 3.0;
        };
    }

    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    private BigDecimal getSeasonalMultiplier() {
        return seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now())
                .map(SeasonalConfig::getMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private void recordSupplierPenalty(Supplier supplier, UUID poId,
                                        SupplierPenalty.PenaltyType penaltyType, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .poId(poId)
                .penaltyType(penaltyType)
                .description(description)
                .build();
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndCreatedAtAfter(
                supplier.getSupplierId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPos = purchaseOrderRepository
                    .findPendingOrdersBySupplierId(supplier.getSupplierId());
            for (PurchaseOrder pendingPo : pendingPos) {
                pendingPo.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(pendingPo);
            }
            log.warn("Supplier {} has {} penalties in 30 days. All pending POs are on hold.",
                    supplier.getName(), penaltyCount);
        }
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
                receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS",
                    "Receipt must be in inspecting or pending_approval status");
        }

        // 상태를 confirmed로 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(receipt);

        // 재고 반영 및 PO 업데이트
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);
        for (InboundReceiptLine line : lines) {
            // 재고 증가
            updateInventory(line);

            // 로케이션 current_qty 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // PO 라인 received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderIdAndProductId(
                            receipt.getPurchaseOrder().getPoId(),
                            line.getProduct().getProductId())
                    .orElseThrow();
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getPoId());

        return buildReceiptResponse(receipt);
    }

    private void updateInventory(InboundReceiptLine line) {
        // 동일 상품+로케이션+로트 조합이 있는지 확인
        String lotNumber = line.getLotNumber() != null ? line.getLotNumber() : "";
        var existingInventory = inventoryRepository.findByProductProductIdAndLocationLocationIdAndLotNumber(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                lotNumber);

        if (existingInventory.isPresent()) {
            // 기존 재고 수량 증가
            Inventory inventory = existingInventory.get();
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);
        } else {
            // 새 재고 레코드 생성
            Inventory inventory = Inventory.builder()
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(OffsetDateTime.now())
                    .isExpired(false)
                    .build();
            inventoryRepository.save(inventory);
        }
    }

    private void updatePurchaseOrderStatus(UUID poId) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrderId(poId);
        boolean allFulfilled = true;
        boolean anyFulfilled = false;

        for (PurchaseOrderLine line : poLines) {
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allFulfilled = false;
            }
            if (line.getReceivedQty() > 0) {
                anyFulfilled = true;
            }
        }

        PurchaseOrder po = purchaseOrderRepository.findById(poId).orElseThrow();
        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        purchaseOrderRepository.save(po);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
                receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS",
                    "Receipt must be in inspecting or pending_approval status");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        inboundReceiptRepository.save(receipt);

        return buildReceiptResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS",
                    "Receipt must be in pending_approval status");
        }

        // pending_approval -> inspecting으로 변경 (이후 confirm 가능)
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        inboundReceiptRepository.save(receipt);

        return buildReceiptResponse(receipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));
        return buildReceiptResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::buildReceiptResponse)
                .collect(Collectors.toList());
    }

    private InboundReceiptResponse buildReceiptResponse(InboundReceipt receipt) {
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receipt.getReceiptId());

        return InboundReceiptResponse.builder()
                .receiptId(receipt.getReceiptId())
                .poId(receipt.getPurchaseOrder().getPoId())
                .status(receipt.getStatus().name())
                .receivedBy(receipt.getReceivedBy())
                .receivedAt(receipt.getReceivedAt())
                .confirmedAt(receipt.getConfirmedAt())
                .lines(lines.stream()
                        .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                                .receiptLineId(line.getReceiptLineId())
                                .productId(line.getProduct().getProductId())
                                .productName(line.getProduct().getName())
                                .locationId(line.getLocation().getLocationId())
                                .locationCode(line.getLocation().getCode())
                                .quantity(line.getQuantity())
                                .lotNumber(line.getLotNumber())
                                .expiryDate(line.getExpiryDate())
                                .manufactureDate(line.getManufactureDate())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
