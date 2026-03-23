package com.wms.outbound.service;

import com.wms.common.entity.*;
import com.wms.common.repository.*;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.*;
import com.wms.outbound.dto.*;
import com.wms.outbound.entity.*;
import com.wms.outbound.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final BackorderRepository backorderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrder createShipmentOrder(CreateShipmentOrderRequest request) {
        // 출고 지시서 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : ZonedDateTime.now())
                .status(ShipmentOrderStatus.PENDING)
                .build();

        // HAZMAT과 FRESH 분리 체크
        List<ShipmentLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentLineRequest> nonHazmatLines = new ArrayList<>();
        boolean hasFresh = false;

        for (ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + lineReq.getProductId()));

            if (product.getCategory() == ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
                if (product.getCategory() == ProductCategory.FRESH) {
                    hasFresh = true;
                }
            }
        }

        // HAZMAT과 FRESH가 함께 있으면 HAZMAT을 별도 출고로 분리
        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 별도 출고 생성
            ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                    .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .requestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : ZonedDateTime.now())
                    .status(ShipmentOrderStatus.PENDING)
                    .build();

            for (ShipmentLineRequest hazmatLine : hazmatLines) {
                Product product = productRepository.findById(hazmatLine.getProductId()).orElseThrow();
                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(hazmatShipment)
                        .product(product)
                        .requestedQty(hazmatLine.getRequestedQty())
                        .pickedQty(0)
                        .status(ShipmentLineStatus.PENDING)
                        .build();
                hazmatShipment.getLines().add(line);
            }
            shipmentOrderRepository.save(hazmatShipment);

            // 원래 출고에는 비-HAZMAT만 포함
            for (ShipmentLineRequest nonHazmatLine : nonHazmatLines) {
                Product product = productRepository.findById(nonHazmatLine.getProductId()).orElseThrow();
                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(shipmentOrder)
                        .product(product)
                        .requestedQty(nonHazmatLine.getRequestedQty())
                        .pickedQty(0)
                        .status(ShipmentLineStatus.PENDING)
                        .build();
                shipmentOrder.getLines().add(line);
            }
        } else {
            // 분리 불필요, 모든 라인 추가
            for (ShipmentLineRequest lineReq : request.getLines()) {
                Product product = productRepository.findById(lineReq.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + lineReq.getProductId()));

                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(shipmentOrder)
                        .product(product)
                        .requestedQty(lineReq.getRequestedQty())
                        .pickedQty(0)
                        .status(ShipmentLineStatus.PENDING)
                        .build();
                shipmentOrder.getLines().add(line);
            }
        }

        return shipmentOrderRepository.save(shipmentOrder);
    }

    @Transactional
    public PickResponse executePicking(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment order not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrderStatus.PENDING) {
            throw new RuntimeException("Shipment order is not in PENDING status");
        }

        shipment.setStatus(ShipmentOrderStatus.PICKING);
        List<PickResultDto> pickResults = new ArrayList<>();

        for (ShipmentOrderLine line : shipment.getLines()) {
            PickResultDto pickResult = pickLineItem(line);
            pickResults.add(pickResult);
        }

        // 모든 라인 상태 체크하여 전체 출고 상태 결정
        boolean allPicked = shipment.getLines().stream()
                .allMatch(l -> l.getStatus() == ShipmentLineStatus.PICKED);
        boolean anyPicked = shipment.getLines().stream()
                .anyMatch(l -> l.getStatus() == ShipmentLineStatus.PICKED || l.getStatus() == ShipmentLineStatus.PARTIAL);

        if (allPicked) {
            shipment.setStatus(ShipmentOrderStatus.SHIPPED);
        } else if (anyPicked) {
            shipment.setStatus(ShipmentOrderStatus.PARTIAL);
        } else {
            // 전량 백오더
            shipment.setStatus(ShipmentOrderStatus.PARTIAL);
        }

        shipmentOrderRepository.save(shipment);

        return PickResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .shipmentNumber(shipment.getShipmentNumber())
                .status(shipment.getStatus().name())
                .pickResults(pickResults)
                .build();
    }

    private PickResultDto pickLineItem(ShipmentOrderLine line) {
        Product product = line.getProduct();
        Integer requestedQty = line.getRequestedQty();

        // 피킹 가능한 재고 조회
        List<Inventory> availableInventory = getAvailableInventory(product);

        // HAZMAT 제약 체크
        if (product.getCategory() == ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                requestedQty = product.getMaxPickQty();
            }
        }

        // 동결되지 않은 재고만 필터링
        List<Inventory> pickableInventory = availableInventory.stream()
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .collect(Collectors.toList());

        // 요청 수량을 채우는 최적 조합 계산
        List<Inventory> optimalCombination = findOptimalPickCombination(pickableInventory, requestedQty);

        List<PickDetail> pickDetails = new ArrayList<>();
        int totalPicked = 0;

        // 최적 조합으로 피킹 실행
        for (Inventory inv : optimalCombination) {
            int neededQty = requestedQty - totalPicked;
            int pickQty = Math.min(neededQty, inv.getQuantity());

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inv.getLocation().setCurrentQty(inv.getLocation().getCurrentQty() - pickQty);

            inventoryRepository.save(inv);
            locationRepository.save(inv.getLocation());

            // 피킹 상세 기록
            PickDetail detail = PickDetail.builder()
                    .locationId(inv.getLocation().getLocationId())
                    .locationCode(inv.getLocation().getCode())
                    .inventoryId(inv.getInventoryId())
                    .lotNumber(inv.getLotNumber())
                    .pickedQty(pickQty)
                    .build();
            pickDetails.add(detail);

            totalPicked += pickQty;

            // 보관유형 불일치 경고
            if (inv.getLocation().getStorageType() != product.getStorageType()) {
                logStorageTypeMismatch(inv, product);
            }
        }

        // 피킹 결과 처리
        line.setPickedQty(totalPicked);

        BackorderDto backorderDto = null;
        int availableTotal = availableInventory.stream()
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .mapToInt(Inventory::getQuantity)
                .sum();

        double fulfillmentRate = (double) availableTotal / line.getRequestedQty();

        if (totalPicked >= line.getRequestedQty()) {
            // 전량 피킹
            line.setStatus(ShipmentLineStatus.PICKED);
        } else if (fulfillmentRate >= 0.7) {
            // 70% 이상: 부분출고 + 백오더
            line.setStatus(ShipmentLineStatus.PARTIAL);
            backorderDto = createBackorder(line, line.getRequestedQty() - totalPicked);
        } else if (fulfillmentRate >= 0.3) {
            // 30~70%: 부분출고 + 백오더 + 긴급발주
            line.setStatus(ShipmentLineStatus.PARTIAL);
            backorderDto = createBackorder(line, line.getRequestedQty() - totalPicked);
            createUrgentReorder(product, availableTotal);
        } else {
            // 30% 미만: 전량 백오더
            line.setStatus(ShipmentLineStatus.BACKORDERED);
            backorderDto = createBackorder(line, line.getRequestedQty());
        }

        shipmentOrderLineRepository.save(line);

        return PickResultDto.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(product.getProductId())
                .productSku(product.getSku())
                .requestedQty(line.getRequestedQty())
                .pickedQty(totalPicked)
                .status(line.getStatus().name())
                .pickDetails(pickDetails)
                .backorder(backorderDto)
                .build();
    }

    /**
     * 요청 수량을 채우기 위한 최적의 로케이션 조합을 찾습니다.
     * 목표: 가장 적은 수의 로케이션으로 요청 수량을 충족
     * 우선순위:
     * 1. 단일 로케이션으로 전량 충족 가능한 경우 → 해당 로케이션 선택
     * 2. 여러 로케이션 필요 시 → 가장 적은 조합 선택 (Greedy: 큰 수량부터)
     * 3. 동일 조합 수일 경우 → FEFO/FIFO 우선순위 적용
     */
    private List<Inventory> findOptimalPickCombination(List<Inventory> inventories, int requestedQty) {
        if (inventories.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 단일 로케이션으로 충족 가능한지 확인
        Optional<Inventory> singleMatch = inventories.stream()
                .filter(inv -> inv.getQuantity() >= requestedQty)
                .min((a, b) -> {
                    // 유통기한 있으면 FEFO, 없으면 FIFO
                    if (a.getExpiryDate() != null && b.getExpiryDate() != null) {
                        int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                        if (expiryCompare != 0) return expiryCompare;
                    }
                    return a.getReceivedAt().compareTo(b.getReceivedAt());
                });

        if (singleMatch.isPresent()) {
            return Collections.singletonList(singleMatch.get());
        }

        // 2. 여러 로케이션 필요 - Greedy 알고리즘 (큰 수량부터)
        List<Inventory> sorted = new ArrayList<>(inventories);
        sorted.sort((a, b) -> {
            // 수량 내림차순 우선
            int qtyCompare = Integer.compare(b.getQuantity(), a.getQuantity());
            if (qtyCompare != 0) return qtyCompare;

            // 수량 같으면 FEFO/FIFO
            if (a.getExpiryDate() != null && b.getExpiryDate() != null) {
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;
            }
            return a.getReceivedAt().compareTo(b.getReceivedAt());
        });

        List<Inventory> result = new ArrayList<>();
        int accumulated = 0;

        for (Inventory inv : sorted) {
            if (accumulated >= requestedQty) {
                break;
            }
            result.add(inv);
            accumulated += inv.getQuantity();
        }

        return result;
    }

    private List<Inventory> getAvailableInventory(Product product) {
        // 전체 재고 조회
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getIsExpired())
                .collect(Collectors.toList());

        // 유통기한 체크 및 만료 처리
        LocalDate today = LocalDate.now();
        for (Inventory inv : allInventory) {
            if (product.getHasExpiry() && inv.getExpiryDate() != null) {
                // 유통기한 지남
                if (inv.getExpiryDate().isBefore(today)) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    continue;
                }

                // 잔여율 계산
                if (inv.getManufactureDate() != null) {
                    long totalDays = inv.getExpiryDate().toEpochDay() - inv.getManufactureDate().toEpochDay();
                    long remainingDays = inv.getExpiryDate().toEpochDay() - today.toEpochDay();
                    double remainingPct = (double) remainingDays / totalDays * 100;

                    // 잔여율 < 10%: 출고 불가
                    if (remainingPct < 10) {
                        inv.setIsExpired(true);
                        inventoryRepository.save(inv);
                    }
                }
            }
        }

        // 만료되지 않은 재고만 필터링
        List<Inventory> available = allInventory.stream()
                .filter(inv -> !inv.getIsExpired())
                .collect(Collectors.toList());

        // HAZMAT zone 제약 제거 (위험물 전용 구역 포화로 인해 일반 로케이션 사용 허용)

        // FIFO/FEFO 정렬
        if (product.getHasExpiry()) {
            // FEFO: 유통기한 오름차순, 잔여율 30% 미만 최우선
            available.sort((a, b) -> {
                LocalDate today2 = LocalDate.now();
                Double aPct = calculateRemainingShelfLifePct(a, today2);
                Double bPct = calculateRemainingShelfLifePct(b, today2);

                boolean aUrgent = aPct != null && aPct < 30;
                boolean bUrgent = bPct != null && bPct < 30;

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;

                // 유통기한 오름차순
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;

                // 유통기한 같으면 입고일 오름차순 (FIFO)
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO: 입고일 오름차순
            available.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return available;
    }

    private Double calculateRemainingShelfLifePct(Inventory inv, LocalDate today) {
        if (inv.getExpiryDate() == null || inv.getManufactureDate() == null) {
            return null;
        }
        long totalDays = inv.getExpiryDate().toEpochDay() - inv.getManufactureDate().toEpochDay();
        long remainingDays = inv.getExpiryDate().toEpochDay() - today.toEpochDay();
        return (double) remainingDays / totalDays * 100;
    }

    private BackorderDto createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);

        return BackorderDto.builder()
                .backorderId(backorder.getBackorderId())
                .shortageQty(shortageQty)
                .status(BackorderStatus.OPEN.name())
                .build();
    }

    private void createUrgentReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(TriggerType.URGENT_REORDER)
                .currentStock(currentStock)
                .minQty(rule != null ? rule.getMinQty() : 0)
                .reorderQty(rule != null ? rule.getReorderQty() : 100)
                .triggeredBy("SYSTEM")
                .build();
        autoReorderLogRepository.save(log);
    }

    private void checkSafetyStockAfterShipment(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return;
        }

        // 전체 가용 재고 계산 (is_expired=false만)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailable)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    private void logStorageTypeMismatch(Inventory inv, Product product) {
        Map<String, Object> details = new HashMap<>();
        details.put("productId", product.getProductId().toString());
        details.put("productSku", product.getSku());
        details.put("productStorageType", product.getStorageType().name());
        details.put("locationId", inv.getLocation().getLocationId().toString());
        details.put("locationCode", inv.getLocation().getCode());
        details.put("locationStorageType", inv.getLocation().getStorageType().name());

        AuditLog log = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(inv.getInventoryId())
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(log);
    }

    public ShipmentOrder getShipmentOrder(UUID shipmentId) {
        return shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment order not found: " + shipmentId));
    }

    public List<ShipmentOrder> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll();
    }

    @Transactional
    public ShipmentOrder confirmShipment(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment order not found: " + shipmentId));

        if (shipment.getStatus() == ShipmentOrderStatus.PICKING ||
            shipment.getStatus() == ShipmentOrderStatus.PARTIAL) {
            shipment.setStatus(ShipmentOrderStatus.SHIPPED);
            shipment.setShippedAt(ZonedDateTime.now());
        }

        return shipmentOrderRepository.save(shipment);
    }
}
