package com.wms.inbound.service;

import com.wms.inbound.dto.*;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
    private final SeasonalConfigRepository seasonalConfigRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(CreateInboundReceiptRequest request) {
        // 1. 발주서 확인
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(request.getPoId())
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        if (purchaseOrder.getStatus() == PurchaseOrderStatus.cancelled) {
            throw new IllegalArgumentException("Cannot receive from cancelled purchase order");
        }

        // 2. 입고 접수 생성
        InboundReceipt receipt = InboundReceipt.builder()
            .purchaseOrder(purchaseOrder)
            .status(InboundReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .build();

        inboundReceiptRepository.save(receipt);

        // 3. 각 라인 검증 및 생성
        boolean needsApproval = false;

        for (InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found: " + lineReq.getLocationId()));

            // 3.1 기본 검증
            validateBasicConstraints(product, location, lineReq, purchaseOrder);

            // 3.2 유통기한 검증 (성수기 고려)
            if (product.getHasExpiry()) {
                ExpiryValidationResult expiryResult = validateExpiryDate(
                    product,
                    lineReq.getExpiryDate(),
                    lineReq.getManufactureDate()
                );

                if (expiryResult == ExpiryValidationResult.NEEDS_APPROVAL) {
                    needsApproval = true;
                }
            }

            // 3.3 입고 라인 생성
            InboundReceiptLine line = InboundReceiptLine.builder()
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineReq.getQuantity())
                .lotNumber(lineReq.getLotNumber())
                .expiryDate(lineReq.getExpiryDate())
                .manufactureDate(lineReq.getManufactureDate())
                .build();

            inboundReceiptLineRepository.save(line);
        }

        // 4. 승인 필요 여부에 따라 상태 설정
        if (needsApproval) {
            receipt.setStatus(InboundReceiptStatus.pending_approval);
            inboundReceiptRepository.save(receipt);
        }

        return buildResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (receipt.getStatus() != InboundReceiptStatus.inspecting
            && receipt.getStatus() != InboundReceiptStatus.pending_approval) {
            throw new IllegalArgumentException("Receipt cannot be confirmed in current status: " + receipt.getStatus());
        }

        // 재고 반영
        receipt.getLines().forEach(line -> {
            // 재고 생성 또는 증가
            Inventory inventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(
                    line.getProduct().getProductId(),
                    line.getLocation().getLocationId(),
                    line.getLotNumber()
                )
                .orElseGet(() -> Inventory.builder()
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(0)
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(ZonedDateTime.now())
                    .isExpired(false)
                    .build());

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);

            // 로케이션 수량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 발주 라인 수령 수량 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPoIdAndProductId(
                    receipt.getPurchaseOrder().getPoId(),
                    line.getProduct().getProductId()
                )
                .orElseThrow(() -> new IllegalArgumentException("PO line not found"));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        });

        // 발주서 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        // 입고 확정
        receipt.setStatus(InboundReceiptStatus.confirmed);
        receipt.setConfirmedAt(ZonedDateTime.now());
        inboundReceiptRepository.save(receipt);

        return buildResponse(receipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        return buildResponse(receipt);
    }

    private void validateBasicConstraints(Product product, Location location,
                                         InboundReceiptLineRequest lineReq,
                                         PurchaseOrder purchaseOrder) {
        // 수량 검증
        if (lineReq.getQuantity() == null || lineReq.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        // 실사 동결 체크
        if (location.getIsFrozen()) {
            throw new IllegalArgumentException("Cannot receive to frozen location: " + location.getCode());
        }

        // 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, location);

        // HAZMAT zone 체크
        if (product.getCategory() == ProductCategory.HAZMAT && location.getZone() != LocationZone.HAZMAT) {
            throw new IllegalArgumentException("HAZMAT products must be received to HAZMAT zone");
        }

        // 로케이션 용량 체크
        if (location.getCurrentQty() + lineReq.getQuantity() > location.getCapacity()) {
            throw new IllegalArgumentException("Location capacity exceeded: " + location.getCode());
        }

        // 유통기한 필수 체크
        if (product.getHasExpiry()) {
            if (lineReq.getExpiryDate() == null || lineReq.getManufactureDate() == null) {
                throw new IllegalArgumentException("Expiry date and manufacture date are required for product: " + product.getSku());
            }
        }

        // 초과 입고 체크
        validateOverDelivery(product, lineReq, purchaseOrder);
    }

    private void validateStorageTypeCompatibility(Product product, Location location) {
        StorageType productType = product.getStorageType();
        StorageType locationType = location.getStorageType();

        if (productType == StorageType.FROZEN && locationType != StorageType.FROZEN) {
            throw new IllegalArgumentException("FROZEN product requires FROZEN location");
        }

        if (productType == StorageType.COLD &&
            locationType != StorageType.COLD &&
            locationType != StorageType.FROZEN) {
            throw new IllegalArgumentException("COLD product requires COLD or FROZEN location");
        }

        if (productType == StorageType.AMBIENT && locationType != StorageType.AMBIENT) {
            throw new IllegalArgumentException("AMBIENT product requires AMBIENT location");
        }
    }

    private enum ExpiryValidationResult {
        OK,
        NEEDS_APPROVAL,
        REJECTED
    }

    /**
     * 유통기한 검증 (성수기 고려)
     * - 성수기가 아닌 경우: 30% 미만 거부, 30~50% 승인 필요, 50% 이상 정상
     * - 성수기인 경우: 유통기한 제약 완화 (30% 미만도 허용)
     */
    private ExpiryValidationResult validateExpiryDate(Product product, LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();

        // 만료일 지남
        if (expiryDate.isBefore(today)) {
            throw new IllegalArgumentException("Cannot receive expired product");
        }

        if (manufactureDate == null) {
            throw new IllegalArgumentException("Manufacture date is required for expiry check");
        }

        // 잔여 유통기한 계산
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
        double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

        // 최소 잔여 유통기한 기준
        int minPct = product.getMinRemainingShelfLifePct() != null
            ? product.getMinRemainingShelfLifePct()
            : 30;

        // 성수기 확인
        boolean isPeakSeason = isCurrentlyPeakSeason();

        // 성수기인 경우 유통기한 제약 완화
        if (isPeakSeason) {
            // 성수기에는 30% 미만도 허용 (단, 만료되지 않은 경우)
            if (remainingPct >= 10) {
                return ExpiryValidationResult.OK;
            } else if (remainingPct < 10) {
                throw new IllegalArgumentException(
                    String.format("Remaining shelf life %.1f%% is too low (minimum 10%% even in peak season)", remainingPct)
                );
            }
        }

        // 일반 시즌
        if (remainingPct < minPct) {
            throw new IllegalArgumentException(
                String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, minPct)
            );
        } else if (remainingPct >= minPct && remainingPct < 50) {
            return ExpiryValidationResult.NEEDS_APPROVAL;
        }

        return ExpiryValidationResult.OK;
    }

    /**
     * 현재 성수기 여부 확인
     */
    private boolean isCurrentlyPeakSeason() {
        LocalDate today = LocalDate.now();

        return seasonalConfigRepository.findAll().stream()
            .filter(SeasonalConfig::getIsActive)
            .anyMatch(season ->
                !today.isBefore(season.getStartDate()) &&
                !today.isAfter(season.getEndDate())
            );
    }

    private void validateOverDelivery(Product product, InboundReceiptLineRequest lineReq, PurchaseOrder purchaseOrder) {
        // 발주 라인 확인
        PurchaseOrderLine poLine = purchaseOrderLineRepository
            .findByPoIdAndProductId(purchaseOrder.getPoId(), product.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("Product not in purchase order: " + product.getSku()));

        int remainingQty = poLine.getOrderedQty() - poLine.getReceivedQty();
        int overQty = lineReq.getQuantity() - remainingQty;

        if (overQty <= 0) {
            return; // 초과 없음
        }

        // 초과 허용률 계산
        double overPct = (double) overQty / poLine.getOrderedQty() * 100;
        double allowedOverPct = calculateAllowedOverDeliveryPct(product, purchaseOrder);

        if (overPct > allowedOverPct) {
            throw new IllegalArgumentException(
                String.format("Over delivery %.1f%% exceeds allowed %.1f%%", overPct, allowedOverPct)
            );
        }
    }

    private double calculateAllowedOverDeliveryPct(Product product, PurchaseOrder purchaseOrder) {
        // 카테고리별 기본 허용률
        double basePct;
        switch (product.getCategory()) {
            case HAZMAT:
                return 0.0; // HAZMAT은 항상 0%
            case FRESH:
                basePct = 5.0;
                break;
            case HIGH_VALUE:
                basePct = 3.0;
                break;
            default:
                basePct = 10.0;
        }

        // 발주 유형별 가중치
        double typeMultiplier;
        switch (purchaseOrder.getPoType()) {
            case URGENT:
                typeMultiplier = 2.0;
                break;
            case IMPORT:
                typeMultiplier = 1.5;
                break;
            default:
                typeMultiplier = 1.0;
        }

        // 성수기 가중치
        double seasonMultiplier = 1.0;
        LocalDate today = LocalDate.now();

        seasonalConfigRepository.findAll().stream()
            .filter(SeasonalConfig::getIsActive)
            .filter(season -> !today.isBefore(season.getStartDate()) && !today.isAfter(season.getEndDate()))
            .findFirst()
            .ifPresent(season -> {
                // seasonMultiplier 설정은 로컬 변수로는 불가능하므로 별도 처리 필요
            });

        return basePct * typeMultiplier * seasonMultiplier;
    }

    private void updatePurchaseOrderStatus(PurchaseOrder purchaseOrder) {
        boolean allCompleted = purchaseOrderLineRepository.findByPoId(purchaseOrder.getPoId()).stream()
            .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());

        boolean anyReceived = purchaseOrderLineRepository.findByPoId(purchaseOrder.getPoId()).stream()
            .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            purchaseOrder.setStatus(PurchaseOrderStatus.completed);
        } else if (anyReceived) {
            purchaseOrder.setStatus(PurchaseOrderStatus.partial);
        }

        purchaseOrderRepository.save(purchaseOrder);
    }

    private InboundReceiptResponse buildResponse(InboundReceipt receipt) {
        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPurchaseOrder().getPoId())
            .poNumber(receipt.getPurchaseOrder().getPoNumber())
            .status(receipt.getStatus())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .build();
    }
}
