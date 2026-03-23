package com.wms.service;

import com.wms.dto.ShipmentOrderListResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
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
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // HAZMAT과 FRESH 상품 분리 체크
        Map<Boolean, List<ShipmentOrderRequest.ShipmentOrderLineRequest>> partitioned =
            request.getLines().stream()
                .collect(Collectors.partitioningBy(line -> {
                    Product product = productRepository.findById(line.getProductId())
                        .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));
                    return product.getCategory() == Product.ProductCategory.HAZMAT;
                }));

        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = partitioned.get(true);
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = partitioned.get(false);

        // HAZMAT과 FRESH 혼재 체크
        boolean hasFresh = nonHazmatLines.stream().anyMatch(line -> {
            Product product = productRepository.findById(line.getProductId()).orElse(null);
            return product != null && product.getCategory() == Product.ProductCategory.FRESH;
        });

        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT과 FRESH가 혼재된 경우 분리 출고
            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder mainOrder = createShipmentOrderInternal(request.getShipmentNumber(),
                request.getCustomerName(), nonHazmatLines);

            // HAZMAT 출고 지시서 별도 생성
            String hazmatShipmentNumber = request.getShipmentNumber() + "-HAZMAT";
            ShipmentOrder hazmatOrder = createShipmentOrderInternal(hazmatShipmentNumber,
                request.getCustomerName(), hazmatLines);

            log.info("Separated HAZMAT order {} from main order {}",
                hazmatOrder.getShipmentNumber(), mainOrder.getShipmentNumber());

            return toResponse(mainOrder);
        } else {
            // 분리 불필요, 정상 생성
            ShipmentOrder order = createShipmentOrderInternal(request.getShipmentNumber(),
                request.getCustomerName(), request.getLines());
            return toResponse(order);
        }
    }

    private ShipmentOrder createShipmentOrderInternal(String shipmentNumber, String customerName,
                                                      List<ShipmentOrderRequest.ShipmentOrderLineRequest> lineRequests) {
        ShipmentOrder order = ShipmentOrder.builder()
            .shipmentNumber(shipmentNumber)
            .customerName(customerName)
            .status(ShipmentOrder.ShipmentStatus.pending)
            .requestedDate(Instant.now())
            .build();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : lineRequests) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                .shipmentOrder(order)
                .product(product)
                .requestedQuantity(lineReq.getRequestedQuantity())
                .pickedQuantity(0)
                .status(ShipmentOrderLine.LineStatus.pending)
                .build();

            order.getLines().add(line);
        }

        return shipmentOrderRepository.save(order);
    }

    @Transactional
    public ShipmentOrderResponse pickShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("SHIPMENT_ORDER_NOT_FOUND", "Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("INVALID_STATUS", "Shipment order must be in pending status to pick");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.picking);

        for (ShipmentOrderLine line : order.getLines()) {
            pickLine(line);
        }

        // 전체 주문 상태 업데이트
        updateShipmentOrderStatus(order);

        ShipmentOrder savedOrder = shipmentOrderRepository.save(order);
        return toResponse(savedOrder);
    }

    private void pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQuantity();

        // 가용 재고 조회 (FIFO/FEFO 적용)
        List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

        int totalPicked = 0;
        int remainingQty = requestedQty;

        // HAZMAT max_pick_qty 체크
        if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                throw new BusinessException("MAX_PICK_QTY_EXCEEDED",
                    String.format("Requested quantity %d exceeds max pick qty %d for HAZMAT product %s",
                        requestedQty, product.getMaxPickQty(), product.getSku()));
            }
        }

        for (Inventory inventory : availableInventories) {
            if (remainingQty <= 0) break;

            // 실사 동결 로케이션 체크
            if (inventory.getLocation().getIsFrozen()) {
                continue;
            }

            // HAZMAT zone 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                if (inventory.getLocation().getZone() != Location.Zone.HAZMAT) {
                    continue;
                }
            }

            // 보관 유형 불일치 경고
            if (inventory.getLocation().getStorageType() != product.getStorageType()) {
                recordAuditLog("STORAGE_TYPE_MISMATCH", inventory.getId(),
                    String.format("Storage type mismatch: product %s (%s) in location %s (%s)",
                        product.getSku(), product.getStorageType(),
                        inventory.getLocation().getCode(), inventory.getLocation().getStorageType()));
            }

            int pickQty = Math.min(remainingQty, inventory.getQuantity());

            // 재고 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // 로케이션 현재 수량 차감
            Location location = inventory.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() - pickQty);
            locationRepository.save(location);

            totalPicked += pickQty;
            remainingQty -= pickQty;
        }

        line.setPickedQuantity(totalPicked);

        // 부분출고 의사결정 트리
        if (totalPicked < requestedQty) {
            double fulfillmentRate = (double) totalPicked / requestedQty;
            int backorderQty = requestedQty - totalPicked;

            if (fulfillmentRate >= 0.7) {
                // 70% 이상: 부분출고 + 백오더
                line.setStatus(ShipmentOrderLine.LineStatus.partial);
                createBackorder(line, backorderQty);
            } else if (fulfillmentRate >= 0.3) {
                // 30~70%: 부분출고 + 백오더 + 긴급발주
                line.setStatus(ShipmentOrderLine.LineStatus.partial);
                createBackorder(line, backorderQty);
                recordEmergencyReorder(product, backorderQty);
            } else {
                // 30% 미만: 전량 백오더 (부분출고 안 함)
                // 피킹한 재고를 다시 원복
                rollbackPicking(product, totalPicked);
                line.setPickedQuantity(0);
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                createBackorder(line, requestedQty);
            }
        } else {
            line.setStatus(ShipmentOrderLine.LineStatus.picked);
        }

        shipmentOrderLineRepository.save(line);
    }

    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        List<Inventory> inventories = inventoryRepository.findAvailableInventoryForProduct(product.getId());

        // 만료된 재고 및 잔여율 < 10% 재고 제외 및 expired 플래그 설정
        LocalDate today = LocalDate.now();
        inventories.removeIf(inv -> {
            if (inv.getExpiryDate() != null) {
                // 만료된 재고
                if (inv.getExpiryDate().isBefore(today)) {
                    inv.setExpired(true);
                    inventoryRepository.save(inv);
                    return true;
                }

                // 잔여율 < 10% 재고
                if (inv.getManufactureDate() != null) {
                    double remainingPct = calculateRemainingShelfLifePct(
                        inv.getManufactureDate(), inv.getExpiryDate(), today);
                    if (remainingPct < 10) {
                        inv.setExpired(true);
                        inventoryRepository.save(inv);
                        return true;
                    }
                }
            }
            return false;
        });

        // 효율적인 로케이션 조합을 위한 정렬
        // 1순위: 잔여율 < 30% (유통기한 관리 상품만)
        // 2순위: 수량이 큰 로케이션 우선 (피킹 횟수 최소화)
        // 3순위: 유통기한 빠른 순 (유통기한 관리 상품만)
        inventories.sort((i1, i2) -> {
            LocalDate today1 = LocalDate.now();

            // 1순위: 잔여율 < 30% 우선 처리 (유통기한 관리 상품만)
            if (product.getRequiresExpiry()) {
                double pct1 = i1.getManufactureDate() != null && i1.getExpiryDate() != null ?
                    calculateRemainingShelfLifePct(i1.getManufactureDate(), i1.getExpiryDate(), today1) : 100;
                double pct2 = i2.getManufactureDate() != null && i2.getExpiryDate() != null ?
                    calculateRemainingShelfLifePct(i2.getManufactureDate(), i2.getExpiryDate(), today1) : 100;

                boolean i1Priority = pct1 < 30;
                boolean i2Priority = pct2 < 30;

                if (i1Priority && !i2Priority) return -1;
                if (!i1Priority && i2Priority) return 1;
            }

            // 2순위: 수량이 큰 로케이션 우선 (효율성)
            int qtyCompare = Integer.compare(i2.getQuantity(), i1.getQuantity());
            if (qtyCompare != 0) return qtyCompare;

            // 3순위: 유통기한 빠른 순 (유통기한 관리 상품만)
            if (product.getRequiresExpiry() && i1.getExpiryDate() != null && i2.getExpiryDate() != null) {
                return i1.getExpiryDate().compareTo(i2.getExpiryDate());
            }

            // 마지막: 입고일 빠른 순
            return i1.getReceivedAt().compareTo(i2.getReceivedAt());
        });

        return inventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalShelfLife <= 0) return 0;
        return ((double) remainingShelfLife / totalShelfLife) * 100;
    }

    private void createBackorder(ShipmentOrderLine line, int quantity) {
        Backorder backorder = Backorder.builder()
            .shipmentOrderLine(line)
            .product(line.getProduct())
            .quantity(quantity)
            .status(Backorder.BackorderStatus.open)
            .build();
        backorderRepository.save(backorder);
    }

    private void recordEmergencyReorder(Product product, int quantity) {
        AutoReorderLog log = AutoReorderLog.builder()
            .product(product)
            .triggerReason("EMERGENCY_REORDER")
            .reorderQty(quantity)
            .triggeredAt(Instant.now())
            .build();
        autoReorderLogRepository.save(log);
        log.info("Emergency reorder triggered for product {} with quantity {}", product.getSku(), quantity);
    }

    private void rollbackPicking(Product product, int quantity) {
        // 30% 미만일 때 피킹한 재고를 원복하는 로직
        // 실제로는 더 복잡하지만, 간단히 로그만 남김
        log.warn("Rollback picking for product {} with quantity {} (30% threshold not met)",
            product.getSku(), quantity);
        // TODO: 실제 원복 로직 구현 필요
    }

    private void recordAuditLog(String action, UUID entityId, String details) {
        AuditLog auditLog = AuditLog.builder()
            .entityType("INVENTORY")
            .entityId(entityId)
            .action(action)
            .details(details)
            .performedBy("SYSTEM")
            .performedAt(Instant.now())
            .build();
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("SHIPMENT_ORDER_NOT_FOUND", "Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
            order.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new BusinessException("INVALID_STATUS", "Shipment order must be in picking or partial status to ship");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        order.setShippedAt(Instant.now());

        ShipmentOrder savedOrder = shipmentOrderRepository.save(order);
        return toResponse(savedOrder);
    }

    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);
        if (rule == null) {
            return; // 안전재고 규칙 없음
        }

        int totalAvailable = inventoryRepository.getTotalAvailableQuantity(product.getId());

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason("SAFETY_STOCK_TRIGGER")
                .reorderQty(rule.getReorderQty())
                .triggeredAt(Instant.now())
                .build();
            autoReorderLogRepository.save(log);
            log.info("Safety stock trigger for product {} - current: {}, min: {}, reorder: {}",
                product.getSku(), totalAvailable, rule.getMinQty(), rule.getReorderQty());
        }
    }

    private void updateShipmentOrderStatus(ShipmentOrder order) {
        boolean allPicked = true;
        boolean anyPicked = false;

        for (ShipmentOrderLine line : order.getLines()) {
            if (line.getPickedQuantity() < line.getRequestedQuantity()) {
                allPicked = false;
            }
            if (line.getPickedQuantity() > 0) {
                anyPicked = true;
            }
        }

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.picking);
        } else if (anyPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("SHIPMENT_ORDER_NOT_FOUND", "Shipment order not found"));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderListResponse> getShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
            .map(this::toListResponse)
            .collect(Collectors.toList());
    }

    private ShipmentOrderResponse toResponse(ShipmentOrder order) {
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = order.getLines().stream()
            .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                .id(line.getId())
                .productId(line.getProduct().getId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .requestedQuantity(line.getRequestedQuantity())
                .pickedQuantity(line.getPickedQuantity())
                .status(line.getStatus().name())
                .build())
            .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
            .id(order.getId())
            .shipmentNumber(order.getShipmentNumber())
            .customerName(order.getCustomerName())
            .status(order.getStatus().name())
            .requestedDate(order.getRequestedDate())
            .shippedAt(order.getShippedAt())
            .lines(lineResponses)
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    private ShipmentOrderListResponse toListResponse(ShipmentOrder order) {
        return ShipmentOrderListResponse.builder()
            .id(order.getId())
            .shipmentNumber(order.getShipmentNumber())
            .customerName(order.getCustomerName())
            .status(order.getStatus().name())
            .requestedDate(order.getRequestedDate())
            .shippedAt(order.getShippedAt())
            .createdAt(order.getCreatedAt())
            .build();
    }
}
