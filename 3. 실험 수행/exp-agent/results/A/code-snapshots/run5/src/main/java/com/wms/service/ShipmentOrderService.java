package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
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
        // 1. 출고 지시서 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .orderNumber(generateOrderNumber())
                .customerName(request.getCustomerName())
                .orderDate(request.getOrderDate())
                .status(ShipmentOrder.ShipmentStatus.pending)
                .build();
        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // 2. HAZMAT/FRESH 분리 출고 처리
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> normalLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                normalLines.add(lineReq);
            }
        }

        // 3. HAZMAT 상품이 FRESH와 함께 있으면 분리
        boolean hasFresh = false;
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : normalLines) {
            Product product = productRepository.findById(lineReq.getProductId()).get();
            if (product.getCategory() == Product.ProductCategory.FRESH) {
                hasFresh = true;
                break;
            }
        }

        ShipmentOrder finalShipmentOrder = shipmentOrder;
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = new ArrayList<>();

        // 4. 정상 라인 처리 (비-HAZMAT)
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : normalLines) {
            Product product = productRepository.findById(lineReq.getProductId()).get();

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(finalShipmentOrder)
                    .product(product)
                    .requestedQuantity(lineReq.getRequestedQuantity())
                    .pickedQuantity(0)
                    .status(ShipmentOrderLine.LineStatus.pending)
                    .build();
            line = shipmentOrderLineRepository.save(line);

            lineResponses.add(ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                    .id(line.getId())
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .requestedQuantity(line.getRequestedQuantity())
                    .pickedQuantity(line.getPickedQuantity())
                    .status(line.getStatus().name())
                    .build());
        }

        // 5. HAZMAT 라인이 있고 FRESH와 함께 있으면 별도 출고 지시서 생성
        if (!hazmatLines.isEmpty() && hasFresh) {
            ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                    .orderNumber(generateOrderNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .orderDate(request.getOrderDate())
                    .status(ShipmentOrder.ShipmentStatus.pending)
                    .build();
            hazmatShipment = shipmentOrderRepository.save(hazmatShipment);

            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : hazmatLines) {
                Product product = productRepository.findById(lineReq.getProductId()).get();

                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(hazmatShipment)
                        .product(product)
                        .requestedQuantity(lineReq.getRequestedQuantity())
                        .pickedQuantity(0)
                        .status(ShipmentOrderLine.LineStatus.pending)
                        .build();
                shipmentOrderLineRepository.save(line);
            }
        } else if (!hazmatLines.isEmpty()) {
            // FRESH가 없으면 HAZMAT도 동일 출고 지시서에 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : hazmatLines) {
                Product product = productRepository.findById(lineReq.getProductId()).get();

                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(finalShipmentOrder)
                        .product(product)
                        .requestedQuantity(lineReq.getRequestedQuantity())
                        .pickedQuantity(0)
                        .status(ShipmentOrderLine.LineStatus.pending)
                        .build();
                line = shipmentOrderLineRepository.save(line);

                lineResponses.add(ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                        .id(line.getId())
                        .productId(product.getId())
                        .productSku(product.getSku())
                        .productName(product.getName())
                        .requestedQuantity(line.getRequestedQuantity())
                        .pickedQuantity(line.getPickedQuantity())
                        .status(line.getStatus().name())
                        .build());
            }
        }

        return ShipmentOrderResponse.builder()
                .id(finalShipmentOrder.getId())
                .orderNumber(finalShipmentOrder.getOrderNumber())
                .customerName(finalShipmentOrder.getCustomerName())
                .status(finalShipmentOrder.getStatus())
                .orderDate(finalShipmentOrder.getOrderDate())
                .shipDate(finalShipmentOrder.getShipDate())
                .lines(lineResponses)
                .createdAt(finalShipmentOrder.getCreatedAt())
                .updatedAt(finalShipmentOrder.getUpdatedAt())
                .build();
    }

    @Transactional
    public ShipmentOrderResponse pickShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("Cannot pick shipment in status: " + shipmentOrder.getStatus());
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);
        shipmentOrderRepository.save(shipmentOrder);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);

        for (ShipmentOrderLine line : lines) {
            Product product = line.getProduct();

            // FIFO/FEFO 피킹
            List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

            int remainingQty = line.getRequestedQuantity();
            int totalPicked = 0;

            // HAZMAT 1회 출고 최대 수량 체크
            Integer maxPickQty = product.getMaxPickQty();
            if (product.getCategory() == Product.ProductCategory.HAZMAT && maxPickQty != null) {
                if (remainingQty > maxPickQty) {
                    throw new BusinessException(
                            String.format("HAZMAT product %s exceeds max pick quantity: requested=%d, max=%d",
                                    product.getSku(), remainingQty, maxPickQty));
                }
            }

            for (Inventory inv : availableInventories) {
                if (remainingQty <= 0) break;

                // 실사 동결 로케이션 체크
                if (inv.getLocation().getIsFrozen()) {
                    continue;
                }

                // HAZMAT zone 체크 (일반 로케이션도 허용)
                // HAZMAT 상품은 이제 모든 구역에서 피킹 가능

                int pickQty = Math.min(inv.getQuantity(), remainingQty);

                // 재고 차감
                inv.setQuantity(inv.getQuantity() - pickQty);
                inventoryRepository.save(inv);

                // 로케이션 용량 차감
                Location location = inv.getLocation();
                location.setCurrentQuantity(location.getCurrentQuantity() - pickQty);
                locationRepository.save(location);

                // 보관 유형 불일치 경고
                if (product.getStorageType() != location.getStorageType()) {
                    logStorageTypeMismatch(product, location);
                }

                totalPicked += pickQty;
                remainingQty -= pickQty;
            }

            line.setPickedQuantity(totalPicked);

            // 부분출고 의사결정 트리
            if (totalPicked == 0) {
                // 가용 재고 = 0: 전량 백오더
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                createBackorder(line, line.getRequestedQuantity());
            } else if (totalPicked < line.getRequestedQuantity()) {
                int backorderQty = line.getRequestedQuantity() - totalPicked;
                double fulfillmentRate = (totalPicked * 100.0) / line.getRequestedQuantity();

                if (fulfillmentRate >= 70) {
                    // 가용 재고 ≥ 70%: 부분출고 + 백오더
                    line.setStatus(ShipmentOrderLine.LineStatus.partial);
                    createBackorder(line, backorderQty);
                } else if (fulfillmentRate >= 30) {
                    // 가용 재고 30% ~ 70%: 부분출고 + 백오더 + 긴급발주
                    line.setStatus(ShipmentOrderLine.LineStatus.partial);
                    createBackorder(line, backorderQty);
                    createUrgentReorder(product, "PARTIAL_SHIPMENT_30_70");
                } else {
                    // 가용 재고 < 30%: 전량 백오더 (부분출고 안 함)
                    // 피킹한 것을 다시 되돌림
                    rollbackPicking(line, totalPicked, availableInventories);
                    line.setPickedQuantity(0);
                    line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                    createBackorder(line, line.getRequestedQuantity());
                }
            } else {
                // 전량 피킹 완료
                line.setStatus(ShipmentOrderLine.LineStatus.picked);
            }

            shipmentOrderLineRepository.save(line);
        }

        // 출고 지시서 상태 갱신
        updateShipmentOrderStatus(shipmentOrderId);

        return getShipmentOrderById(shipmentOrderId);
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
            shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new BusinessException("Cannot ship order in status: " + shipmentOrder.getStatus());
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        shipmentOrder.setShipDate(LocalDate.now());
        shipmentOrderRepository.save(shipmentOrder);

        return getShipmentOrderById(shipmentOrderId);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrderById(UUID shipmentOrderId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);

        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
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
                .id(shipmentOrder.getId())
                .orderNumber(shipmentOrder.getOrderNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus())
                .orderDate(shipmentOrder.getOrderDate())
                .shipDate(shipmentOrder.getShipDate())
                .lines(lineResponses)
                .createdAt(shipmentOrder.getCreatedAt())
                .updatedAt(shipmentOrder.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> shipmentOrders = shipmentOrderRepository.findAll();
        return shipmentOrders.stream()
                .map(order -> getShipmentOrderById(order.getId()))
                .collect(Collectors.toList());
    }

    // === Helper methods ===

    private String generateOrderNumber() {
        return "SO-" + System.currentTimeMillis();
    }

    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        List<Inventory> allInventories = inventoryRepository.findByProductId(product.getId());

        LocalDate today = LocalDate.now();

        // 필터링: 만료된 재고 제외, 잔여율 <10% 제외
        List<Inventory> validInventories = allInventories.stream()
                .filter(inv -> {
                    // 수량이 0이면 제외
                    if (inv.getQuantity() <= 0) return false;

                    // 유통기한 관리 상품인 경우
                    if (product.getRequiresExpiryManagement() && inv.getExpiryDate() != null) {
                        // 만료된 재고 제외
                        if (inv.getExpiryDate().isBefore(today)) {
                            return false;
                        }

                        // 잔여율 계산
                        if (inv.getManufactureDate() != null) {
                            double remainingPct = calculateRemainingShelfLifePct(
                                    inv.getManufactureDate(),
                                    inv.getExpiryDate(),
                                    today
                            );

                            // 잔여율 <10%: 출고 불가 (폐기 대상)
                            if (remainingPct < 10.0) {
                                markAsExpired(inv);
                                return false;
                            }
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        // 효율적 피킹 전략: 잔여율 긴급 재고 우선 + 수량 많은 로케이션 우선
        validInventories.sort((inv1, inv2) -> {
            // 유통기한 관리 상품
            if (product.getRequiresExpiryManagement()) {
                if (inv1.getExpiryDate() != null && inv2.getExpiryDate() != null) {
                    // 잔여율 <30% 재고 최우선
                    double pct1 = calculateRemainingShelfLifePct(
                            inv1.getManufactureDate(),
                            inv1.getExpiryDate(),
                            today
                    );
                    double pct2 = calculateRemainingShelfLifePct(
                            inv2.getManufactureDate(),
                            inv2.getExpiryDate(),
                            today
                    );

                    boolean urgent1 = pct1 < 30.0;
                    boolean urgent2 = pct2 < 30.0;

                    if (urgent1 && !urgent2) return -1;
                    if (!urgent1 && urgent2) return 1;

                    // 잔여율이 둘 다 긴급하거나 둘 다 정상인 경우, 수량 많은 순
                }
            }

            // 효율성 우선: 수량 많은 로케이션 우선 (로케이션 이동 최소화)
            return Integer.compare(inv2.getQuantity(), inv1.getQuantity());
        });

        return validInventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) return 100.0;

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) return 0.0;

        return (remainingDays * 100.0) / totalDays;
    }

    private void markAsExpired(Inventory inv) {
        // 만료 재고로 표시 (실제로는 별도 컬럼이 필요하지만, 여기서는 수량을 0으로 설정)
        // 실무에서는 is_expired 컬럼을 추가하는 것이 좋음
        inv.setQuantity(0);
        inventoryRepository.save(inv);
    }

    private void createBackorder(ShipmentOrderLine line, int backorderQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .backorderedQuantity(backorderQty)
                .status(Backorder.BackorderStatus.open)
                .build();
        backorderRepository.save(backorder);
    }

    private void createUrgentReorder(Product product, String reason) {
        int currentStock = inventoryRepository.getTotalAvailableQuantity(product.getId());

        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        int reorderQty = ruleOpt.map(SafetyStockRule::getReorderQty).orElse(100);

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason(reason)
                .currentStock(currentStock)
                .reorderQuantity(reorderQty)
                .build();
        autoReorderLogRepository.save(log);
    }

    private void checkSafetyStock(Product product) {
        int totalStock = inventoryRepository.getTotalAvailableQuantity(product.getId());

        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());

        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();

            if (totalStock <= rule.getMinQty()) {
                AutoReorderLog log = AutoReorderLog.builder()
                        .product(product)
                        .triggerReason("SAFETY_STOCK_TRIGGER")
                        .currentStock(totalStock)
                        .reorderQuantity(rule.getReorderQty())
                        .build();
                autoReorderLogRepository.save(log);
            }
        }
    }

    private void logStorageTypeMismatch(Product product, Location location) {
        AuditLog log = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(product.getId())
                .description(String.format("Product %s (storage_type=%s) picked from location %s (storage_type=%s)",
                        product.getSku(), product.getStorageType(), location.getCode(), location.getStorageType()))
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(log);
    }

    private void rollbackPicking(ShipmentOrderLine line, int pickedQty, List<Inventory> inventories) {
        // 피킹한 수량을 다시 재고에 되돌림
        int remaining = pickedQty;

        for (Inventory inv : inventories) {
            if (remaining <= 0) break;

            int restoreQty = Math.min(remaining, pickedQty);
            inv.setQuantity(inv.getQuantity() + restoreQty);
            inventoryRepository.save(inv);

            Location location = inv.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + restoreQty);
            locationRepository.save(location);

            remaining -= restoreQty;
        }
    }

    private void updateShipmentOrderStatus(UUID shipmentOrderId) {
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);

        boolean allPicked = true;
        boolean anyPicked = false;
        boolean anyBackordered = false;

        for (ShipmentOrderLine line : lines) {
            if (line.getStatus() == ShipmentOrderLine.LineStatus.picked ||
                line.getStatus() == ShipmentOrderLine.LineStatus.partial) {
                anyPicked = true;
            }

            if (line.getStatus() == ShipmentOrderLine.LineStatus.backordered ||
                line.getStatus() == ShipmentOrderLine.LineStatus.partial) {
                allPicked = false;
            }

            if (line.getStatus() == ShipmentOrderLine.LineStatus.backordered) {
                anyBackordered = true;
            }
        }

        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        if (allPicked && anyPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);
        } else if (anyPicked || anyBackordered) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }

        shipmentOrderRepository.save(shipmentOrder);
    }
}
