package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SupplierRepository supplierRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회 및 검증
        PurchaseOrder po = purchaseOrderRepository.findByIdWithLines(request.getPoId())
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase order not found"));

        // 2. 각 라인별 검증
        List<InboundReceiptLine> receiptLines = new ArrayList<>();
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

            // 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count");
            }

            // 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                validateExpiryDateRequirements(product, lineReq);
            }

            InboundReceiptLine line = InboundReceiptLine.builder()
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
            receiptLines.add(line);
        }

        // 3. 입고 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.ReceiptStatus.INSPECTING)
                .receivedBy(request.getReceivedBy())
                .receivedAt(Instant.now())
                .lines(receiptLines)
                .build();

        receiptLines.forEach(line -> line.setInboundReceipt(receipt));

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
        log.info("Inbound receipt created with ID: {}", savedReceipt.getReceiptId());

        return convertToResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.INSPECTING &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in inspecting or pending_approval status");
        }

        PurchaseOrder po = receipt.getPurchaseOrder();

        // 1. 각 라인별 초과입고 검증 및 유통기한 검증
        boolean requiresApproval = false;
        for (InboundReceiptLine line : receipt.getLines()) {
            Product product = line.getProduct();

            // 초과입고 검증
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderPoIdAndProductProductId(po.getPoId(), product.getProductId())
                    .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND", "PO line not found for product"));

            int totalReceivedQty = poLine.getReceivedQty() + line.getQuantity();
            double allowedOverdeliveryRate = calculateAllowedOverdeliveryRate(product.getCategory(), po.getPoType());
            int maxAllowedQty = (int) (poLine.getOrderedQty() * (1 + allowedOverdeliveryRate / 100.0));

            if (totalReceivedQty > maxAllowedQty) {
                // 초과입고 거부 및 페널티 부과
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        "Over delivery: " + totalReceivedQty + " > " + maxAllowedQty, po.getPoId());
                throw new BusinessException("OVER_DELIVERY",
                        String.format("Over delivery detected. Allowed: %d, Received: %d", maxAllowedQty, totalReceivedQty));
            }

            // 유통기한 잔여율 검증 (성수기로 인해 30% 미만도 입고 가능)
            if (product.getHasExpiry() && line.getExpiryDate() != null && line.getManufactureDate() != null) {
                double remainingShelfLifePct = calculateRemainingShelfLife(line.getExpiryDate(), line.getManufactureDate());

                // 성수기: 유통기한 30% 미만도 입고 허용
                if (remainingShelfLifePct >= 30 && remainingShelfLifePct < 50) {
                    requiresApproval = true;
                }
            }
        }

        // 2. 승인 필요 여부 확인
        if (requiresApproval && receipt.getStatus() == InboundReceipt.ReceiptStatus.INSPECTING) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.PENDING_APPROVAL);
            InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
            log.info("Inbound receipt {} requires approval due to shelf life warning", receiptId);
            return convertToResponse(savedReceipt);
        }

        // 3. 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
            updatePurchaseOrderLine(po.getPoId(), line.getProduct().getProductId(), line.getQuantity());
        }

        // 4. PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 5. 입고 확정
        receipt.setStatus(InboundReceipt.ReceiptStatus.CONFIRMED);
        receipt.setConfirmedAt(Instant.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("Inbound receipt {} confirmed", receiptId);
        return convertToResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.INSPECTING &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in inspecting or pending_approval status");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.REJECTED);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("Inbound receipt {} rejected. Reason: {}", receiptId, reason);
        return convertToResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in pending_approval status");
        }

        // 재고 반영
        PurchaseOrder po = receipt.getPurchaseOrder();
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
            updatePurchaseOrderLine(po.getPoId(), line.getProduct().getProductId(), line.getQuantity());
        }

        // PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 입고 확정
        receipt.setStatus(InboundReceipt.ReceiptStatus.CONFIRMED);
        receipt.setConfirmedAt(Instant.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("Inbound receipt {} approved and confirmed", receiptId);
        return convertToResponse(savedReceipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));
        return convertToResponse(receipt);
    }

    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getAllInboundReceipts(Pageable pageable) {
        return inboundReceiptRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ===== Helper Methods =====

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN은 FROZEN만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "FROZEN products can only be stored in FROZEN locations");
        }

        // COLD는 COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
            locationType != Product.StorageType.COLD && locationType != Product.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "COLD products can only be stored in COLD or FROZEN locations");
        }

        // AMBIENT는 AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "AMBIENT products can only be stored in AMBIENT locations");
        }
    }

    private void validateExpiryDateRequirements(Product product, InboundReceiptRequest.InboundReceiptLineRequest lineReq) {
        if (lineReq.getExpiryDate() == null) {
            throw new BusinessException("MISSING_EXPIRY_DATE", "Expiry date is required for products with expiry management");
        }

        if (product.getManufactureDateRequired() && lineReq.getManufactureDate() == null) {
            throw new BusinessException("MISSING_MANUFACTURE_DATE", "Manufacture date is required for this product");
        }

        if (lineReq.getExpiryDate() != null && lineReq.getManufactureDate() != null) {
            if (lineReq.getExpiryDate().isBefore(lineReq.getManufactureDate())) {
                throw new BusinessException("INVALID_EXPIRY_DATE", "Expiry date cannot be before manufacture date");
            }
        }
    }

    private double calculateAllowedOverdeliveryRate(Product.ProductCategory category, PurchaseOrder.PoType poType) {
        // 카테고리별 기본 허용률
        double baseRate = switch (category) {
            case GENERAL -> 30.0;
            case FRESH -> 5.0;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 3.0;
        };

        // HAZMAT은 항상 0%
        if (category == Product.ProductCategory.HAZMAT) {
            return 0.0;
        }

        // 발주 유형별 가중치
        double poTypeMultiplier = switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        double rateWithPoType = baseRate * poTypeMultiplier;

        // 성수기 가중치
        LocalDate today = LocalDate.now();
        SeasonalConfig seasonalConfig = seasonalConfigRepository.findActiveSeasonByDate(today).orElse(null);

        if (seasonalConfig != null) {
            double seasonalMultiplier = seasonalConfig.getMultiplier().doubleValue();
            rateWithPoType *= seasonalMultiplier;
        }

        return rateWithPoType;
    }

    private double calculateRemainingShelfLife(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType,
                                       String description, UUID poId) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .description(description)
                .poId(poId)
                .build();
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierAndCreatedAtAfter(
                supplier.getSupplierId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository
                    .findBySupplierSupplierIdAndStatus(supplier.getSupplierId(), PurchaseOrder.PoStatus.PENDING);

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.HOLD);
            }
            purchaseOrderRepository.saveAll(pendingPOs);

            // 공급업체 상태도 hold로 변경
            supplier.setStatus(Supplier.SupplierStatus.HOLD);
            supplierRepository.save(supplier);

            log.warn("Supplier {} has been put on hold due to 3+ penalties in 30 days", supplier.getSupplierId());
        }
    }

    private void updateInventory(InboundReceiptLine line) {
        Product product = line.getProduct();
        Location location = line.getLocation();

        // 동일 product + location + lot_number 조합 찾기
        Inventory inventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        product.getProductId(), location.getLocationId(), line.getLotNumber())
                .orElse(null);

        if (inventory != null) {
            // 기존 재고 증가
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);
        } else {
            // 새 재고 생성
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(location)
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            inventoryRepository.save(newInventory);
        }

        // Location의 current_qty 증가
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);
    }

    private void updatePurchaseOrderLine(UUID poId, UUID productId, int receivedQty) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderPoIdAndProductProductId(poId, productId)
                .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND", "PO line not found"));

        poLine.setReceivedQty(poLine.getReceivedQty() + receivedQty);
        purchaseOrderLineRepository.save(poLine);
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allFulfilled = true;
        boolean anyFulfilled = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allFulfilled = false;
            }
            if (line.getReceivedQty() > 0) {
                anyFulfilled = true;
            }
        }

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.COMPLETED);
        } else if (anyFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.PARTIAL);
        }

        purchaseOrderRepository.save(po);
    }

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
                .collect(Collectors.toList());

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
