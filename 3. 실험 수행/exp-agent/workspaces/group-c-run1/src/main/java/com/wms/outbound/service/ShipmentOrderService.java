package com.wms.outbound.service;

import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.outbound.dto.ShipmentOrderCreateRequest;
import com.wms.outbound.dto.ShipmentOrderResponse;
import com.wms.outbound.entity.*;
import com.wms.outbound.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderCreateRequest request) {
        // 출고 지시서 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
            .shipmentOrderId(UUID.randomUUID())
            .orderNumber(request.getOrderNumber())
            .customerName(request.getCustomerName())
            .status(ShipmentOrder.ShipmentStatus.PENDING)
            .requestedAt(Instant.now())
            .build();

        // 라인 생성
        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderCreateRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineReq.getProductId()));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                .lineId(UUID.randomUUID())
                .shipmentOrder(shipmentOrder)
                .product(product)
                .lineNumber(lineReq.getLineNumber())
                .requestedQty(lineReq.getRequestedQty())
                .pickedQty(0)
                .status(ShipmentOrderLine.LineStatus.PENDING)
                .build();
            lines.add(line);
        }

        shipmentOrder.setLines(lines);

        // HAZMAT + FRESH 분리 출고 체크 (ALS-WMS-OUT-002 Constraint)
        shipmentOrder = handleHazmatFreshSeparation(shipmentOrder);

        ShipmentOrder saved = shipmentOrderRepository.save(shipmentOrder);
        return ShipmentOrderResponse.from(saved);
    }

    /**
     * HAZMAT + FRESH 분리 출고 처리 (ALS-WMS-OUT-002 Constraint)
     * 동일 출고 지시서에 HAZMAT + FRESH 상품이 공존하면 분리 출고
     */
    private ShipmentOrder handleHazmatFreshSeparation(ShipmentOrder order) {
        boolean hasHazmat = order.getLines().stream()
            .anyMatch(line -> line.getProduct().getCategory() == Product.ProductCategory.HAZMAT);
        boolean hasFresh = order.getLines().stream()
            .anyMatch(line -> line.getProduct().getCategory() == Product.ProductCategory.FRESH);

        if (hasHazmat && hasFresh) {
            // HAZMAT 상품만 별도 shipment_order로 분할
            List<ShipmentOrderLine> hazmatLines = order.getLines().stream()
                .filter(line -> line.getProduct().getCategory() == Product.ProductCategory.HAZMAT)
                .collect(Collectors.toList());

            List<ShipmentOrderLine> nonHazmatLines = order.getLines().stream()
                .filter(line -> line.getProduct().getCategory() != Product.ProductCategory.HAZMAT)
                .collect(Collectors.toList());

            // 원래 주문에는 비-HAZMAT만 남김
            order.setLines(nonHazmatLines);

            // HAZMAT 전용 주문 생성
            ShipmentOrder hazmatOrder = ShipmentOrder.builder()
                .shipmentOrderId(UUID.randomUUID())
                .orderNumber(order.getOrderNumber() + "-HAZMAT")
                .customerName(order.getCustomerName())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .requestedAt(order.getRequestedAt())
                .lines(hazmatLines)
                .build();

            // HAZMAT 라인들의 shipmentOrder 참조 갱신
            hazmatLines.forEach(line -> line.setShipmentOrder(hazmatOrder));

            shipmentOrderRepository.save(hazmatOrder);
            log.info("HAZMAT + FRESH separation: Created separate HAZMAT order {}", hazmatOrder.getOrderNumber());
        }

        return order;
    }

    @Transactional
    public ShipmentOrderResponse executePickingAndShip(UUID shipmentOrderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
            .orElseThrow(() -> new IllegalArgumentException("ShipmentOrder not found: " + shipmentOrderId));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new IllegalStateException("ShipmentOrder is not in PENDING status");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        // 각 라인별 피킹 실행
        for (ShipmentOrderLine line : order.getLines()) {
            executePicking(line);
        }

        // 모든 라인이 picked이면 shipped로 변경
        boolean allPicked = order.getLines().stream()
            .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED);

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            order.setShippedAt(Instant.now());
        } else {
            order.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }

        ShipmentOrder saved = shipmentOrderRepository.save(order);

        // 출고 후 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
        for (ShipmentOrderLine line : saved.getLines()) {
            checkSafetyStockAfterShipment(line.getProduct());
        }

        return ShipmentOrderResponse.from(saved);
    }

    /**
     * 피킹 실행 (ALS-WMS-OUT-002 Rule 및 Constraint 준수)
     */
    private void executePicking(ShipmentOrderLine line) {
        Product product = line.getProduct();
        Integer requestedQty = line.getRequestedQty();

        // 피킹 가능한 재고 조회 (FIFO/FEFO 적용)
        List<Inventory> pickableInventory = getPickableInventory(product);

        // 전체 가용 재고 계산
        int totalAvailable = pickableInventory.stream()
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 부분출고 의사결정 트리 (ALS-WMS-OUT-002 Constraint)
        if (totalAvailable == 0) {
            // 가용 재고 = 0: 전량 백오더
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
        } else {
            double ratio = (double) totalAvailable / requestedQty;

            if (ratio < 0.30) {
                // 가용 < 30%: 전량 백오더 (부분출고 안 함)
                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
                createBackorder(line, requestedQty);
            } else if (ratio >= 0.30 && ratio < 0.70) {
                // 30% ≤ 가용 < 70%: 부분출고 + 백오더 + 긴급발주 트리거
                int pickedQty = pickFromInventory(line, pickableInventory, totalAvailable);
                line.setPickedQty(pickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
                createBackorder(line, requestedQty - pickedQty);
                triggerUrgentReorder(product, totalAvailable);
            } else if (ratio >= 0.70 && ratio < 1.0) {
                // 70% ≤ 가용 < 100%: 부분출고 + 백오더
                int pickedQty = pickFromInventory(line, pickableInventory, totalAvailable);
                line.setPickedQty(pickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
                createBackorder(line, requestedQty - pickedQty);
            } else {
                // 가용 ≥ 100%: 전량 피킹
                int pickedQty = pickFromInventory(line, pickableInventory, requestedQty);
                line.setPickedQty(pickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
            }
        }
    }

    /**
     * 피킹 가능한 재고 조회 (FIFO/FEFO, ALS-WMS-OUT-002 Constraint)
     */
    private List<Inventory> getPickableInventory(Product product) {
        LocalDate today = LocalDate.now();

        // 기본 피킹 제외 조건: is_expired=true, is_frozen=true, expiry_date < today
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> !inv.getIsExpired())
            .filter(inv -> !inv.getLocation().getIsFrozen())
            .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
            .filter(inv -> inv.getQuantity() > 0)
            .collect(Collectors.toList());

        // HAZMAT 상품은 HAZMAT zone에서만 피킹 (ALS-WMS-OUT-002 Constraint)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            allInventory = allInventory.stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                .collect(Collectors.toList());
        }

        // 잔여 유통기한 < 10% 재고는 is_expired=true로 설정하고 제외 (ALS-WMS-OUT-002 Constraint)
        for (Inventory inv : allInventory) {
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                    inv.getExpiryDate(),
                    inv.getManufactureDate(),
                    today
                );
                if (remainingPct < 10.0) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                }
            }
        }

        // is_expired=true로 변경된 재고 제외
        allInventory = allInventory.stream()
            .filter(inv -> !inv.getIsExpired())
            .collect(Collectors.toList());

        // FIFO/FEFO 정렬 (ALS-WMS-OUT-002 Constraint)
        List<Inventory> sorted;
        if (product.getHasExpiry()) {
            // 유통기한 관리 상품: FEFO 우선, 잔여율 <30% 최우선
            sorted = allInventory.stream()
                .sorted(Comparator
                    .comparing((Inventory inv) -> {
                        // 잔여율 <30%를 최우선으로
                        if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                            double pct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate(), today);
                            return pct < 30.0 ? 0 : 1;
                        }
                        return 1;
                    })
                    .thenComparing(inv -> inv.getExpiryDate() != null ? inv.getExpiryDate() : LocalDate.MAX)
                    .thenComparing(Inventory::getReceivedAt))
                .collect(Collectors.toList());
        } else {
            // 비-유통기한 관리 상품: FIFO만
            sorted = allInventory.stream()
                .sorted(Comparator.comparing(Inventory::getReceivedAt))
                .collect(Collectors.toList());
        }

        return sorted;
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 재고에서 실제 피킹 수행
     */
    private int pickFromInventory(ShipmentOrderLine line, List<Inventory> pickableInventory, int qtyToPick) {
        int remaining = qtyToPick;
        Product product = line.getProduct();

        for (Inventory inv : pickableInventory) {
            if (remaining <= 0) break;

            // HAZMAT max_pick_qty 체크 (ALS-WMS-OUT-002 Constraint)
            int maxAllowed = remaining;
            if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
                maxAllowed = Math.min(remaining, product.getMaxPickQty());
            }

            int pickQty = Math.min(inv.getQuantity(), maxAllowed);

            // inventory.quantity 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inventoryRepository.save(inv);

            // locations.current_qty 차감
            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고 (ALS-WMS-OUT-002 Constraint)
            if (location.getStorageType() != product.getStorageType()) {
                AuditLog auditLog = AuditLog.builder()
                    .logId(UUID.randomUUID())
                    .eventType(AuditLog.EventType.STORAGE_TYPE_MISMATCH)
                    .product(product)
                    .location(location)
                    .referenceId(line.getShipmentOrder().getShipmentOrderId())
                    .referenceType("SHIPMENT_ORDER")
                    .message(String.format("Storage type mismatch: Product %s (%s) picked from Location %s (%s)",
                        product.getSku(), product.getStorageType(), location.getCode(), location.getStorageType()))
                    .severity("WARNING")
                    .build();
                auditLogRepository.save(auditLog);
            }

            remaining -= pickQty;
        }

        return qtyToPick - remaining;
    }

    /**
     * 백오더 생성
     */
    private void createBackorder(ShipmentOrderLine line, int shortageQty) {
        if (shortageQty <= 0) return;

        Backorder backorder = Backorder.builder()
            .backorderId(UUID.randomUUID())
            .shipmentOrder(line.getShipmentOrder())
            .shipmentLine(line)
            .product(line.getProduct())
            .shortageQty(shortageQty)
            .status(Backorder.BackorderStatus.OPEN)
            .build();
        backorderRepository.save(backorder);
    }

    /**
     * 긴급발주 트리거 (ALS-WMS-OUT-002 Constraint)
     */
    private void triggerUrgentReorder(Product product, int currentQty) {
        AutoReorderLog log = AutoReorderLog.builder()
            .logId(UUID.randomUUID())
            .product(product)
            .triggerReason(AutoReorderLog.TriggerReason.URGENT_REORDER)
            .currentQty(currentQty)
            .reorderQty(0) // 긴급발주는 수량 미지정
            .referenceType("URGENT_SHIPMENT")
            .build();
        autoReorderLogRepository.save(log);
        log.info("Urgent reorder triggered for product {}: current qty {}", product.getSku(), currentQty);
    }

    /**
     * 출고 후 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
     */
    private void checkSafetyStockAfterShipment(Product product) {
        // 전체 가용 재고 합산 (is_expired=true 제외)
        int totalAvailable = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository
            .findByProduct_ProductIdAndIsActive(product.getProductId(), true);

        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();
            if (totalAvailable < rule.getMinQty()) {
                // 안전재고 미달 -> 자동 재발주
                AutoReorderLog log = AutoReorderLog.builder()
                    .logId(UUID.randomUUID())
                    .product(product)
                    .triggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentQty(totalAvailable)
                    .reorderQty(rule.getReorderQty())
                    .referenceType("SAFETY_STOCK")
                    .build();
                autoReorderLogRepository.save(log);
                log.info("Safety stock trigger for product {}: current {} < min {}, reorder qty {}",
                    product.getSku(), totalAvailable, rule.getMinQty(), rule.getReorderQty());
            }
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
            .orElseThrow(() -> new IllegalArgumentException("ShipmentOrder not found: " + shipmentOrderId));
        return ShipmentOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
            .map(ShipmentOrderResponse::from)
            .collect(Collectors.toList());
    }
}
