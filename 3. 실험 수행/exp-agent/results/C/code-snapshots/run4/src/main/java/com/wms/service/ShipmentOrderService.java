package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentOrderService {

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
     */
    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        log.info("출고 지시서 생성 시작: orderNumber = {}", request.getOrderNumber());

        // 1. 중복 체크
        if (shipmentOrderRepository.findByOrderNumber(request.getOrderNumber()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 출고 지시서 번호입니다: " + request.getOrderNumber());
        }

        // 2. HAZMAT + FRESH 분리 출고 체크 (ALS-WMS-OUT-002 Constraint)
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 FRESH가 공존하는지 확인
        boolean hasFresh = nonHazmatLines.stream().anyMatch(lineReq -> {
            Product p = productRepository.findById(lineReq.getProductId()).orElse(null);
            return p != null && p.getCategory() == Product.ProductCategory.FRESH;
        });

        // HAZMAT + FRESH 분리 출고 처리
        if (!hazmatLines.isEmpty() && hasFresh) {
            log.info("HAZMAT + FRESH 분리 출고 처리");

            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder mainOrder = createOrder(request, nonHazmatLines);

            // HAZMAT 출고 지시서 별도 생성
            ShipmentOrderRequest hazmatRequest = ShipmentOrderRequest.builder()
                    .orderNumber(request.getOrderNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .requestedShipDate(request.getRequestedShipDate())
                    .lines(hazmatLines)
                    .build();
            createOrder(hazmatRequest, hazmatLines);

            return ShipmentOrderResponse.fromEntity(mainOrder);
        } else {
            // 일반 출고 지시서 생성
            ShipmentOrder order = createOrder(request, request.getLines());
            return ShipmentOrderResponse.fromEntity(order);
        }
    }

    private ShipmentOrder createOrder(ShipmentOrderRequest request,
                                      List<ShipmentOrderRequest.ShipmentOrderLineRequest> lines) {
        ShipmentOrder order = ShipmentOrder.builder()
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .requestedShipDate(request.getRequestedShipDate())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : lines) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();

            order.addLine(line);
        }

        return shipmentOrderRepository.save(order);
    }

    /**
     * 피킹 실행
     */
    @Transactional
    public ShipmentOrderResponse executePicking(UUID shipmentOrderId) {
        log.info("피킹 실행 시작: shipmentOrderId = {}", shipmentOrderId);

        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentOrderId));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 출고 지시서만 피킹할 수 있습니다");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        for (ShipmentOrderLine line : order.getLines()) {
            pickLine(line);
        }

        // 모든 라인이 picked이면 shipped로 변경
        boolean allPicked = order.getLines().stream()
                .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED);

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        } else {
            order.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }

        return ShipmentOrderResponse.fromEntity(order);
    }

    private void pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        Integer requestedQty = line.getRequestedQty();

        log.info("라인 피킹 시작: product = {}, requestedQty = {}", product.getSku(), requestedQty);

        // 1. 피킹 가능한 재고 조회 (FIFO/FEFO 적용, ALS-WMS-OUT-002 Constraint)
        List<Inventory> pickableInventories = getPickableInventories(product);

        // 2. 가용 재고 합산
        int availableQty = pickableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        log.info("가용 재고: {}, 요청 수량: {}", availableQty, requestedQty);

        // 3. 부분출고 의사결정 트리 (ALS-WMS-OUT-002 Constraint)
        double availableRatio = (double) availableQty / requestedQty;

        if (availableQty == 0) {
            // 전량 백오더
            log.info("재고 없음 -> 전량 백오더");
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
            return;
        }

        if (availableRatio < 0.3) {
            // 가용 < 30% -> 전량 백오더 (부분출고 안 함)
            log.info("가용 재고 < 30% -> 전량 백오더");
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
            return;
        }

        // 4. 실제 피킹 수행
        int pickedQty = performPicking(pickableInventories, requestedQty, product);
        line.setPickedQty(pickedQty);

        if (pickedQty >= requestedQty) {
            // 완전 피킹
            line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
        } else {
            // 부분 피킹
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
            int shortageQty = requestedQty - pickedQty;
            createBackorder(line, shortageQty);

            // 30% <= 가용 < 70% -> 긴급발주 트리거
            if (availableRatio >= 0.3 && availableRatio < 0.7) {
                log.info("가용 재고 30%~70% -> 긴급발주 트리거");
                createAutoReorderLog(product, AutoReorderLog.TriggerReason.URGENT_REORDER, availableQty);
            }
        }

        // 5. 출고 후 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
        checkSafetyStock(product);
    }

    private List<Inventory> getPickableInventories(Product product) {
        LocalDate today = LocalDate.now();

        // 피킹 가능 조건:
        // - is_expired = false
        // - is_frozen = false (로케이션)
        // - expiry_date >= today (만료되지 않음)
        // - 잔여 유통기한 >= 10% (ALS-WMS-OUT-002 Constraint)
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .filter(inv -> inv.getQuantity() > 0)
                .collect(Collectors.toList());

        // 만료 재고 제외 및 잔여율 10% 미만 체크
        inventories = inventories.stream()
                .filter(inv -> {
                    if (Boolean.TRUE.equals(product.getHasExpiry())) {
                        if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                            return false; // 만료 제외
                        }

                        // 잔여율 10% 미만 체크
                        if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                            double remainingPct = calculateRemainingShelfLife(
                                    inv.getManufactureDate(), inv.getExpiryDate(), today);
                            if (remainingPct < 10.0) {
                                // 피킹 불가 -> is_expired = true로 설정
                                log.warn("잔여 유통기한 < 10% -> 피킹 불가 (폐기 전환): inventory = {}", inv.getInventoryId());
                                inv.setIsExpired(true);
                                inventoryRepository.save(inv);
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // HAZMAT zone 체크
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            inventories = inventories.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // FIFO/FEFO 정렬 (ALS-WMS-OUT-002 Constraint)
        if (Boolean.TRUE.equals(product.getHasExpiry())) {
            // 잔여율 < 30% 최우선 출고
            inventories.sort((inv1, inv2) -> {
                LocalDate exp1 = inv1.getExpiryDate();
                LocalDate exp2 = inv2.getExpiryDate();
                LocalDate mfg1 = inv1.getManufactureDate();
                LocalDate mfg2 = inv2.getManufactureDate();

                if (exp1 == null || mfg1 == null) return 1;
                if (exp2 == null || mfg2 == null) return -1;

                double rem1 = calculateRemainingShelfLife(mfg1, exp1, today);
                double rem2 = calculateRemainingShelfLife(mfg2, exp2, today);

                boolean isPriority1 = rem1 < 30.0;
                boolean isPriority2 = rem2 < 30.0;

                if (isPriority1 && !isPriority2) return -1;
                if (!isPriority1 && isPriority2) return 1;

                // 둘 다 <30% 또는 둘 다 >=30% -> FEFO → FIFO
                int expCompare = exp1.compareTo(exp2);
                if (expCompare != 0) return expCompare;
                return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
            });
        } else {
            // FIFO
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    private int performPicking(List<Inventory> inventories, int requestedQty, Product product) {
        int pickedQty = 0;
        Integer maxPickQty = product.getMaxPickQty();

        for (Inventory inventory : inventories) {
            if (pickedQty >= requestedQty) {
                break;
            }

            int needQty = requestedQty - pickedQty;

            // HAZMAT max_pick_qty 체크 (ALS-WMS-OUT-002 Constraint)
            if (product.getCategory() == Product.ProductCategory.HAZMAT && maxPickQty != null) {
                needQty = Math.min(needQty, maxPickQty - pickedQty);
                if (needQty <= 0) {
                    log.warn("HAZMAT max_pick_qty 초과 -> 피킹 중단: product = {}, maxPickQty = {}", product.getSku(), maxPickQty);
                    break;
                }
            }

            int pickQty = Math.min(inventory.getQuantity(), needQty);

            // inventory 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // location current_qty 차감
            Location location = inventory.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고 (ALS-WMS-OUT-002 Constraint)
            if (inventory.getLocation().getStorageType() != product.getStorageType()) {
                log.warn("보관 유형 불일치: location = {}, product = {}", location.getCode(), product.getSku());
                createAuditLog(AuditLog.EventType.STORAGE_TYPE_MISMATCH,
                        "Inventory", inventory.getInventoryId(),
                        String.format("보관 유형 불일치: location=%s (type=%s), product=%s (type=%s)",
                                location.getCode(), location.getStorageType(),
                                product.getSku(), product.getStorageType()));
            }

            pickedQty += pickQty;
        }

        return pickedQty;
    }

    private void createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct_ProductIdAndIsActiveTrue(product.getProductId());
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        // 전체 가용 재고 합산 (is_expired = false)
        int totalAvailableQty = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        log.info("안전재고 체크: product = {}, totalAvailableQty = {}, minQty = {}", product.getSku(), totalAvailableQty, rule.getMinQty());

        if (totalAvailableQty <= rule.getMinQty()) {
            log.warn("안전재고 미달 -> 자동 재발주 트리거: product = {}", product.getSku());
            createAutoReorderLog(product, AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER, totalAvailableQty);
        }
    }

    private void createAutoReorderLog(Product product, AutoReorderLog.TriggerReason reason, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductIdAndIsActiveTrue(product.getProductId())
                .orElse(null);

        int reorderQty = (rule != null) ? rule.getReorderQty() : 0;

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason(reason)
                .reorderQty(reorderQty)
                .currentStock(currentStock)
                .build();
        autoReorderLogRepository.save(log);
    }

    private void createAuditLog(AuditLog.EventType eventType, String entityType, UUID entityId, String description) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .build();
        auditLogRepository.save(log);
    }

    private double calculateRemainingShelfLife(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            return 100.0;
        }
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) {
            return 0.0;
        }
        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 출고 확정
     */
    @Transactional
    public ShipmentOrderResponse confirmShipment(UUID shipmentOrderId) {
        log.info("출고 확정 시작: shipmentOrderId = {}", shipmentOrderId);

        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentOrderId));

        if (order.getStatus() == ShipmentOrder.ShipmentStatus.SHIPPED) {
            throw new IllegalStateException("이미 출고 완료된 지시서입니다");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        order.setShippedAt(OffsetDateTime.now());

        return ShipmentOrderResponse.fromEntity(order);
    }

    /**
     * 출고 지시서 상세 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentOrderId));
        return ShipmentOrderResponse.fromEntity(order);
    }

    /**
     * 출고 지시서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(ShipmentOrderResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
