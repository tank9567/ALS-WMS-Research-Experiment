package com.wms.service;

import com.wms.dto.InboundReceiptListResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new BusinessException("PURCHASE_ORDER_NOT_FOUND", "Purchase order not found"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new BusinessException("PO_ON_HOLD", "Purchase order is on hold due to supplier penalties");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .receiptNumber(request.getReceiptNumber())
                .purchaseOrder(po)
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedDate(Instant.now())
                .build();

        // 3. 각 라인에 대해 검증 및 처리
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND", "Purchase order line not found"));

            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

            // 3.1 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN",
                        "Location " + location.getCode() + " is frozen for cycle count");
            }

            // 3.2 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3.3 초과 입고 체크
            validateOverDelivery(po, poLine, lineReq.getReceivedQuantity(), product);

            // 3.4 유통기한 관리 체크
            if (product.getRequiresExpiry()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new BusinessException("EXPIRY_DATE_REQUIRED",
                            "Expiry date is required for product: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new BusinessException("MANUFACTURE_DATE_REQUIRED",
                            "Manufacture date is required for product: " + product.getSku());
                }

                // 유통기한 잔여율 체크 (성수기 여부 고려)
                LocalDate today = LocalDate.now();
                SeasonalConfig season = seasonalConfigRepository.findActiveSeasonByDate(today).orElse(null);
                boolean isPeakSeason = (season != null);

                ShelfLifeValidation validation = validateShelfLife(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        product.getMinRemainingShelfLifePct(),
                        isPeakSeason
                );

                if (validation == ShelfLifeValidation.REJECT) {
                    // 공급업체 페널티 부과
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "Shelf life remaining below threshold for product: " + product.getSku());
                    throw new BusinessException("SHELF_LIFE_INSUFFICIENT",
                            "Shelf life remaining is below minimum threshold");
                } else if (validation == ShelfLifeValidation.NEEDS_APPROVAL) {
                    receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
                }
            }

            // 3.5 로케이션 용량 체크
            if (location.getCurrentQuantity() + lineReq.getReceivedQuantity() > location.getCapacity()) {
                throw new BusinessException("LOCATION_CAPACITY_EXCEEDED",
                        "Location capacity would be exceeded");
            }

            // 3.6 입고 라인 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .receivedQuantity(lineReq.getReceivedQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .manufactureDate(lineReq.getManufactureDate())
                    .expiryDate(lineReq.getExpiryDate())
                    .build();

            receipt.getLines().add(receiptLine);
        }

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting) {
            throw new BusinessException("INVALID_STATUS", "Receipt must be in inspecting status to confirm");
        }

        // 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            // Inventory 업데이트
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber()
            ).orElse(null);

            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() + line.getReceivedQuantity());
                inventoryRepository.save(inventory);
            } else {
                inventory = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .quantity(line.getReceivedQuantity())
                        .lotNumber(line.getLotNumber())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(Instant.now())
                        .build();
                inventoryRepository.save(inventory);
            }

            // Location 현재 수량 업데이트
            Location location = line.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + line.getReceivedQuantity());
            locationRepository.save(location);

            // PO Line 입고 수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() + line.getReceivedQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // PO 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(Instant.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() == InboundReceipt.ReceiptStatus.confirmed ||
            receipt.getStatus() == InboundReceipt.ReceiptStatus.rejected) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be rejected in current status");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receipt.setRejectionReason(reason);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse approveShelfLife(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not pending approval");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));
        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptListResponse> getInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    // ========== Private Helper Methods ==========

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
            case HAZMAT -> true; // HAZMAT은 모든 로케이션에 적재 가능
        };

        if (!compatible) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Product storage type " + productType + " is not compatible with location storage type " + locationType);
        }
    }

    private void validateOverDelivery(PurchaseOrder po, PurchaseOrderLine poLine,
                                      int receivedQty, Product product) {
        int totalReceived = poLine.getReceivedQuantity() + receivedQty;
        int ordered = poLine.getOrderedQuantity();
        double overDeliveryPct = ((double) (totalReceived - ordered) / ordered) * 100;

        if (overDeliveryPct <= 0) {
            return; // 초과 아님
        }

        // 기본 허용률 계산
        double allowedPct = getCategoryAllowancePct(product.getCategory());

        // HAZMAT은 항상 0%
        if (product.getCategory() != Product.ProductCategory.HAZMAT) {
            // 발주 유형별 가중치
            double poTypeMultiplier = switch (po.getPoType()) {
                case NORMAL -> 1.0;
                case URGENT -> 2.0;
                case IMPORT -> 1.5;
            };
            allowedPct *= poTypeMultiplier;

            // 성수기 가중치
            LocalDate today = LocalDate.now();
            SeasonalConfig season = seasonalConfigRepository.findActiveSeasonByDate(today).orElse(null);
            if (season != null) {
                allowedPct *= season.getMultiplier().doubleValue();
            }
        }

        if (overDeliveryPct > allowedPct) {
            // 공급업체 페널티 부과
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("Over delivery: %.2f%% (allowed: %.2f%%)", overDeliveryPct, allowedPct));
            throw new BusinessException("OVER_DELIVERY",
                    String.format("Over delivery exceeds allowed threshold: %.2f%% > %.2f%%",
                            overDeliveryPct, allowedPct));
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

    private enum ShelfLifeValidation {
        ACCEPT, NEEDS_APPROVAL, REJECT
    }

    private ShelfLifeValidation validateShelfLife(LocalDate manufactureDate, LocalDate expiryDate,
                                                   Integer minPct, boolean isPeakSeason) {
        LocalDate today = LocalDate.now();

        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalShelfLife <= 0) {
            throw new BusinessException("INVALID_DATES", "Expiry date must be after manufacture date");
        }

        double remainingPct = ((double) remainingShelfLife / totalShelfLife) * 100;

        int threshold = (minPct != null) ? minPct : 30;

        // 성수기에는 유통기한 30% 미만도 허용 (REJECT 규칙 무시)
        if (isPeakSeason) {
            if (remainingPct < 50) {
                return ShelfLifeValidation.NEEDS_APPROVAL;
            } else {
                return ShelfLifeValidation.ACCEPT;
            }
        }

        // 비성수기 기존 로직
        if (remainingPct < threshold) {
            return ShelfLifeValidation.REJECT;
        } else if (remainingPct < 50) {
            return ShelfLifeValidation.NEEDS_APPROVAL;
        } else {
            return ShelfLifeValidation.ACCEPT;
        }
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType type, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(type)
                .reason(reason)
                .occurredAt(Instant.now())
                .build();
        supplierPenaltyRepository.save(penalty);

        // 30일 내 페널티 3회 이상이면 공급업체 hold
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndOccurredAtAfter(
                supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            supplier.setStatus(Supplier.SupplierStatus.hold);
            supplierRepository.save(supplier);

            // 해당 공급업체의 pending PO를 모두 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findPendingOrdersBySupplierId(supplier.getId());
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(po);
            }

            log.warn("Supplier {} has been put on hold due to {} penalties in last 30 days",
                    supplier.getName(), penaltyCount);
        }
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQuantity() < line.getOrderedQuantity()) {
                allCompleted = false;
            }
            if (line.getReceivedQuantity() > 0) {
                anyReceived = true;
            }
        }

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .receivedQuantity(line.getReceivedQuantity())
                        .lotNumber(line.getLotNumber())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .purchaseOrderId(receipt.getPurchaseOrder().getId())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedDate(receipt.getReceivedDate())
                .confirmedAt(receipt.getConfirmedAt())
                .rejectionReason(receipt.getRejectionReason())
                .lines(lineResponses)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }

    private InboundReceiptListResponse toListResponse(InboundReceipt receipt) {
        return InboundReceiptListResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedDate(receipt.getReceivedDate())
                .confirmedAt(receipt.getConfirmedAt())
                .createdAt(receipt.getCreatedAt())
                .build();
    }
}
