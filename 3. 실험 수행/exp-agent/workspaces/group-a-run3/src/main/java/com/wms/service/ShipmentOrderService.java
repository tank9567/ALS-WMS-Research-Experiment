package com.wms.service;

import com.wms.dto.ShipmentOrderLineRequest;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    public ShipmentOrderService(
            ShipmentOrderRepository shipmentOrderRepository,
            ShipmentOrderLineRepository shipmentOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            BackorderRepository backorderRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository,
            AuditLogRepository auditLogRepository) {
        this.shipmentOrderRepository = shipmentOrderRepository;
        this.shipmentOrderLineRepository = shipmentOrderLineRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.backorderRepository = backorderRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // HAZMAT과 FRESH 분리 여부 체크
        List<ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 비-HAZMAT이 함께 있으면 분리
        if (!hazmatLines.isEmpty() && !nonHazmatLines.isEmpty()) {
            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder nonHazmatOrder = createShipmentOrderInternal(
                    request.getOrderNumber(),
                    request.getCustomerName(),
                    nonHazmatLines
            );

            // HAZMAT 출고 지시서 생성 (별도)
            ShipmentOrder hazmatOrder = createShipmentOrderInternal(
                    request.getOrderNumber() + "-HAZMAT",
                    request.getCustomerName(),
                    hazmatLines
            );

            // 비-HAZMAT 출고 지시서를 기본으로 반환
            List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(nonHazmatOrder.getId());
            return buildResponse(nonHazmatOrder, lines);
        } else {
            // 분리 불필요, 일반 생성
            ShipmentOrder order = createShipmentOrderInternal(
                    request.getOrderNumber(),
                    request.getCustomerName(),
                    request.getLines()
            );

            List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(order.getId());
            return buildResponse(order, lines);
        }
    }

    private ShipmentOrder createShipmentOrderInternal(String orderNumber, String customerName,
                                                      List<ShipmentOrderLineRequest> lineRequests) {
        ShipmentOrder order = new ShipmentOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerName(customerName);
        order.setStatus(ShipmentOrder.ShipmentStatus.pending);
        order = shipmentOrderRepository.save(order);

        for (ShipmentOrderLineRequest lineReq : lineRequests) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            ShipmentOrderLine line = new ShipmentOrderLine();
            line.setShipmentOrder(order);
            line.setProduct(product);
            line.setRequestedQuantity(lineReq.getRequestedQuantity());
            line.setPickedQuantity(0);
            line.setStatus(ShipmentOrderLine.LineStatus.pending);
            shipmentOrderLineRepository.save(line);
        }

        return order;
    }

    @Transactional
    public ShipmentOrderResponse pickShipmentOrder(UUID id) {
        ShipmentOrder order = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new IllegalArgumentException("Order is not in pending status");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.picking);
        order = shipmentOrderRepository.save(order);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        for (ShipmentOrderLine line : lines) {
            Product product = line.getProduct();
            int requestedQty = line.getRequestedQuantity();

            // HAZMAT 상품인 경우 max_pick_qty 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
                if (requestedQty > product.getMaxPickQty()) {
                    throw new IllegalArgumentException("HAZMAT product exceeds max pick quantity: " + product.getSku());
                }
            }

            // 피킹 가능한 재고 조회
            List<Inventory> availableInventory = getAvailableInventoryForPicking(product);

            // 최적 피킹 조합 계산
            List<Inventory> optimalPicks = calculateOptimalPickingCombination(availableInventory, requestedQty);

            int totalPicked = 0;

            for (Inventory inv : optimalPicks) {
                // 실사 동결된 로케이션은 이미 필터링되었으므로 스킵
                if (inv.getLocation().getIsFrozen()) {
                    continue;
                }

                int pickQty = Math.min(inv.getQuantity(), requestedQty - totalPicked);

                // 재고 차감
                inv.setQuantity(inv.getQuantity() - pickQty);
                inv.setUpdatedAt(OffsetDateTime.now());
                inventoryRepository.save(inv);

                // 로케이션 현재 수량 감소
                Location location = inv.getLocation();
                location.setCurrentQuantity(location.getCurrentQuantity() - pickQty);
                location.setUpdatedAt(OffsetDateTime.now());
                locationRepository.save(location);

                // 보관 유형 불일치 경고
                if (product.getStorageType() != location.getStorageType()) {
                    createAuditLog("SHIPMENT_PICK", order.getId(),
                            "Storage type mismatch: Product " + product.getSku() +
                                    " (" + product.getStorageType() + ") picked from location " +
                                    location.getCode() + " (" + location.getStorageType() + ")");
                }

                totalPicked += pickQty;
                if (totalPicked >= requestedQty) break;
            }

            // 부분출고 의사결정 트리
            double fulfillmentRate = (double) totalPicked / requestedQty;

            if (fulfillmentRate >= 0.70) {
                // 70% 이상: 부분출고 + 백오더
                line.setPickedQuantity(totalPicked);
                if (totalPicked >= requestedQty) {
                    line.setStatus(ShipmentOrderLine.LineStatus.picked);
                } else {
                    line.setStatus(ShipmentOrderLine.LineStatus.partial);
                    createBackorder(line, requestedQty - totalPicked);
                }
            } else if (fulfillmentRate >= 0.30) {
                // 30% ~ 70%: 부분출고 + 백오더 + 긴급발주
                line.setPickedQuantity(totalPicked);
                line.setStatus(ShipmentOrderLine.LineStatus.partial);
                createBackorder(line, requestedQty - totalPicked);
                createEmergencyReorder(product, totalPicked);
            } else {
                // 30% 미만: 전량 백오더
                line.setPickedQuantity(0);
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                createBackorder(line, requestedQty);
            }

            line.setUpdatedAt(OffsetDateTime.now());
            shipmentOrderLineRepository.save(line);
        }

        // 출고 지시서 상태 업데이트
        lines = shipmentOrderLineRepository.findByShipmentOrderId(id);
        boolean allPicked = lines.stream().allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.picked);
        boolean anyPartial = lines.stream().anyMatch(l ->
                l.getStatus() == ShipmentOrderLine.LineStatus.partial ||
                        l.getStatus() == ShipmentOrderLine.LineStatus.backordered);

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.picking);
        } else if (anyPartial) {
            order.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }

        order.setUpdatedAt(OffsetDateTime.now());
        order = shipmentOrderRepository.save(order);

        return buildResponse(order, lines);
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID id) {
        ShipmentOrder order = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
                order.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new IllegalArgumentException("Order is not ready for shipping");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        order.setShippedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order = shipmentOrderRepository.save(order);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);
        return buildResponse(order, lines);
    }

    public ShipmentOrderResponse getShipmentOrder(UUID id) {
        ShipmentOrder order = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);
        return buildResponse(order, lines);
    }

    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> orders = shipmentOrderRepository.findAll();
        return orders.stream()
                .map(order -> {
                    List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(order.getId());
                    return buildResponse(order, lines);
                })
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    /**
     * 요청 수량을 가장 효율적으로 충족하는 로케이션 조합을 계산합니다.
     * 우선순위:
     * 1. 단일 로케이션에서 완전 충족 가능 여부
     * 2. 최소 로케이션 개수로 충족
     * 3. 큰 수량을 가진 로케이션 우선
     */
    private List<Inventory> calculateOptimalPickingCombination(List<Inventory> availableInventory, int requestedQty) {
        // 동결된 로케이션 제외
        availableInventory = availableInventory.stream()
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .collect(Collectors.toList());

        if (availableInventory.isEmpty()) {
            return new ArrayList<>();
        }

        // 전략 1: 단일 로케이션에서 완전 충족 가능한 경우
        Optional<Inventory> singleFullfillment = availableInventory.stream()
                .filter(inv -> inv.getQuantity() >= requestedQty)
                .min(Comparator.comparingInt(Inventory::getQuantity)); // 가장 적은 잉여 재고를 가진 로케이션 선택

        if (singleFullfillment.isPresent()) {
            return Collections.singletonList(singleFullfillment.get());
        }

        // 전략 2: 최소 로케이션 조합 (큰 수량부터 그리디 선택)
        List<Inventory> sortedByQuantityDesc = availableInventory.stream()
                .sorted(Comparator.comparingInt(Inventory::getQuantity).reversed())
                .collect(Collectors.toList());

        List<Inventory> selectedInventories = new ArrayList<>();
        int accumulated = 0;

        for (Inventory inv : sortedByQuantityDesc) {
            selectedInventories.add(inv);
            accumulated += inv.getQuantity();

            if (accumulated >= requestedQty) {
                break;
            }
        }

        return selectedInventories;
    }

    private List<Inventory> getAvailableInventoryForPicking(Product product) {
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getExpired())
                .collect(Collectors.toList());

        // 유통기한이 지난 재고 제외
        LocalDate today = LocalDate.now();
        allInventory = allInventory.stream()
                .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        // 잔여율 < 10% 재고 폐기 전환
        for (Inventory inv : allInventory) {
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                        inv.getManufactureDate(),
                        inv.getExpiryDate(),
                        today
                );

                if (remainingPct < 10) {
                    inv.setExpired(true);
                    inv.setUpdatedAt(OffsetDateTime.now());
                    inventoryRepository.save(inv);
                }
            }
        }

        // expired 재고 필터링
        allInventory = allInventory.stream()
                .filter(inv -> !inv.getExpired())
                .collect(Collectors.toList());

        // 유통기한 관리 상품: FEFO + 잔여율 우선순위
        if (product.getRequiresExpiryTracking()) {
            allInventory.sort((inv1, inv2) -> {
                LocalDate exp1 = inv1.getExpiryDate();
                LocalDate exp2 = inv2.getExpiryDate();

                if (exp1 == null && exp2 == null) {
                    return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
                }
                if (exp1 == null) return 1;
                if (exp2 == null) return -1;

                // 잔여율 계산
                double rem1 = calculateRemainingShelfLifePct(inv1.getManufactureDate(), exp1, today);
                double rem2 = calculateRemainingShelfLifePct(inv2.getManufactureDate(), exp2, today);

                // 잔여율 < 30%인 재고 최우선
                boolean priority1 = rem1 < 30;
                boolean priority2 = rem2 < 30;

                if (priority1 && !priority2) return -1;
                if (!priority1 && priority2) return 1;

                // 동일 우선순위에서는 유통기한 빠른 순
                int expCompare = exp1.compareTo(exp2);
                if (expCompare != 0) return expCompare;

                // 유통기한 같으면 FIFO
                return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
            });
        } else {
            // 일반 상품: FIFO
            allInventory.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return allInventory;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            return 100;
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void createBackorder(ShipmentOrderLine line, int quantity) {
        Backorder backorder = new Backorder();
        backorder.setShipmentOrderLine(line);
        backorder.setProduct(line.getProduct());
        backorder.setQuantity(quantity);
        backorder.setStatus(Backorder.BackorderStatus.open);
        backorderRepository.save(backorder);
    }

    private void createEmergencyReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);

        if (rule != null) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerReason(AutoReorderLog.TriggerReason.EMERGENCY_REORDER);
            log.setReorderQty(rule.getReorderQty());
            log.setCurrentStock(currentStock);
            autoReorderLogRepository.save(log);
        }
    }

    private void checkSafetyStock(Product product) {
        // 전체 가용 재고 계산 (모든 로케이션 합산, expired 제외)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);

        if (rule != null && totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER);
            log.setReorderQty(rule.getReorderQty());
            log.setCurrentStock(totalAvailable);
            autoReorderLogRepository.save(log);
        }
    }

    private void createAuditLog(String action, UUID entityId, String description) {
        AuditLog log = new AuditLog();
        log.setEntityType("SHIPMENT_ORDER");
        log.setEntityId(entityId);
        log.setAction(action);
        log.setDescription(description);
        auditLogRepository.save(log);
    }

    private ShipmentOrderResponse buildResponse(ShipmentOrder order, List<ShipmentOrderLine> lines) {
        ShipmentOrderResponse response = new ShipmentOrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setCustomerName(order.getCustomerName());
        response.setStatus(order.getStatus().name());
        response.setCreatedAt(order.getCreatedAt());
        response.setShippedAt(order.getShippedAt());

        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    ShipmentOrderResponse.ShipmentOrderLineResponse lineResp =
                            new ShipmentOrderResponse.ShipmentOrderLineResponse();
                    lineResp.setId(line.getId());
                    lineResp.setProductId(line.getProduct().getId());
                    lineResp.setRequestedQuantity(line.getRequestedQuantity());
                    lineResp.setPickedQuantity(line.getPickedQuantity());
                    lineResp.setStatus(line.getStatus().name());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);
        return response;
    }
}
