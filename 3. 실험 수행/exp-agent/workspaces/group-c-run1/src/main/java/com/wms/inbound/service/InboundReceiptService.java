package com.wms.inbound.service;

import com.wms.inbound.dto.InboundReceiptCreateRequest;
import com.wms.inbound.dto.InboundReceiptResponse;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * 입고 등록 (검수 상태)
     */
    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptCreateRequest request) {
        // 1. PO 검증
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
            .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다"));

        // 2. 입고 Receipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
            .receiptId(UUID.randomUUID())
            .purchaseOrder(po)
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .receivedAt(Instant.now())
            .build();

        InboundReceipt.ReceiptStatus finalStatus = InboundReceipt.ReceiptStatus.inspecting;

        // 3. 각 품목별 검증 및 입고 라인 생성
        for (InboundReceiptCreateRequest.InboundReceiptLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineRequest.getProductId()));

            Location location = locationRepository.findById(lineRequest.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineRequest.getLocationId()));

            // 3-1. 실사 동결 체크
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("실사 동결된 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineRequest.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (lineRequest.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3-4. 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                    lineRequest.getManufactureDate(),
                    lineRequest.getExpiryDate(),
                    LocalDate.now()
                );

                int minPct = product.getMinRemainingShelfLifePct() != null ?
                    product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minPct) {
                    // 잔여율 < 30% → 입고 거부 + 페널티
                    recordSupplierPenalty(po, SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                        String.format("유통기한 부족: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, minPct));
                    throw new IllegalStateException(
                        String.format("유통기한 잔여율 부족: %s (%.1f%% < %d%%)",
                            product.getSku(), remainingPct, minPct));
                } else if (remainingPct >= minPct && remainingPct <= 50) {
                    // 잔여율 30~50% → 승인 대기
                    finalStatus = InboundReceipt.ReceiptStatus.pending_approval;
                }
            }

            // 3-5. 초과입고 검증
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoAndProduct(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "발주서에 해당 상품이 없습니다: " + product.getSku()));

            validateOverReceive(po, product, poLine, lineRequest.getQuantity());

            // 3-6. 입고 라인 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                .receiptLineId(UUID.randomUUID())
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineRequest.getQuantity())
                .lotNumber(lineRequest.getLotNumber())
                .expiryDate(lineRequest.getExpiryDate())
                .manufactureDate(lineRequest.getManufactureDate())
                .build();

            inboundReceiptLineRepository.save(receiptLine);
        }

        // 4. 최종 상태 설정 및 저장
        receipt.setStatus(finalStatus);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 확정
     */
    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중 또는 승인 대기 상태에서만 확정할 수 있습니다");
        }

        // 1. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
            ).orElse(null);

            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            } else {
                inventory = Inventory.builder()
                    .inventoryId(UUID.randomUUID())
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            }
            inventoryRepository.save(inventory);

            // 로케이션 적재량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // PO 라인 received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoAndProduct(
                receipt.getPurchaseOrder().getPoId(),
                line.getProduct().getProductId()
            ).orElseThrow();

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 2. PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getPoId());

        // 3. 입고 전표 상태 갱신
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(Instant.now());
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중 또는 승인 대기 상태에서만 거부할 수 있습니다");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 유통기한 경고 승인
     */
    @Transactional
    public InboundReceiptResponse approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태에서만 승인할 수 있습니다");
        }

        // 승인 시 검수 중 상태로 변경 (이후 confirm으로 확정)
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 상세 조회
     */
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        return mapToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    public Page<InboundReceiptResponse> getInboundReceipts(Pageable pageable) {
        return inboundReceiptRepository.findAll(pageable)
            .map(this::mapToResponse);
    }

    // ===== 내부 유틸리티 메서드 =====

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                    "위험물은 HAZMAT zone 로케이션에만 입고할 수 있습니다");
            }
        }

        if (productType == Product.StorageType.FROZEN) {
            if (locationType != Product.StorageType.FROZEN) {
                throw new IllegalStateException(
                    "FROZEN 상품은 FROZEN 로케이션에만 입고할 수 있습니다");
            }
        } else if (productType == Product.StorageType.COLD) {
            if (locationType != Product.StorageType.COLD &&
                locationType != Product.StorageType.FROZEN) {
                throw new IllegalStateException(
                    "COLD 상품은 COLD 또는 FROZEN 로케이션에만 입고할 수 있습니다");
            }
        } else if (productType == Product.StorageType.AMBIENT) {
            if (locationType != Product.StorageType.AMBIENT) {
                throw new IllegalStateException(
                    "AMBIENT 상품은 AMBIENT 로케이션에만 입고할 수 있습니다");
            }
        }
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate,
                                                   LocalDate expiryDate,
                                                   LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 초과입고 검증
     */
    private void validateOverReceive(PurchaseOrder po, Product product,
                                      PurchaseOrderLine poLine, int incomingQty) {
        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalReceiving = receivedQty + incomingQty;

        // 카테고리별 기본 허용률
        double baseTolerance = getCategoryTolerance(product.getCategory());

        // HAZMAT은 무조건 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            baseTolerance = 0.0;
        } else {
            // PO 유형별 가중치
            double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());
            baseTolerance *= poTypeMultiplier;

            // 성수기 가중치
            BigDecimal seasonMultiplier = getSeasonalMultiplier(LocalDate.now());
            baseTolerance *= seasonMultiplier.doubleValue();
        }

        int maxAllowed = (int) Math.floor(orderedQty * (1.0 + baseTolerance));

        if (totalReceiving > maxAllowed) {
            // 초과입고 → 페널티 기록 후 거부
            recordSupplierPenalty(po, SupplierPenalty.PenaltyType.OVER_DELIVERY,
                String.format("초과입고: %s (입고 %d > 허용 %d)",
                    product.getSku(), totalReceiving, maxAllowed));

            throw new IllegalStateException(
                String.format("초과입고 거부: %s (입고 %d > 허용 %d, 허용률 %.1f%%)",
                    product.getSku(), totalReceiving, maxAllowed, baseTolerance * 100));
        }
    }

    /**
     * 카테고리별 초과입고 허용률
     */
    private double getCategoryTolerance(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 0.10;  // 10%
            case FRESH -> 0.05;    // 5%
            case HAZMAT -> 0.0;    // 0%
            case HIGH_VALUE -> 0.03; // 3%
        };
    }

    /**
     * PO 유형별 가중치
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    /**
     * 성수기 가중치 조회
     */
    private BigDecimal getSeasonalMultiplier(LocalDate date) {
        return seasonalConfigRepository.findActiveSeason(date)
            .map(SeasonalConfig::getMultiplier)
            .orElse(BigDecimal.ONE);
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(PurchaseOrder po, SupplierPenalty.PenaltyType type, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
            .penaltyId(UUID.randomUUID())
            .supplier(po.getSupplier())
            .penaltyType(type)
            .description(description)
            .poId(po.getPoId())
            .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 체크
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId30Days(
            po.getSupplier().getSupplierId(), since);

        if (penaltyCount >= 3) {
            // pending PO를 hold로 변경
            purchaseOrderRepository.holdPendingOrdersBySupplier(po.getSupplier().getSupplierId());
        }
    }

    /**
     * PO 상태 갱신 (모든 라인 완납 여부 체크)
     */
    private void updatePurchaseOrderStatus(UUID poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId).orElseThrow();
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPoId(poId);

        boolean allFulfilled = lines.stream()
            .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream()
            .anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity → Response 매핑
     */
    private InboundReceiptResponse mapToResponse(InboundReceipt receipt) {
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receipt.getReceiptId());

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPurchaseOrder().getPoId())
            .poNumber(receipt.getPurchaseOrder().getPoNumber())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .lines(lines.stream().map(this::mapLineToResponse).collect(Collectors.toList()))
            .build();
    }

    private InboundReceiptResponse.InboundReceiptLineResponse mapLineToResponse(InboundReceiptLine line) {
        return InboundReceiptResponse.InboundReceiptLineResponse.builder()
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
            .build();
    }
}
