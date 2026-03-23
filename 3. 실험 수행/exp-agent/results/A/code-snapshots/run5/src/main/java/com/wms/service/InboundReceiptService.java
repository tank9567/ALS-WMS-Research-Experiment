package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
    private final SeasonalConfigRepository seasonalConfigRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new BusinessException("Purchase order is on hold");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .receiptNumber(generateReceiptNumber())
                .purchaseOrder(po)
                .receivedDate(request.getReceivedDate())
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .build();
        receipt = inboundReceiptRepository.save(receipt);

        List<InboundReceiptLineResponse> lineResponses = new ArrayList<>();
        boolean needsApproval = false;
        String rejectionReason = null;

        // 3. 각 라인 검증
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order line not found"));

            Product product = poLine.getProduct();
            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

            // 3.1 실사 동결 체크
            if (location.getIsFrozen()) {
                rejectionReason = "Location is frozen for cycle count";
                break;
            }

            // 3.2 보관유형 호환성 체크
            if (!isStorageTypeCompatible(product, location)) {
                rejectionReason = "Storage type incompatible: product=" + product.getStorageType() +
                                 ", location=" + location.getStorageType();
                break;
            }

            // 3.3 HAZMAT zone 체크 (일반 로케이션도 허용)
            // HAZMAT 상품은 이제 모든 구역에 보관 가능

            // 3.4 유통기한 필수 체크
            if (product.getRequiresExpiryManagement()) {
                if (lineReq.getExpiryDate() == null) {
                    rejectionReason = "Expiry date is required for this product";
                    break;
                }
                if (lineReq.getManufactureDate() == null) {
                    rejectionReason = "Manufacture date is required for this product";
                    break;
                }
            }

            // 3.5 초과입고 허용률 체크
            int totalReceived = poLine.getReceivedQuantity() + lineReq.getQuantity();
            double allowedRate = calculateAllowedOverReceiveRate(product.getCategory(), po.getPoType(), request.getReceivedDate());
            int maxAllowed = (int) (poLine.getOrderedQuantity() * (1 + allowedRate / 100.0));

            if (totalReceived > maxAllowed) {
                rejectionReason = String.format("Over delivery: ordered=%d, max_allowed=%d, total_received=%d",
                        poLine.getOrderedQuantity(), maxAllowed, totalReceived);

                // 공급업체 페널티
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, rejectionReason);
                break;
            }

            // 3.6 유통기한 잔여율 체크
            if (product.getRequiresExpiryManagement() && lineReq.getExpiryDate() != null && lineReq.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        request.getReceivedDate()
                );

                Integer minPct = product.getMinRemainingShelfLifePct() != null ?
                                product.getMinRemainingShelfLifePct() : 30;

                // 성수기 체크
                Optional<SeasonalConfig> seasonal = seasonalConfigRepository.findByDate(request.getReceivedDate());
                boolean isPeakSeason = seasonal.isPresent();

                // 성수기에는 유통기한 검증 스킵
                if (!isPeakSeason && remainingPct < minPct) {
                    rejectionReason = String.format("Shelf life too short: remaining=%.1f%%, required>=%d%%",
                            remainingPct, minPct);

                    // 공급업체 페널티
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE, rejectionReason);
                    break;
                } else if (remainingPct >= minPct && remainingPct <= 50) {
                    needsApproval = true;
                }
            }

            // 3.7 용량 체크
            if (location.getCurrentQuantity() + lineReq.getQuantity() > location.getCapacity()) {
                rejectionReason = "Location capacity exceeded";
                break;
            }

            // 라인 저장
            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .manufactureDate(lineReq.getManufactureDate())
                    .expiryDate(lineReq.getExpiryDate())
                    .build();
            line = inboundReceiptLineRepository.save(line);

            lineResponses.add(InboundReceiptLineResponse.builder()
                    .id(line.getId())
                    .purchaseOrderLineId(poLine.getId())
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .locationId(location.getId())
                    .locationCode(location.getCode())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .manufactureDate(line.getManufactureDate())
                    .expiryDate(line.getExpiryDate())
                    .build());
        }

        // 4. 최종 상태 결정
        if (rejectionReason != null) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
            receipt.setRejectionReason(rejectionReason);
        } else if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        receipt = inboundReceiptRepository.save(receipt);

        // 5. 공급업체 페널티 체크 (3회 이상이면 PO hold)
        if (rejectionReason != null) {
            checkAndHoldSupplierOrders(po.getSupplier());
        }

        return InboundReceiptResponse.from(receipt, lineResponses);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("Cannot confirm receipt in status: " + receipt.getStatus());
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);

        // 재고 반영
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Optional<Inventory> existingInv = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber(),
                    line.getExpiryDate()
            );

            if (existingInv.isPresent()) {
                Inventory inv = existingInv.get();
                inv.setQuantity(inv.getQuantity() + line.getQuantity());
                inventoryRepository.save(inv);
            } else {
                Inventory inv = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .lotNumber(line.getLotNumber())
                        .quantity(line.getQuantity())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(OffsetDateTime.now())
                        .build();
                inventoryRepository.save(inv);
            }

            // 로케이션 용량 증가
            Location location = line.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + line.getQuantity());
            locationRepository.save(location);

            // PO Line 수량 갱신
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getId());

        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt = inboundReceiptRepository.save(receipt);

        return getInboundReceiptById(receiptId);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("Cannot reject receipt in status: " + receipt.getStatus());
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receipt.setRejectionReason(reason);
        receipt = inboundReceiptRepository.save(receipt);

        return getInboundReceiptById(receiptId);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("Receipt is not pending approval");
        }

        // pending_approval → inspecting으로 변경 후 확정 가능 상태로
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receipt = inboundReceiptRepository.save(receipt);

        // 자동으로 확정 처리
        return confirmInboundReceipt(receiptId);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceiptById(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);

        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .purchaseOrderLineId(line.getPurchaseOrderLine().getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .quantity(line.getQuantity())
                        .lotNumber(line.getLotNumber())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.from(receipt, lineResponses);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(receipt -> getInboundReceiptById(receipt.getId()))
                .collect(Collectors.toList());
    }

    // === Helper methods ===

    private String generateReceiptNumber() {
        return "IR-" + System.currentTimeMillis();
    }

    private boolean isStorageTypeCompatible(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT은 HAZMAT끼리만
        if (productType == Product.StorageType.HAZMAT) {
            return locationType == Product.StorageType.HAZMAT;
        }

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN) {
            return locationType == Product.StorageType.FROZEN;
        }

        // COLD 상품 → COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD) {
            return locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
        }

        // AMBIENT 상품 → AMBIENT만
        if (productType == Product.StorageType.AMBIENT) {
            return locationType == Product.StorageType.AMBIENT;
        }

        return false;
    }

    private double calculateAllowedOverReceiveRate(
            Product.ProductCategory category,
            PurchaseOrder.PoType poType,
            LocalDate receivedDate) {

        // 기본 허용률
        double baseRate;
        switch (category) {
            case GENERAL:
                baseRate = 30.0;
                break;
            case FRESH:
                baseRate = 5.0;
                break;
            case HAZMAT:
                return 0.0; // HAZMAT은 항상 0%
            case HIGH_VALUE:
                baseRate = 3.0;
                break;
            default:
                baseRate = 10.0;
        }

        // PO 유형별 가중치
        double poMultiplier = 1.0;
        switch (poType) {
            case NORMAL:
                poMultiplier = 1.0;
                break;
            case URGENT:
                poMultiplier = 2.0;
                break;
            case IMPORT:
                poMultiplier = 1.5;
                break;
        }

        // 성수기 가중치
        double seasonalMultiplier = 1.0;
        Optional<SeasonalConfig> seasonal = seasonalConfigRepository.findByDate(receivedDate);
        if (seasonal.isPresent()) {
            seasonalMultiplier = seasonal.get().getMultiplier().doubleValue();
        }

        // HAZMAT은 어떤 경우에도 0%
        if (category == Product.ProductCategory.HAZMAT) {
            return 0.0;
        }

        return baseRate * poMultiplier * seasonalMultiplier;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .reason(reason)
                .occurredAt(OffsetDateTime.now())
                .build();
        supplierPenaltyRepository.save(penalty);
    }

    private void checkAndHoldSupplierOrders(Supplier supplier) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // PO를 hold로 변경
            purchaseOrderRepository.holdPendingOrdersBySupplierId(supplier.getId());

            // Supplier 상태도 hold로
            supplier.setStatus(Supplier.SupplierStatus.hold);
            supplierRepository.save(supplier);
        }
    }

    private void updatePurchaseOrderStatus(UUID purchaseOrderId) {
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrderId);

        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : lines) {
            if (line.getReceivedQuantity() > 0) {
                anyReceived = true;
            }
            if (line.getReceivedQuantity() < line.getOrderedQuantity()) {
                allCompleted = false;
            }
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }
}
