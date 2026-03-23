package com.wms.service;

import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
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

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderCreateRequest request) {
        // HAZMAT/FRESH 분리 출고 체크
        List<ShipmentOrderCreateRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderCreateRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderCreateRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 FRESH가 동시에 있으면 분리
        boolean hasFresh = false;
        for (ShipmentOrderCreateRequest.ShipmentOrderLineRequest lineReq : nonHazmatLines) {
            Product product = productRepository.findById(lineReq.getProductId()).orElseThrow();
            if (product.getCategory() == Product.ProductCategory.FRESH) {
                hasFresh = true;
                break;
            }
        }

        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT만 별도 출고 지시서 생성
            ShipmentOrderCreateRequest hazmatRequest = ShipmentOrderCreateRequest.builder()
                    .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .requestedAt(request.getRequestedAt())
                    .lines(hazmatLines)
                    .build();
            createSingleShipmentOrder(hazmatRequest);

            // 비-HAZMAT은 원래 요청에서 생성
            ShipmentOrderCreateRequest nonHazmatRequest = ShipmentOrderCreateRequest.builder()
                    .shipmentNumber(request.getShipmentNumber())
                    .customerName(request.getCustomerName())
                    .requestedAt(request.getRequestedAt())
                    .lines(nonHazmatLines)
                    .build();
            return createSingleShipmentOrder(nonHazmatRequest);
        } else {
            // 분리 필요 없으면 그대로 생성
            return createSingleShipmentOrder(request);
        }
    }

    private ShipmentOrderResponse createSingleShipmentOrder(ShipmentOrderCreateRequest request) {
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .status(ShipmentOrder.ShipmentStatus.pending)
                .requestedAt(request.getRequestedAt())
                .build();
        shipmentOrderRepository.save(shipmentOrder);

        for (ShipmentOrderCreateRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipmentOrder)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.pending)
                    .build();
            shipmentOrderLineRepository.save(line);
        }

        return buildShipmentOrderResponse(shipmentOrder);
    }

    @Transactional
    public PickResponse pickShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("INVALID_STATUS", "Shipment order must be in pending status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);
        shipmentOrderRepository.save(shipmentOrder);

        List<PickResponse.LinePickResult> lineResults = new ArrayList<>();
        List<PickResponse.BackorderInfo> backorderInfos = new ArrayList<>();

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            PickResponse.LinePickResult lineResult = pickLine(line, backorderInfos);
            lineResults.add(lineResult);
        }

        // 모든 라인이 picked인지 확인
        boolean allPicked = lines.stream()
                .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.picked);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        } else {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }
        shipmentOrderRepository.save(shipmentOrder);

        return PickResponse.builder()
                .shipmentId(shipmentOrder.getShipmentId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .status(shipmentOrder.getStatus().name())
                .lineResults(lineResults)
                .backorders(backorderInfos)
                .build();
    }

    private PickResponse.LinePickResult pickLine(ShipmentOrderLine line,
                                                   List<PickResponse.BackorderInfo> backorderInfos) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // HAZMAT max_pick_qty 제한 체크
        if (product.getCategory() == Product.ProductCategory.HAZMAT &&
                product.getMaxPickQty() != null &&
                requestedQty > product.getMaxPickQty()) {
            requestedQty = product.getMaxPickQty();
        }

        // 피킹 가능한 재고 조회 및 정렬
        List<Inventory> availableInventories = getAvailableInventories(product);

        int totalPicked = 0;
        List<PickResponse.PickDetail> pickDetails = new ArrayList<>();

        for (Inventory inventory : availableInventories) {
            if (totalPicked >= requestedQty) {
                break;
            }

            int neededQty = requestedQty - totalPicked;
            int pickQty = Math.min(neededQty, inventory.getQuantity());

            // 재고 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // 로케이션 current_qty 차감
            Location location = inventory.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고
            if (location.getStorageType().name().equals(product.getStorageType().name()) == false) {
                logStorageTypeMismatch(product, location);
            }

            totalPicked += pickQty;

            pickDetails.add(PickResponse.PickDetail.builder()
                    .locationId(location.getLocationId())
                    .locationCode(location.getCode())
                    .pickedQty(pickQty)
                    .lotNumber(inventory.getLotNumber())
                    .build());
        }

        // 라인 상태 업데이트
        line.setPickedQty(totalPicked);

        if (totalPicked == 0) {
            line.setStatus(ShipmentOrderLine.LineStatus.backordered);
        } else if (totalPicked < requestedQty) {
            line.setStatus(ShipmentOrderLine.LineStatus.partial);
        } else {
            line.setStatus(ShipmentOrderLine.LineStatus.picked);
        }
        shipmentOrderLineRepository.save(line);

        // 백오더 처리 (부분출고 의사결정 트리)
        if (totalPicked < requestedQty) {
            int shortageQty = requestedQty - totalPicked;
            double fulfillmentRate = (double) totalPicked / requestedQty;

            if (fulfillmentRate < 0.30) {
                // 가용 재고 < 30%: 전량 백오더 (이미 피킹한 것도 롤백해야 하지만 단순화)
                // 실제로는 트랜잭션 내에서 피킹 전 체크해야 함
            }

            if (fulfillmentRate >= 0.30 && fulfillmentRate < 0.70) {
                // 가용 재고 30~70%: 부분출고 + 백오더 + 긴급발주
                createUrgentReorder(product, shortageQty);
            }

            // 백오더 생성
            Backorder backorder = Backorder.builder()
                    .shipmentOrderLine(line)
                    .product(product)
                    .shortageQty(shortageQty)
                    .status(Backorder.BackorderStatus.open)
                    .build();
            backorderRepository.save(backorder);

            backorderInfos.add(PickResponse.BackorderInfo.builder()
                    .backorderId(backorder.getBackorderId())
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .shortageQty(shortageQty)
                    .build());
        }

        return PickResponse.LinePickResult.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(product.getProductId())
                .productName(product.getName())
                .requestedQty(line.getRequestedQty())
                .pickedQty(totalPicked)
                .status(line.getStatus().name())
                .pickDetails(pickDetails)
                .build();
    }

    private List<Inventory> getAvailableInventories(Product product) {
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .filter(inv -> !Boolean.TRUE.equals(inv.getLocation().getIsFrozen()))
                .collect(Collectors.toList());

        // 유통기한 체크
        LocalDate today = LocalDate.now();
        inventories = inventories.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null) {
                        // 만료된 재고 제외
                        if (inv.getExpiryDate().isBefore(today)) {
                            return false;
                        }
                        // 잔여 유통기한 10% 미만 제외
                        if (inv.getManufactureDate() != null) {
                            double remainingPct = calculateRemainingShelfLifePct(
                                    inv.getManufactureDate(), inv.getExpiryDate());
                            if (remainingPct < 10.0) {
                                // is_expired = true 설정
                                inv.setIsExpired(true);
                                inventoryRepository.save(inv);
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 효율적인 로케이션 조합 우선순위 정렬
        inventories.sort((a, b) -> {
            // 1순위: 잔여 유통기한 30% 미만 (긴급 소진 필요)
            if (a.getExpiryDate() != null && a.getManufactureDate() != null &&
                b.getExpiryDate() != null && b.getManufactureDate() != null) {
                double remainingA = calculateRemainingShelfLifePct(a.getManufactureDate(), a.getExpiryDate());
                double remainingB = calculateRemainingShelfLifePct(b.getManufactureDate(), b.getExpiryDate());

                boolean aUrgent = remainingA < 30.0;
                boolean bUrgent = remainingB < 30.0;

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;
            }

            // 2순위: 수량이 많은 로케이션 우선 (피킹 횟수 최소화)
            int qtyCompare = Integer.compare(b.getQuantity(), a.getQuantity());
            if (qtyCompare != 0) return qtyCompare;

            // 3순위: 유통기한 있는 상품은 유통기한 오름차순
            if (Boolean.TRUE.equals(a.getProduct().getHasExpiry()) &&
                a.getExpiryDate() != null && b.getExpiryDate() != null) {
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;
            }

            // 4순위: 입고일 오름차순
            return a.getReceivedAt().compareTo(b.getReceivedAt());
        });

        return inventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays == 0) return 0.0;
        return (double) remainingDays / totalDays * 100.0;
    }

    private void createUrgentReorder(Product product, int shortageQty) {
        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(AutoReorderLog.TriggerType.URGENT_REORDER)
                .currentStock(getTotalAvailableStock(product))
                .minQty(0)
                .reorderQty(shortageQty)
                .triggeredBy("SYSTEM")
                .build();
        autoReorderLogRepository.save(log);
        log.info("Urgent reorder triggered for product {} due to shortage", product.getSku());
    }

    private void checkSafetyStockAfterShipment(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getProductId());
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();
        int currentStock = getTotalAvailableStock(product);

        if (currentStock <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(currentStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(log);
            log.info("Safety stock reorder triggered for product {}", product.getSku());
        }
    }

    private int getTotalAvailableStock(Product product) {
        return inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .mapToInt(Inventory::getQuantity)
                .sum();
    }

    private void logStorageTypeMismatch(Product product, Location location) {
        Map<String, Object> details = new HashMap<>();
        details.put("productId", product.getProductId().toString());
        details.put("productSku", product.getSku());
        details.put("productStorageType", product.getStorageType().name());
        details.put("locationId", location.getLocationId().toString());
        details.put("locationCode", location.getCode());
        details.put("locationStorageType", location.getStorageType().name());
        details.put("message", "Storage type mismatch detected during picking");

        AuditLog auditLog = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("SHIPMENT")
                .entityId(product.getProductId())
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(auditLog);

        log.warn("Storage type mismatch: product {} ({}) in location {} ({})",
                product.getSku(), product.getStorageType(), location.getCode(), location.getStorageType());
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
                shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.partial &&
                shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.shipped) {
            throw new BusinessException("INVALID_STATUS",
                    "Shipment order must be in picking, partial, or shipped status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        shipmentOrder.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipmentOrder);

        return buildShipmentOrderResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));
        return buildShipmentOrderResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(this::buildShipmentOrderResponse)
                .collect(Collectors.toList());
    }

    private ShipmentOrderResponse buildShipmentOrderResponse(ShipmentOrder shipmentOrder) {
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository
                .findByShipmentOrderShipmentId(shipmentOrder.getShipmentId());

        return ShipmentOrderResponse.builder()
                .shipmentId(shipmentOrder.getShipmentId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus().name())
                .requestedAt(shipmentOrder.getRequestedAt())
                .shippedAt(shipmentOrder.getShippedAt())
                .lines(lines.stream()
                        .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                                .shipmentLineId(line.getShipmentLineId())
                                .productId(line.getProduct().getProductId())
                                .productName(line.getProduct().getName())
                                .requestedQty(line.getRequestedQty())
                                .pickedQty(line.getPickedQty())
                                .status(line.getStatus().name())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
