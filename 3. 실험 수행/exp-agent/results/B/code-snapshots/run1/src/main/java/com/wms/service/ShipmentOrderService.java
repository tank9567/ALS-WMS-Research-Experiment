package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShipmentOrderService {

    @Autowired
    private ShipmentOrderRepository shipmentOrderRepository;

    @Autowired
    private ShipmentOrderLineRepository shipmentOrderLineRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BackorderRepository backorderRepository;

    @Autowired
    private SafetyStockRuleRepository safetyStockRuleRepository;

    @Autowired
    private AutoReorderLogRepository autoReorderLogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 출고 지시서 생성
        ShipmentOrder shipmentOrder = new ShipmentOrder();
        shipmentOrder.setShipmentNumber(request.getShipmentNumber());
        shipmentOrder.setCustomerName(request.getCustomerName());
        shipmentOrder.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : OffsetDateTime.now());
        shipmentOrder.setStatus("pending");
        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // HAZMAT과 FRESH 상품 분리 확인
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();
        boolean hasFresh = false;

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineReq.getProductId()));

            if ("HAZMAT".equals(product.getCategory())) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
                if ("FRESH".equals(product.getCategory())) {
                    hasFresh = true;
                }
            }
        }

        // HAZMAT과 FRESH가 함께 있으면 분리 출고
        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 상품만 별도 출고 지시서 생성
            ShipmentOrder hazmatShipment = new ShipmentOrder();
            hazmatShipment.setShipmentNumber(request.getShipmentNumber() + "-HAZMAT");
            hazmatShipment.setCustomerName(request.getCustomerName());
            hazmatShipment.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : OffsetDateTime.now());
            hazmatShipment.setStatus("pending");
            hazmatShipment = shipmentOrderRepository.save(hazmatShipment);

            // HAZMAT 라인 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : hazmatLines) {
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentId(hazmatShipment.getShipmentId());
                line.setProductId(lineReq.getProductId());
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus("pending");
                shipmentOrderLineRepository.save(line);
            }

            // 원래 출고 지시서에는 비-HAZMAT 라인만 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : nonHazmatLines) {
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentId(shipmentOrder.getShipmentId());
                line.setProductId(lineReq.getProductId());
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus("pending");
                shipmentOrderLineRepository.save(line);
            }
        } else {
            // 분리 불필요 - 모든 라인 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentId(shipmentOrder.getShipmentId());
                line.setProductId(lineReq.getProductId());
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus("pending");
                shipmentOrderLineRepository.save(line);
            }
        }

        return convertToResponse(shipmentOrder);
    }

    @Transactional
    public ShipmentOrderResponse pickShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found: " + shipmentId));

        if (!"pending".equals(shipmentOrder.getStatus())) {
            throw new IllegalStateException("Shipment order is not in pending status");
        }

        shipmentOrder.setStatus("picking");
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);

        List<ShipmentOrderResponse.PickDetail> pickDetails = new ArrayList<>();
        List<ShipmentOrderResponse.BackorderInfo> backorders = new ArrayList<>();

        for (ShipmentOrderLine line : lines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + line.getProductId()));

            // 가용 재고 조회 및 정렬 (FIFO/FEFO)
            List<Inventory> availableInventories = getAvailableInventorySorted(product, line.getProductId());

            int requestedQty = line.getRequestedQty();
            int totalAvailable = availableInventories.stream()
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            // HAZMAT 상품인 경우 max_pick_qty 제한
            if ("HAZMAT".equals(product.getCategory()) && product.getMaxPickQty() != null) {
                requestedQty = Math.min(requestedQty, product.getMaxPickQty());
            }

            int pickedQty = 0;

            // 피킹 실행
            for (Inventory inventory : availableInventories) {
                if (pickedQty >= requestedQty) {
                    break;
                }

                Location location = inventory.getLocation();

                // 실사 동결 로케이션 제외
                if (location.getIsFrozen()) {
                    continue;
                }

                // HAZMAT 상품은 HAZMAT zone에서만 피킹
                if ("HAZMAT".equals(product.getCategory()) && !"HAZMAT".equals(location.getZone())) {
                    continue;
                }

                int pickQty = Math.min(inventory.getQuantity(), requestedQty - pickedQty);

                // 재고 차감
                inventory.setQuantity(inventory.getQuantity() - pickQty);
                inventoryRepository.save(inventory);

                // 로케이션 수량 차감
                location.setCurrentQty(location.getCurrentQty() - pickQty);
                locationRepository.save(location);

                pickedQty += pickQty;

                // 피킹 상세 기록
                ShipmentOrderResponse.PickDetail pickDetail = new ShipmentOrderResponse.PickDetail();
                pickDetail.setProductId(line.getProductId());
                pickDetail.setLocationId(location.getLocationId());
                pickDetail.setPickedQty(pickQty);
                pickDetails.add(pickDetail);

                // 보관 유형 불일치 경고
                if (!product.getStorageType().equals(location.getStorageType())) {
                    logStorageTypeMismatch(shipmentOrder.getShipmentId(), product, location);
                }
            }

            // 라인 상태 업데이트
            line.setPickedQty(pickedQty);

            double fulfillmentRate = totalAvailable > 0 ? (double) pickedQty / requestedQty : 0;

            if (pickedQty == requestedQty) {
                line.setStatus("picked");
            } else if (pickedQty == 0) {
                // 전량 백오더
                line.setStatus("backordered");
                Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), requestedQty);
                backorders.add(convertToBackorderInfo(backorder));
            } else {
                // 부분출고 의사결정
                if (fulfillmentRate >= 0.7) {
                    // 70% 이상: 부분출고 + 백오더
                    line.setStatus("partial");
                    int shortageQty = requestedQty - pickedQty;
                    Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), shortageQty);
                    backorders.add(convertToBackorderInfo(backorder));
                } else if (fulfillmentRate >= 0.3) {
                    // 30%~70%: 부분출고 + 백오더 + 긴급발주
                    line.setStatus("partial");
                    int shortageQty = requestedQty - pickedQty;
                    Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), shortageQty);
                    backorders.add(convertToBackorderInfo(backorder));
                    triggerUrgentReorder(line.getProductId(), totalAvailable);
                } else {
                    // 30% 미만: 전량 백오더
                    line.setStatus("backordered");
                    line.setPickedQty(0);
                    Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), requestedQty);
                    backorders.add(convertToBackorderInfo(backorder));

                    // 피킹한 재고를 롤백
                    for (ShipmentOrderResponse.PickDetail detail : pickDetails) {
                        if (detail.getProductId().equals(line.getProductId())) {
                            Inventory inv = inventoryRepository.findByProductAndLocationAndLotNumber(
                                    detail.getProductId(), detail.getLocationId(), null)
                                    .orElse(null);
                            if (inv != null) {
                                inv.setQuantity(inv.getQuantity() + detail.getPickedQty());
                                inventoryRepository.save(inv);

                                Location loc = locationRepository.findById(detail.getLocationId()).orElse(null);
                                if (loc != null) {
                                    loc.setCurrentQty(loc.getCurrentQty() + detail.getPickedQty());
                                    locationRepository.save(loc);
                                }
                            }
                        }
                    }
                    pickDetails.removeIf(d -> d.getProductId().equals(line.getProductId()));
                }
            }

            shipmentOrderLineRepository.save(line);
        }

        // 출고 지시서 상태 업데이트
        boolean allPicked = lines.stream().allMatch(l -> "picked".equals(l.getStatus()));
        boolean anyPicked = lines.stream().anyMatch(l -> "picked".equals(l.getStatus()) || "partial".equals(l.getStatus()));

        if (allPicked) {
            shipmentOrder.setStatus("shipped");
        } else if (anyPicked) {
            shipmentOrder.setStatus("partial");
        } else {
            shipmentOrder.setStatus("pending");
        }

        shipmentOrderRepository.save(shipmentOrder);

        ShipmentOrderResponse response = convertToResponse(shipmentOrder);
        response.setPickDetails(pickDetails);
        response.setBackorders(backorders);

        return response;
    }

    @Transactional
    public ShipmentOrderResponse shipShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found: " + shipmentId));

        if (!"picking".equals(shipmentOrder.getStatus()) && !"partial".equals(shipmentOrder.getStatus())) {
            throw new IllegalStateException("Shipment order is not ready to ship");
        }

        shipmentOrder.setStatus("shipped");
        shipmentOrder.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipmentOrder);

        return convertToResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found: " + shipmentId));
        return convertToResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private List<Inventory> getAvailableInventorySorted(Product product, UUID productId) {
        List<Inventory> inventories = inventoryRepository.findAvailableInventoryByProductId(productId);

        LocalDate today = LocalDate.now();

        // 만료 재고 및 is_expired=true 제외
        inventories = inventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        // 잔여 유통기한 < 10% 재고는 expired 처리
        for (Inventory inv : new ArrayList<>(inventories)) {
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                if (remainingPct < 10) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    inventories.remove(inv);
                }
            }
        }

        // 효율적 피킹을 위한 정렬: 수량 내림차순 (가장 많은 수량이 있는 로케이션 우선)
        // 이렇게 하면 피킹 작업 횟수가 최소화됨
        inventories.sort(Comparator.comparing(Inventory::getQuantity).reversed());

        return inventories;
    }

    private boolean isLowShelfLife(Inventory inventory) {
        if (inventory.getExpiryDate() == null || inventory.getManufactureDate() == null) {
            return false;
        }
        double remainingPct = calculateRemainingShelfLifePct(inventory.getExpiryDate(), inventory.getManufactureDate());
        return remainingPct < 30;
    }

    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) return 0;
        return (double) remainingDays / totalDays * 100;
    }

    private Backorder createBackorder(UUID shipmentLineId, UUID productId, int shortageQty) {
        Backorder backorder = new Backorder();
        backorder.setShipmentLineId(shipmentLineId);
        backorder.setProductId(productId);
        backorder.setShortageQty(shortageQty);
        backorder.setStatus("open");
        return backorderRepository.save(backorder);
    }

    private void triggerUrgentReorder(UUID productId, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);

        AutoReorderLog log = new AutoReorderLog();
        log.setProductId(productId);
        log.setTriggerType("URGENT_REORDER");
        log.setCurrentStock(currentStock);
        log.setMinQty(rule != null ? rule.getMinQty() : 0);
        log.setReorderQty(rule != null ? rule.getReorderQty() : 0);
        log.setTriggeredBy("SYSTEM");
        autoReorderLogRepository.save(log);
    }

    private void checkSafetyStockAndReorder(UUID productId) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);
        if (rule == null) {
            return;
        }

        Integer totalAvailable = inventoryRepository.getTotalAvailableQuantityByProductId(productId);
        if (totalAvailable == null) totalAvailable = 0;

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProductId(productId);
            log.setTriggerType("SAFETY_STOCK_TRIGGER");
            log.setCurrentStock(totalAvailable);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(log);
        }
    }

    private void logStorageTypeMismatch(UUID shipmentId, Product product, Location location) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventType("STORAGE_TYPE_MISMATCH");
        auditLog.setEntityType("SHIPMENT_ORDER");
        auditLog.setEntityId(shipmentId);
        auditLog.setDetails("{\"productId\":\"" + product.getProductId() +
                           "\",\"productStorageType\":\"" + product.getStorageType() +
                           "\",\"locationId\":\"" + location.getLocationId() +
                           "\",\"locationStorageType\":\"" + location.getStorageType() + "\"}");
        auditLog.setPerformedBy("SYSTEM");
        auditLogRepository.save(auditLog);
    }

    private ShipmentOrderResponse convertToResponse(ShipmentOrder shipmentOrder) {
        ShipmentOrderResponse response = new ShipmentOrderResponse();
        response.setShipmentId(shipmentOrder.getShipmentId());
        response.setShipmentNumber(shipmentOrder.getShipmentNumber());
        response.setCustomerName(shipmentOrder.getCustomerName());
        response.setStatus(shipmentOrder.getStatus());
        response.setRequestedAt(shipmentOrder.getRequestedAt());
        response.setShippedAt(shipmentOrder.getShippedAt());

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentOrder.getShipmentId());
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
                .map(this::convertToLineResponse)
                .collect(Collectors.toList());
        response.setLines(lineResponses);

        return response;
    }

    private ShipmentOrderResponse.ShipmentOrderLineResponse convertToLineResponse(ShipmentOrderLine line) {
        ShipmentOrderResponse.ShipmentOrderLineResponse lineResponse = new ShipmentOrderResponse.ShipmentOrderLineResponse();
        lineResponse.setShipmentLineId(line.getShipmentLineId());
        lineResponse.setProductId(line.getProductId());
        lineResponse.setRequestedQty(line.getRequestedQty());
        lineResponse.setPickedQty(line.getPickedQty());
        lineResponse.setStatus(line.getStatus());
        return lineResponse;
    }

    private ShipmentOrderResponse.BackorderInfo convertToBackorderInfo(Backorder backorder) {
        ShipmentOrderResponse.BackorderInfo info = new ShipmentOrderResponse.BackorderInfo();
        info.setBackorderId(backorder.getBackorderId());
        info.setProductId(backorder.getProductId());
        info.setShortageQty(backorder.getShortageQty());
        return info;
    }
}
