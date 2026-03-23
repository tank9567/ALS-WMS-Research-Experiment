package com.wms.service;

import com.wms.dto.ShipmentLineRequest;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 출고 지시서 생성
     * ALS-WMS-OUT-002: HAZMAT+FRESH 분리 출고
     */
    @Transactional
    public ShipmentOrder createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 상품 정보 조회 및 HAZMAT+FRESH 분리 체크
        Map<UUID, Product> productMap = new HashMap<>();
        boolean hasHazmat = false;
        boolean hasFresh = false;

        for (ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));
            productMap.put(product.getProductId(), product);

            if (product.getCategory() == Product.Category.HAZMAT) {
                hasHazmat = true;
            }
            if (product.getCategory() == Product.Category.FRESH) {
                hasFresh = true;
            }
        }

        // 2. HAZMAT+FRESH 공존 시 분리 출고
        if (hasHazmat && hasFresh) {
            return createSeparatedShipments(request, productMap);
        }

        // 3. 일반 출고 지시서 생성
        ShipmentOrder shipment = ShipmentOrder.builder()
                .customerName(request.getCustomerName())
                .createdBy(request.getCreatedBy())
                .status(ShipmentOrder.Status.pending)
                .build();
        shipmentOrderRepository.save(shipment);

        // 4. 출고 라인 생성
        for (ShipmentLineRequest lineReq : request.getLines()) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipment)
                    .product(productMap.get(lineReq.getProductId()))
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.Status.pending)
                    .build();
            shipmentOrderLineRepository.save(line);
        }

        return shipment;
    }

    /**
     * HAZMAT+FRESH 분리 출고 처리
     * ALS-WMS-OUT-002 Constraint: 동일 출고 지시서에 HAZMAT + FRESH 상품이 공존하면 분리 출고
     */
    private ShipmentOrder createSeparatedShipments(ShipmentOrderRequest request, Map<UUID, Product> productMap) {
        // 1. 비-HAZMAT 출고 지시서 생성 (원본)
        ShipmentOrder mainShipment = ShipmentOrder.builder()
                .customerName(request.getCustomerName())
                .createdBy(request.getCreatedBy())
                .status(ShipmentOrder.Status.pending)
                .build();
        shipmentOrderRepository.save(mainShipment);

        // 2. HAZMAT 전용 출고 지시서 생성
        ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                .customerName(request.getCustomerName() + " (HAZMAT)")
                .createdBy(request.getCreatedBy())
                .status(ShipmentOrder.Status.pending)
                .build();
        shipmentOrderRepository.save(hazmatShipment);

        // 3. 라인 분리
        for (ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productMap.get(lineReq.getProductId());
            ShipmentOrder targetShipment = (product.getCategory() == Product.Category.HAZMAT)
                    ? hazmatShipment : mainShipment;

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(targetShipment)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.Status.pending)
                    .build();
            shipmentOrderLineRepository.save(line);
        }

        // 원본(비-HAZMAT) 출고 지시서 반환
        return mainShipment;
    }

    /**
     * 피킹 실행
     * ALS-WMS-OUT-002: FIFO/FEFO, 만료 임박 우선, 부분출고 의사결정
     */
    @Transactional
    public ShipmentOrder executePicking(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.Status.pending) {
            throw new IllegalStateException("대기 상태의 출고만 피킹할 수 있습니다.");
        }

        shipment.setStatus(ShipmentOrder.Status.picking);
        shipmentOrderRepository.save(shipment);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            pickLine(line);
        }

        // 피킹 완료 후 출고 지시서 상태 업데이트
        updateShipmentStatus(shipment);

        return shipment;
    }

    /**
     * 라인별 피킹 처리
     * ALS-WMS-OUT-002: FIFO/FEFO + 잔여율 우선순위 + 부분출고 의사결정
     */
    private void pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // 1. 피킹 가능 재고 조회 (FIFO/FEFO + 만료 임박 우선)
        List<Inventory> pickableInventories = getPickableInventories(product);

        // 2. 가용 재고 합산
        int totalAvailable = pickableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 부분출고 의사결정 트리 (ALS-WMS-OUT-002)
        if (totalAvailable == 0) {
            // 전량 백오더
            handleFullBackorder(line, requestedQty);
        } else if (totalAvailable < requestedQty * 0.3) {
            // 가용 < 30%: 전량 백오더 (부분출고 안 함)
            handleFullBackorder(line, requestedQty);
        } else if (totalAvailable < requestedQty * 0.7) {
            // 30% ≤ 가용 < 70%: 부분출고 + 백오더 + 긴급발주 트리거
            int pickedQty = performPicking(line, pickableInventories, totalAvailable);
            handlePartialBackorder(line, pickedQty, requestedQty - pickedQty);
            triggerUrgentReorder(product);
        } else if (totalAvailable < requestedQty) {
            // 70% ≤ 가용 < 100%: 부분출고 + 백오더
            int pickedQty = performPicking(line, pickableInventories, totalAvailable);
            handlePartialBackorder(line, pickedQty, requestedQty - pickedQty);
        } else {
            // 가용 ≥ 100%: 정상 출고
            performPicking(line, pickableInventories, requestedQty);
            line.setStatus(ShipmentOrderLine.Status.picked);
        }

        shipmentOrderLineRepository.save(line);
    }

    /**
     * 피킹 가능 재고 조회
     * ALS-WMS-OUT-002: FIFO/FEFO + 만료 임박 우선
     */
    private List<Inventory> getPickableInventories(Product product) {
        LocalDate today = LocalDate.now();

        // 1. 기본 필터: is_expired=false, is_frozen=false, expiry_date >= today
        List<Inventory> allInventories = inventoryRepository.findByProduct_ProductId(product.getProductId());

        List<Inventory> pickable = allInventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> {
                    // 실사 동결 로케이션 제외
                    Location loc = inv.getLocation();
                    return !loc.getIsFrozen();
                })
                .filter(inv -> {
                    // 유통기한 지난 것 제외
                    if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 2. HAZMAT 카테고리는 HAZMAT zone만
        if (product.getCategory() == Product.Category.HAZMAT) {
            pickable = pickable.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 3. 잔여율 < 10% 재고 제외 및 is_expired 마킹
        pickable = pickable.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(
                                inv.getManufactureDate(), inv.getExpiryDate(), today);
                        if (remainingPct < 10) {
                            // 출고 불가 -> is_expired=true 설정
                            inv.setIsExpired(true);
                            inventoryRepository.save(inv);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 4. FIFO/FEFO 정렬
        if (product.getHasExpiry()) {
            // FEFO → FIFO (유통기한 관리 상품)
            pickable.sort((a, b) -> {
                // 잔여율 <30% 최우선
                double aPct = calculateRemainingShelfLifePct(a.getManufactureDate(), a.getExpiryDate(), today);
                double bPct = calculateRemainingShelfLifePct(b.getManufactureDate(), b.getExpiryDate(), today);

                boolean aUrgent = aPct < 30;
                boolean bUrgent = bPct < 30;

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;

                // FEFO
                int cmp = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (cmp != 0) return cmp;

                // FIFO
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO만 (유통기한 비관리 상품)
            pickable.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return pickable;
    }

    /**
     * 실제 피킹 수행
     */
    private int performPicking(ShipmentOrderLine line, List<Inventory> inventories, int qtyToPick) {
        Product product = line.getProduct();
        int remainingToPick = qtyToPick;
        int totalPicked = 0;

        for (Inventory inv : inventories) {
            if (remainingToPick == 0) break;

            // HAZMAT max_pick_qty 체크
            int maxPick = remainingToPick;
            if (product.getCategory() == Product.Category.HAZMAT && product.getMaxPickQty() != null) {
                maxPick = Math.min(maxPick, product.getMaxPickQty());
            }

            int pickQty = Math.min(inv.getQuantity(), maxPick);

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inventoryRepository.save(inv);

            // 로케이션 현재 수량 차감
            Location loc = inv.getLocation();
            loc.setCurrentQty(loc.getCurrentQty() - pickQty);
            locationRepository.save(loc);

            // 보관 유형 불일치 경고
            if (!inv.getLocation().getStorageType().equals(product.getStorageType())) {
                createAuditLog("SHIPMENT_PICKING", line.getLineId(),
                        "보관 유형 불일치: 로케이션=" + loc.getStorageType() + ", 상품=" + product.getStorageType(),
                        line.getShipmentOrder().getCreatedBy());
            }

            totalPicked += pickQty;
            remainingToPick -= pickQty;
        }

        line.setPickedQty(line.getPickedQty() + totalPicked);
        return totalPicked;
    }

    /**
     * 부분출고 처리
     */
    private void handlePartialBackorder(ShipmentOrderLine line, int pickedQty, int shortageQty) {
        line.setStatus(ShipmentOrderLine.Status.partial);

        // 백오더 생성
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.Status.open)
                .build();
        backorderRepository.save(backorder);
    }

    /**
     * 전량 백오더 처리
     */
    private void handleFullBackorder(ShipmentOrderLine line, int shortageQty) {
        line.setPickedQty(0);
        line.setStatus(ShipmentOrderLine.Status.backordered);

        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.Status.open)
                .build();
        backorderRepository.save(backorder);
    }

    /**
     * 긴급발주 트리거
     * ALS-WMS-OUT-002: 가용 30%~70% 구간에서 긴급발주
     */
    private void triggerUrgentReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .reorderQty(rule.getReorderQty())
                    .reason(AutoReorderLog.Reason.URGENT_REORDER)
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 출고 확정
     * ALS-WMS-OUT-002: 안전재고 체크
     */
    @Transactional
    public ShipmentOrder confirmShipment(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.Status.picking && shipment.getStatus() != ShipmentOrder.Status.partial) {
            throw new IllegalStateException("피킹 중이거나 부분출고 상태만 확정할 수 있습니다.");
        }

        shipment.setStatus(ShipmentOrder.Status.shipped);
        shipment.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipment);

        // 안전재고 체크
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProduct());
        }

        return shipment;
    }

    /**
     * 안전재고 체크
     * ALS-WMS-OUT-002 Constraint: 출고 후 안전재고 체크
     */
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule == null) return;

        // 전체 가용 재고 합산 (is_expired=false만)
        int totalAvailable = inventoryRepository.findByProduct_ProductId(product.getProductId()).stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주
        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .reorderQty(rule.getReorderQty())
                    .reason(AutoReorderLog.Reason.SAFETY_STOCK_TRIGGER)
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 출고 상태 업데이트
     */
    private void updateShipmentStatus(ShipmentOrder shipment) {
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipment.getShipmentId());

        boolean allPicked = lines.stream().allMatch(line -> line.getStatus() == ShipmentOrderLine.Status.picked);
        boolean anyPartial = lines.stream().anyMatch(line -> line.getStatus() == ShipmentOrderLine.Status.partial);

        if (allPicked) {
            shipment.setStatus(ShipmentOrder.Status.picking); // 피킹 완료, 확정 대기
        } else if (anyPartial) {
            shipment.setStatus(ShipmentOrder.Status.partial);
        }

        shipment.setPickedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipment);
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays == 0) return 0;
        return (double) remainingDays / totalDays * 100;
    }

    /**
     * 감사 로그 생성
     */
    private void createAuditLog(String action, UUID entityId, String description, String createdBy) {
        AuditLog log = AuditLog.builder()
                .entityType("SHIPMENT")
                .entityId(entityId)
                .action(action)
                .description(description)
                .createdBy(createdBy)
                .build();
        auditLogRepository.save(log);
    }

    /**
     * 출고 지시서 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrder getShipmentOrder(UUID shipmentId) {
        return shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentId));
    }

    /**
     * 출고 지시서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ShipmentOrder> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll();
    }
}
