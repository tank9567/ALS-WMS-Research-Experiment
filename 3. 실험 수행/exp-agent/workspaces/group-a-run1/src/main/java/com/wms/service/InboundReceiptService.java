package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.entity.InboundReceipt.ReceiptStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.PurchaseOrder.PoStatus;
import com.wms.entity.PurchaseOrder.PoType;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (검수 시작)
     */
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. 입고번호 중복 체크
        if (inboundReceiptRepository.existsByReceiptNumber(request.getReceiptNumber())) {
            throw new BusinessException("Receipt number already exists", "DUPLICATE_RECEIPT_NUMBER");
        }

        // 2. PO 조회 및 검증
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new BusinessException("Purchase order not found", "PO_NOT_FOUND"));

        if (purchaseOrder.getStatus() == PoStatus.HOLD) {
            throw new BusinessException("Purchase order is on hold", "PO_ON_HOLD");
        }

        if (purchaseOrder.getStatus() == PoStatus.CANCELLED) {
            throw new BusinessException("Purchase order is cancelled", "PO_CANCELLED");
        }

        if (purchaseOrder.getStatus() == PoStatus.COMPLETED) {
            throw new BusinessException("Purchase order is already completed", "PO_COMPLETED");
        }

        // 3. PO Lines 조회
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrder.getId());

        // 4. InboundReceipt 생성
        InboundReceipt inboundReceipt = InboundReceipt.builder()
                .receiptNumber(request.getReceiptNumber())
                .purchaseOrder(purchaseOrder)
                .status(ReceiptStatus.INSPECTING)
                .receivedDate(request.getReceivedDate() != null ? request.getReceivedDate() : OffsetDateTime.now())
                .build();

        inboundReceipt = inboundReceiptRepository.save(inboundReceipt);

        // 5. 각 라인 검증 및 생성
        List<InboundReceiptLine> receiptLines = new ArrayList<>();
        boolean needsApproval = false;
        String rejectionReason = null;

        for (InboundReceiptRequest.InboundReceiptLineRequest lineRequest : request.getLines()) {
            // PO Line 조회
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineRequest.getPurchaseOrderLineId())
                    .orElseThrow(() -> new BusinessException("Purchase order line not found", "PO_LINE_NOT_FOUND"));

            if (!poLine.getPurchaseOrder().getId().equals(purchaseOrder.getId())) {
                throw new BusinessException("Purchase order line does not belong to the specified PO", "PO_LINE_MISMATCH");
            }

            // Product 조회
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            if (!product.getId().equals(poLine.getProduct().getId())) {
                throw new BusinessException("Product does not match PO line", "PRODUCT_MISMATCH");
            }

            // Location 조회
            Location location = locationRepository.findById(lineRequest.getLocationId())
                    .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

            // (a) 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("Location is frozen for cycle count", "LOCATION_FROZEN");
            }

            // (b) 초과입고 허용률 체크
            int receivedQty = lineRequest.getReceivedQty();
            int orderedQty = poLine.getOrderedQty();
            int alreadyReceivedQty = poLine.getReceivedQty();
            int totalReceivedQty = alreadyReceivedQty + receivedQty;

            double allowedOverReceiptPct = calculateAllowedOverReceiptPercentage(
                    product.getCategory(),
                    purchaseOrder.getPoType(),
                    LocalDate.now()
            );

            int maxAllowedQty = (int) Math.floor(orderedQty * (1.0 + allowedOverReceiptPct / 100.0));

            if (totalReceivedQty > maxAllowedQty) {
                // 초과입고 거부
                rejectionReason = String.format(
                        "Over-delivery: received %d, ordered %d, max allowed %d (%.2f%%)",
                        totalReceivedQty, orderedQty, maxAllowedQty, allowedOverReceiptPct
                );

                // 공급업체 페널티 기록
                recordSupplierPenalty(purchaseOrder.getSupplier(), "OVER_DELIVERY", rejectionReason);

                inboundReceipt.setStatus(ReceiptStatus.REJECTED);
                inboundReceipt.setRejectionReason(rejectionReason);
                inboundReceiptRepository.save(inboundReceipt);

                throw new BusinessException(rejectionReason, "OVER_DELIVERY");
            }

            // (c) 유통기한 관리 체크
            if (product.getManagesExpiry()) {
                if (lineRequest.getExpiryDate() == null) {
                    throw new BusinessException("Expiry date is required for expiry-managed product", "EXPIRY_DATE_REQUIRED");
                }

                if (lineRequest.getManufactureDate() == null) {
                    throw new BusinessException("Manufacture date is required for expiry-managed product", "MANUFACTURE_DATE_REQUIRED");
                }

                // 유통기한 잔여율 체크
                LocalDate today = LocalDate.now();
                LocalDate manufactureDate = lineRequest.getManufactureDate();
                LocalDate expiryDate = lineRequest.getExpiryDate();

                long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
                long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

                if (totalShelfLife <= 0) {
                    throw new BusinessException("Invalid shelf life: expiry date must be after manufacture date", "INVALID_SHELF_LIFE");
                }

                double remainingPct = (remainingShelfLife * 100.0) / totalShelfLife;
                int minRemainingPct = product.getMinRemainingShelfLifePct() != null ?
                        product.getMinRemainingShelfLifePct() : 30;

                // 성수기 체크
                boolean isPeakSeason = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now()).isPresent();

                if (!isPeakSeason && remainingPct < minRemainingPct) {
                    // 비성수기: 유통기한 부족 시 거부
                    rejectionReason = String.format(
                            "Insufficient shelf life: %.1f%% remaining (min required: %d%%)",
                            remainingPct, minRemainingPct
                    );

                    // 공급업체 페널티 기록
                    recordSupplierPenalty(purchaseOrder.getSupplier(), "SHORT_SHELF_LIFE", rejectionReason);

                    inboundReceipt.setStatus(ReceiptStatus.REJECTED);
                    inboundReceipt.setRejectionReason(rejectionReason);
                    inboundReceiptRepository.save(inboundReceipt);

                    throw new BusinessException(rejectionReason, "SHORT_SHELF_LIFE");
                } else if (!isPeakSeason && remainingPct >= minRemainingPct && remainingPct < 50) {
                    // 비성수기: 30~50%는 승인 필요
                    needsApproval = true;
                }
                // 성수기: 유통기한 체크 없이 모두 입고 허용
            }

            // (d) 보관 유형 호환성 체크
            if (!isStorageTypeCompatible(product.getStorageType(), location.getStorageType(), location.getZone())) {
                throw new BusinessException(
                        String.format("Storage type incompatible: product %s, location %s",
                                product.getStorageType(), location.getStorageType()),
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }

            // (e) 로케이션 용량 체크
            int newQty = location.getCurrentQty() + receivedQty;
            if (newQty > location.getCapacity()) {
                throw new BusinessException(
                        String.format("Location capacity exceeded: current %d + received %d > capacity %d",
                                location.getCurrentQty(), receivedQty, location.getCapacity()),
                        "LOCATION_CAPACITY_EXCEEDED"
                );
            }

            // InboundReceiptLine 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                    .inboundReceipt(inboundReceipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .lotNumber(lineRequest.getLotNumber())
                    .receivedQty(receivedQty)
                    .manufactureDate(lineRequest.getManufactureDate())
                    .expiryDate(lineRequest.getExpiryDate())
                    .build();

            receiptLines.add(receiptLine);
        }

        // 승인 필요 여부에 따라 상태 변경
        if (needsApproval) {
            inboundReceipt.setStatus(ReceiptStatus.PENDING_APPROVAL);
        }

        inboundReceiptLineRepository.saveAll(receiptLines);
        inboundReceiptRepository.save(inboundReceipt);

        return buildResponse(inboundReceipt, receiptLines);
    }

    /**
     * 입고 확정
     */
    public InboundReceiptResponse confirmInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() != ReceiptStatus.INSPECTING) {
            throw new BusinessException(
                    "Can only confirm receipts in INSPECTING status",
                    "INVALID_STATUS"
            );
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        // 재고 반영 및 PO 업데이트
        processConfirmation(inboundReceipt, lines);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 거부
     */
    public InboundReceiptResponse rejectInboundReceipt(UUID id, String reason) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() == ReceiptStatus.CONFIRMED) {
            throw new BusinessException("Cannot reject confirmed receipt", "ALREADY_CONFIRMED");
        }

        if (inboundReceipt.getStatus() == ReceiptStatus.REJECTED) {
            throw new BusinessException("Receipt is already rejected", "ALREADY_REJECTED");
        }

        inboundReceipt.setStatus(ReceiptStatus.REJECTED);
        inboundReceipt.setRejectionReason(reason);
        inboundReceiptRepository.save(inboundReceipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 유통기한 경고 승인
     */
    public InboundReceiptResponse approveInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() != ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only approve receipts in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        // 재고 반영 및 PO 업데이트
        processConfirmation(inboundReceipt, lines);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getInboundReceipts(Pageable pageable) {
        Page<InboundReceipt> receipts = inboundReceiptRepository.findAll(pageable);

        return receipts.map(receipt -> {
            List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(receipt.getId());
            return buildResponse(receipt, lines);
        });
    }

    // ===== Private Helper Methods =====

    /**
     * 초과입고 허용률 계산
     */
    private double calculateAllowedOverReceiptPercentage(
            ProductCategory category,
            PoType poType,
            LocalDate date
    ) {
        // HAZMAT은 무조건 0%
        if (category == ProductCategory.HAZMAT) {
            return 0.0;
        }

        // 카테고리별 기본 허용률
        double basePct = switch (category) {
            case GENERAL -> 30.0;
            case FRESH -> 5.0;
            case HIGH_VALUE -> 3.0;
            default -> 30.0;
        };

        // 발주 유형별 가중치
        double poTypeMultiplier = switch (poType) {
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
            default -> 1.0;
        };

        // 성수기 가중치
        double seasonalMultiplier = seasonalConfigRepository.findActiveSeasonByDate(date)
                .map(config -> config.getMultiplier().doubleValue())
                .orElse(1.0);

        return basePct * poTypeMultiplier * seasonalMultiplier;
    }

    /**
     * 보관 유형 호환성 체크
     */
    private boolean isStorageTypeCompatible(
            StorageType productType,
            StorageType locationType,
            Location.Zone locationZone
    ) {
        // FROZEN 상품은 FROZEN 로케이션만
        if (productType == StorageType.FROZEN) {
            return locationType == StorageType.FROZEN;
        }

        // COLD 상품은 COLD 또는 FROZEN 허용 (상위 호환)
        if (productType == StorageType.COLD) {
            return locationType == StorageType.COLD || locationType == StorageType.FROZEN;
        }

        // AMBIENT 상품은 AMBIENT만
        if (productType == StorageType.AMBIENT) {
            return locationType == StorageType.AMBIENT;
        }

        return false;
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(Supplier supplier, String penaltyType, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .reason(reason)
                .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 개수 확인
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 공급업체 상태를 HOLD로 변경
            supplier.setStatus("hold");
            supplierRepository.save(supplier);

            // 해당 공급업체의 모든 PENDING PO를 HOLD로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findAll().stream()
                    .filter(po -> po.getSupplier().getId().equals(supplier.getId()))
                    .filter(po -> po.getStatus() == PoStatus.PENDING)
                    .collect(Collectors.toList());

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PoStatus.HOLD);
            }

            purchaseOrderRepository.saveAll(pendingPOs);
        }
    }

    /**
     * 입고 확정 처리 (재고 반영 + PO 업데이트)
     */
    private void processConfirmation(InboundReceipt inboundReceipt, List<InboundReceiptLine> lines) {
        PurchaseOrder purchaseOrder = inboundReceipt.getPurchaseOrder();

        for (InboundReceiptLine line : lines) {
            // 1. Inventory 반영
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber(),
                    line.getExpiryDate()
            ).orElse(null);

            if (inventory == null) {
                inventory = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .lotNumber(line.getLotNumber())
                        .quantity(line.getReceivedQty())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(inboundReceipt.getReceivedDate())
                        .isExpired(false)
                        .build();
            } else {
                inventory.setQuantity(inventory.getQuantity() + line.getReceivedQty());
            }

            inventoryRepository.save(inventory);

            // 2. Location 적재량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getReceivedQty());
            locationRepository.save(location);

            // 3. PO Line 입고수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getReceivedQty());
            purchaseOrderLineRepository.save(poLine);
        }

        // 4. PO 상태 업데이트
        List<PurchaseOrderLine> allPoLines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrder.getId());
        boolean allCompleted = allPoLines.stream()
                .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = allPoLines.stream()
                .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            purchaseOrder.setStatus(PoStatus.COMPLETED);
        } else if (anyReceived) {
            purchaseOrder.setStatus(PoStatus.PARTIAL);
        }

        purchaseOrderRepository.save(purchaseOrder);

        // 5. InboundReceipt 상태 업데이트
        inboundReceipt.setStatus(ReceiptStatus.CONFIRMED);
        inboundReceipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(inboundReceipt);
    }

    /**
     * Response 빌드
     */
    private InboundReceiptResponse buildResponse(InboundReceipt inboundReceipt, List<InboundReceiptLine> lines) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .purchaseOrderLineId(line.getPurchaseOrderLine().getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .lotNumber(line.getLotNumber())
                        .receivedQty(line.getReceivedQty())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .id(inboundReceipt.getId())
                .receiptNumber(inboundReceipt.getReceiptNumber())
                .purchaseOrderId(inboundReceipt.getPurchaseOrder().getId())
                .poNumber(inboundReceipt.getPurchaseOrder().getPoNumber())
                .status(inboundReceipt.getStatus())
                .receivedDate(inboundReceipt.getReceivedDate())
                .confirmedAt(inboundReceipt.getConfirmedAt())
                .rejectionReason(inboundReceipt.getRejectionReason())
                .lines(lineResponses)
                .createdAt(inboundReceipt.getCreatedAt())
                .updatedAt(inboundReceipt.getUpdatedAt())
                .build();
    }
}
