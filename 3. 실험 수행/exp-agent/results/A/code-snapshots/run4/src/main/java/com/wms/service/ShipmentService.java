package com.wms.service;

import com.wms.dto.PickingResult;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.enums.*;
import com.wms.exception.WmsException;
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

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // Validate order number uniqueness
        if (shipmentOrderRepository.findByOrderNumber(request.getOrderNumber()).isPresent()) {
            throw new WmsException("Order number already exists: " + request.getOrderNumber());
        }

        // Check for HAZMAT + FRESH separation requirement
        List<Product> products = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findBySku(lineReq.getProductSku())
                    .orElseThrow(() -> new WmsException("Product not found: " + lineReq.getProductSku()));
            products.add(product);
        }

        boolean hasHazmat = products.stream().anyMatch(p -> p.getCategory() == ProductCategory.HAZMAT);
        boolean hasFresh = products.stream().anyMatch(p -> p.getCategory() == ProductCategory.FRESH);

        // If both HAZMAT and FRESH exist, split into separate shipments
        if (hasHazmat && hasFresh) {
            return createSeparateShipments(request, products);
        }

        // Create single shipment order
        return createSingleShipmentOrder(request, products);
    }

    private ShipmentOrderResponse createSingleShipmentOrder(ShipmentOrderRequest request, List<Product> products) {
        ShipmentOrder order = ShipmentOrder.builder()
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .status(ShipmentStatus.PENDING)
                .build();

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < request.getLines().size(); i++) {
            ShipmentOrderRequest.ShipmentLineRequest lineReq = request.getLines().get(i);
            Product product = products.get(i);

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(order)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentLineStatus.PENDING)
                    .build();
            lines.add(line);
        }

        order.setLines(lines);
        ShipmentOrder savedOrder = shipmentOrderRepository.save(order);

        return toResponse(savedOrder);
    }

    private ShipmentOrderResponse createSeparateShipments(ShipmentOrderRequest request, List<Product> products) {
        // Create HAZMAT shipment
        List<ShipmentOrderRequest.ShipmentLineRequest> hazmatLines = new ArrayList<>();
        List<Product> hazmatProducts = new ArrayList<>();

        for (int i = 0; i < request.getLines().size(); i++) {
            Product product = products.get(i);
            if (product.getCategory() == ProductCategory.HAZMAT) {
                hazmatLines.add(request.getLines().get(i));
                hazmatProducts.add(product);
            }
        }

        ShipmentOrderRequest hazmatRequest = ShipmentOrderRequest.builder()
                .orderNumber(request.getOrderNumber() + "-HAZMAT")
                .customerName(request.getCustomerName())
                .lines(hazmatLines)
                .build();

        createSingleShipmentOrder(hazmatRequest, hazmatProducts);

        // Create non-HAZMAT shipment (original order)
        List<ShipmentOrderRequest.ShipmentLineRequest> nonHazmatLines = new ArrayList<>();
        List<Product> nonHazmatProducts = new ArrayList<>();

        for (int i = 0; i < request.getLines().size(); i++) {
            Product product = products.get(i);
            if (product.getCategory() != ProductCategory.HAZMAT) {
                nonHazmatLines.add(request.getLines().get(i));
                nonHazmatProducts.add(product);
            }
        }

        ShipmentOrderRequest nonHazmatRequest = ShipmentOrderRequest.builder()
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .lines(nonHazmatLines)
                .build();

        return createSingleShipmentOrder(nonHazmatRequest, nonHazmatProducts);
    }

    @Transactional
    public PickingResult executePicking(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
                .orElseThrow(() -> new WmsException("Shipment order not found"));

        if (order.getStatus() != ShipmentStatus.PENDING) {
            throw new WmsException("Shipment order is not in PENDING status");
        }

        order.setStatus(ShipmentStatus.PICKING);

        List<PickingResult.PickedLineResult> results = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (ShipmentOrderLine line : order.getLines()) {
            PickingResult.PickedLineResult lineResult = pickLine(line, warnings);
            results.add(lineResult);
        }

        // Update order status based on line statuses
        updateOrderStatusAfterPicking(order);

        shipmentOrderRepository.save(order);

        return PickingResult.builder()
                .shipmentOrderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .pickedLines(results)
                .warnings(warnings)
                .build();
    }

    private PickingResult.PickedLineResult pickLine(ShipmentOrderLine line, List<String> warnings) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // Find eligible inventory
        List<Inventory> eligibleInventory = findEligibleInventory(product, warnings);

        int totalPicked = 0;
        int remainingQty = requestedQty;

        // Apply max_pick_qty constraint for HAZMAT
        if (product.getCategory() == ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                warnings.add(String.format("HAZMAT product %s requested qty %d exceeds max pick qty %d",
                        product.getSku(), requestedQty, product.getMaxPickQty()));
                remainingQty = product.getMaxPickQty();
            }
        }

        // Find most efficient location combination
        List<Inventory> selectedInventory = findOptimalPickingCombination(eligibleInventory, remainingQty);

        // Pick from selected inventory
        for (Inventory inv : selectedInventory) {
            if (remainingQty <= 0) break;

            // Check storage type mismatch warning
            if (!inv.getLocation().getStorageType().name().equals(product.getStorageType().name())) {
                warnings.add(String.format("Storage type mismatch: product %s (%s) found in location %s (%s)",
                        product.getSku(), product.getStorageType(), inv.getLocation().getCode(),
                        inv.getLocation().getStorageType()));
                logAudit("INVENTORY", inv.getId(), "STORAGE_TYPE_MISMATCH",
                        "SYSTEM", Map.of("product", product.getSku(), "location", inv.getLocation().getCode()));
            }

            int pickQty = Math.min(inv.getQuantity(), remainingQty);
            inv.setQuantity(inv.getQuantity() - pickQty);

            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            totalPicked += pickQty;
            remainingQty -= pickQty;
        }

        line.setPickedQty(totalPicked);

        // Determine line status and create backorder if needed
        int backorderedQty = requestedQty - totalPicked;
        ShipmentLineStatus lineStatus;

        if (totalPicked == 0) {
            lineStatus = ShipmentLineStatus.BACKORDERED;
            createBackorder(line, backorderedQty);
        } else if (totalPicked < requestedQty) {
            // Apply partial shipment decision tree
            double fulfillmentRate = (double) totalPicked / requestedQty;

            if (fulfillmentRate >= 0.7) {
                // >= 70%: partial shipment + backorder
                lineStatus = ShipmentLineStatus.PARTIAL;
                createBackorder(line, backorderedQty);
            } else if (fulfillmentRate >= 0.3) {
                // 30-70%: partial shipment + backorder + emergency reorder
                lineStatus = ShipmentLineStatus.PARTIAL;
                createBackorder(line, backorderedQty);
                triggerEmergencyReorder(product, backorderedQty);
            } else {
                // < 30%: full backorder, no partial shipment
                lineStatus = ShipmentLineStatus.BACKORDERED;
                // Revert picked quantity
                line.setPickedQty(0);
                // Restore inventory
                restoreInventory(eligibleInventory, totalPicked);
                createBackorder(line, requestedQty);
                backorderedQty = requestedQty;
                totalPicked = 0;
            }
        } else {
            lineStatus = ShipmentLineStatus.PICKED;
            backorderedQty = 0;
        }

        line.setStatus(lineStatus);
        shipmentOrderLineRepository.save(line);

        return PickingResult.PickedLineResult.builder()
                .lineId(line.getId())
                .productSku(product.getSku())
                .requestedQty(requestedQty)
                .pickedQty(totalPicked)
                .backorderedQty(backorderedQty)
                .status(lineStatus.name())
                .build();
    }

    private List<Inventory> findEligibleInventory(Product product, List<String> warnings) {
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getExpired())
                .collect(Collectors.toList());

        // Filter by zone for HAZMAT - Allow STORAGE zone as well
        if (product.getCategory() == ProductCategory.HAZMAT) {
            allInventory = allInventory.stream()
                    .filter(inv -> inv.getLocation().getZone() == Zone.HAZMAT
                                || inv.getLocation().getZone() == Zone.STORAGE)
                    .collect(Collectors.toList());
        }

        // Filter frozen locations
        allInventory = allInventory.stream()
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .collect(Collectors.toList());

        // Exclude expired inventory
        LocalDate today = LocalDate.now();
        allInventory = allInventory.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                        // Mark as expired
                        inv.setExpired(true);
                        inventoryRepository.save(inv);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Calculate remaining shelf life percentage and exclude < 10%
        allInventory = allInventory.stream()
                .filter(inv -> {
                    if (product.getHasExpiry() && inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(
                                inv.getManufactureDate(), inv.getExpiryDate(), today);
                        if (remainingPct < 10.0) {
                            // Mark as expired (disposal target)
                            inv.setExpired(true);
                            inventoryRepository.save(inv);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        return allInventory;
    }

    private List<Inventory> findOptimalPickingCombination(List<Inventory> eligibleInventory, int requestedQty) {
        if (eligibleInventory.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDate today = LocalDate.now();

        // Priority 1: Try to find single location that covers full quantity
        Optional<Inventory> singleLocation = eligibleInventory.stream()
                .filter(inv -> inv.getQuantity() >= requestedQty)
                .min(Comparator.comparingInt(inv -> {
                    // Prefer exact match, then smallest overage
                    int overage = inv.getQuantity() - requestedQty;
                    return overage == 0 ? 0 : overage + 1000;
                }));

        if (singleLocation.isPresent()) {
            return Collections.singletonList(singleLocation.get());
        }

        // Priority 2: Find minimal location combination
        List<Inventory> sorted = new ArrayList<>(eligibleInventory);

        // Sort by: 1) Expiry priority (< 30% shelf life first), 2) Quantity descending
        sorted.sort((a, b) -> {
            boolean aHasExpiry = a.getExpiryDate() != null && a.getManufactureDate() != null;
            boolean bHasExpiry = b.getExpiryDate() != null && b.getManufactureDate() != null;

            if (aHasExpiry && bHasExpiry) {
                double aPct = calculateRemainingShelfLifePct(a.getManufactureDate(), a.getExpiryDate(), today);
                double bPct = calculateRemainingShelfLifePct(b.getManufactureDate(), b.getExpiryDate(), today);

                boolean aPriority = aPct < 30.0;
                boolean bPriority = bPct < 30.0;

                if (aPriority && !bPriority) return -1;
                if (!aPriority && bPriority) return 1;

                // Within same priority group, prefer larger quantities
                return Integer.compare(b.getQuantity(), a.getQuantity());
            } else if (aHasExpiry) {
                return -1;
            } else if (bHasExpiry) {
                return 1;
            }

            // No expiry: prefer larger quantities for fewer picks
            return Integer.compare(b.getQuantity(), a.getQuantity());
        });

        // Greedy selection: pick largest quantities until fulfilled
        List<Inventory> selected = new ArrayList<>();
        int remaining = requestedQty;

        for (Inventory inv : sorted) {
            if (remaining <= 0) break;
            selected.add(inv);
            remaining -= inv.getQuantity();
        }

        return selected;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (remainingDays * 100.0) / totalDays;
    }

    private void restoreInventory(List<Inventory> inventoryList, int totalPicked) {
        int remaining = totalPicked;
        for (Inventory inv : inventoryList) {
            if (remaining <= 0) break;

            int restored = Math.min(remaining, totalPicked);
            inv.setQuantity(inv.getQuantity() + restored);

            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() + restored);
            locationRepository.save(location);

            remaining -= restored;
        }
    }

    private void createBackorder(ShipmentOrderLine line, int quantity) {
        Backorder backorder = Backorder.builder()
                .orderLine(line)
                .product(line.getProduct())
                .quantity(quantity)
                .status(BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);
    }

    private void triggerEmergencyReorder(Product product, int shortageQty) {
        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason("EMERGENCY_REORDER")
                .requestedQty(shortageQty)
                .build();
        autoReorderLogRepository.save(log);
    }

    private void updateOrderStatusAfterPicking(ShipmentOrder order) {
        long totalLines = order.getLines().size();
        long pickedLines = order.getLines().stream()
                .filter(l -> l.getStatus() == ShipmentLineStatus.PICKED)
                .count();
        long backorderedLines = order.getLines().stream()
                .filter(l -> l.getStatus() == ShipmentLineStatus.BACKORDERED)
                .count();

        if (backorderedLines == totalLines) {
            order.setStatus(ShipmentStatus.PENDING);
        } else if (pickedLines == totalLines) {
            order.setStatus(ShipmentStatus.PICKING);
        } else {
            order.setStatus(ShipmentStatus.PARTIAL);
        }
    }

    @Transactional
    public ShipmentOrderResponse confirmShipment(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
                .orElseThrow(() -> new WmsException("Shipment order not found"));

        if (order.getStatus() != ShipmentStatus.PICKING && order.getStatus() != ShipmentStatus.PARTIAL) {
            throw new WmsException("Cannot confirm shipment in current status: " + order.getStatus());
        }

        order.setStatus(ShipmentStatus.SHIPPED);
        shipmentOrderRepository.save(order);

        return toResponse(order);
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        if (ruleOpt.isEmpty()) return;

        SafetyStockRule rule = ruleOpt.get();

        // Calculate total available inventory (excluding expired)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .requestedQty(rule.getReorderQty())
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
                .orElseThrow(() -> new WmsException("Shipment order not found"));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ShipmentOrderResponse toResponse(ShipmentOrder order) {
        List<ShipmentOrderResponse.ShipmentLineResponse> lineResponses = order.getLines().stream()
                .map(line -> ShipmentOrderResponse.ShipmentLineResponse.builder()
                        .id(line.getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .status(order.getStatus().name())
                .lines(lineResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private void logAudit(String entityType, UUID entityId, String action, String performedBy, Map<String, Object> details) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }
}
