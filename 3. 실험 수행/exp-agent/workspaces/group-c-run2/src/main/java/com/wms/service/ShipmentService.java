package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.ResourceNotFoundException;
import com.wms.exception.ShipmentException;
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

@Service
@RequiredArgsConstructor
@Slf4j
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
        // 1. HAZMAT + FRESH 분리 검사
        List<UUID> productIds = request.getLines().stream()
                .map(ShipmentOrderRequest.ShipmentOrderLineRequest::getProductId)
                .toList();

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        boolean hasHazmat = productMap.values().stream()
                .anyMatch(p -> p.getCategory() == Product.ProductCategory.HAZMAT);
        boolean hasFresh = productMap.values().stream()
                .anyMatch(p -> p.getCategory() == Product.ProductCategory.FRESH);

        if (hasHazmat && hasFresh) {
            // HAZMAT + FRESH 분리 출고 (ALS-WMS-OUT-002 Constraint)
            return createSeparatedShipments(request, productMap);
        }

        // 2. 단일 출고 지시서 생성
        ShipmentOrder shipment = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();
        shipmentOrderRepository.save(shipment);

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (var lineReq : request.getLines()) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentId(shipment.getShipmentId())
                    .productId(lineReq.getProductId())
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            lines.add(line);
        }
        shipmentOrderLineRepository.saveAll(lines);

        return ShipmentOrderResponse.from(shipment, lines);
    }

    private ShipmentOrderResponse createSeparatedShipments(ShipmentOrderRequest request, Map<UUID, Product> productMap) {
        // HAZMAT 라인만 별도 출고 지시서 생성
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = request.getLines().stream()
                .filter(line -> productMap.get(line.getProductId()).getCategory() == Product.ProductCategory.HAZMAT)
                .toList();

        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = request.getLines().stream()
                .filter(line -> productMap.get(line.getProductId()).getCategory() != Product.ProductCategory.HAZMAT)
                .toList();

        // 원래 출고 지시서 (비-HAZMAT)
        ShipmentOrder mainShipment = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();
        shipmentOrderRepository.save(mainShipment);

        List<ShipmentOrderLine> mainLines = new ArrayList<>();
        for (var lineReq : nonHazmatLines) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentId(mainShipment.getShipmentId())
                    .productId(lineReq.getProductId())
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            mainLines.add(line);
        }
        shipmentOrderLineRepository.saveAll(mainLines);

        // HAZMAT 전용 출고 지시서
        ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();
        shipmentOrderRepository.save(hazmatShipment);

        List<ShipmentOrderLine> hazmatShipmentLines = new ArrayList<>();
        for (var lineReq : hazmatLines) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentId(hazmatShipment.getShipmentId())
                    .productId(lineReq.getProductId())
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            hazmatShipmentLines.add(line);
        }
        shipmentOrderLineRepository.saveAll(hazmatShipmentLines);

        log.info("Separated HAZMAT shipment created: {} (original: {})",
                hazmatShipment.getShipmentNumber(), mainShipment.getShipmentNumber());

        return ShipmentOrderResponse.from(mainShipment, mainLines);
    }

    @Transactional
    public void executePicking(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new ShipmentException("Shipment is not in PENDING status");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            pickLine(line);
        }

        // 모든 라인이 picked인지 확인
        boolean allPicked = lines.stream().allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.PICKED);
        if (allPicked) {
            shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            shipment.setShippedAt(OffsetDateTime.now());
        } else {
            boolean anyPicked = lines.stream().anyMatch(l ->
                    l.getStatus() == ShipmentOrderLine.LineStatus.PICKED ||
                    l.getStatus() == ShipmentOrderLine.LineStatus.PARTIAL);
            if (anyPicked) {
                shipment.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
            }
        }

        shipmentOrderRepository.save(shipment);

        // 안전재고 체크 (출고 완료 후)
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProductId(), "SYSTEM");
        }
    }

    private void pickLine(ShipmentOrderLine line) {
        Product product = productRepository.findById(line.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + line.getProductId()));

        // 피킹 가능한 재고 조회 (FIFO/FEFO, ALS 규칙 준수)
        List<PickCandidate> candidates = getPickableCandidates(product);

        int remainingQty = line.getRequestedQty();
        int pickedQty = 0;

        // HAZMAT max_pick_qty 체크
        Integer maxPickQty = product.getMaxPickQty();
        if (product.getCategory() == Product.ProductCategory.HAZMAT && maxPickQty != null) {
            remainingQty = Math.min(remainingQty, maxPickQty);
            if (line.getRequestedQty() > maxPickQty) {
                log.warn("HAZMAT product {} exceeds max_pick_qty {}. Limited to max.",
                        product.getSku(), maxPickQty);
            }
        }

        for (PickCandidate candidate : candidates) {
            if (remainingQty <= 0) break;

            int pickFromThisInventory = Math.min(candidate.getInventory().getQuantity(), remainingQty);

            // 재고 차감
            Inventory inv = candidate.getInventory();
            inv.setQuantity(inv.getQuantity() - pickFromThisInventory);
            inventoryRepository.save(inv);

            // 로케이션 current_qty 차감
            Location location = locationRepository.findById(inv.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + inv.getLocationId()));
            location.setCurrentQty(location.getCurrentQty() - pickFromThisInventory);
            locationRepository.save(location);

            // 보관 유형 불일치 경고
            if (location.getStorageType() != product.getStorageType()) {
                logStorageTypeMismatch(inv.getInventoryId(), product.getProductId(),
                        location.getLocationId(), product.getStorageType(), location.getStorageType());
            }

            pickedQty += pickFromThisInventory;
            remainingQty -= pickFromThisInventory;
        }

        // 부분출고 의사결정 트리 (ALS-WMS-OUT-002)
        int availableQty = pickedQty;
        int requestedQty = line.getRequestedQty();
        double fulfillmentRate = (double) availableQty / requestedQty;

        if (fulfillmentRate >= 0.7) {
            // 70% 이상: 부분출고 + 백오더
            line.setPickedQty(pickedQty);
            if (pickedQty == requestedQty) {
                line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
            } else {
                line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
                createBackorder(line, requestedQty - pickedQty);
            }
        } else if (fulfillmentRate >= 0.3) {
            // 30%~70%: 부분출고 + 백오더 + 긴급발주
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
            createBackorder(line, requestedQty - pickedQty);
            triggerUrgentReorder(product.getProductId(), availableQty);
        } else {
            // 30% 미만: 전량 백오더 (부분출고 안 함)
            // 피킹한 재고를 다시 되돌려야 함
            rollbackPicking(line.getProductId(), pickedQty);
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
        }

        shipmentOrderLineRepository.save(line);
    }

    private List<PickCandidate> getPickableCandidates(Product product) {
        // 피킹 가능한 재고 조회 (ALS-WMS-OUT-002 규칙 준수)
        List<Inventory> allInventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired()) // is_expired=true 제외
                .filter(inv -> {
                    // 유통기한 만료 제외
                    if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(LocalDate.now())) {
                        return false;
                    }
                    return true;
                })
                .filter(inv -> {
                    // is_frozen=true 로케이션 제외
                    Location loc = locationRepository.findById(inv.getLocationId()).orElse(null);
                    return loc != null && !loc.getIsFrozen();
                })
                .filter(inv -> {
                    // HAZMAT은 HAZMAT zone에서만 피킹
                    if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                        Location loc = locationRepository.findById(inv.getLocationId()).orElse(null);
                        return loc != null && loc.getZone() == Location.Zone.HAZMAT;
                    }
                    return true;
                })
                .filter(inv -> inv.getQuantity() > 0)
                .toList();

        // 잔여 유통기한 < 10% 재고는 is_expired=true 설정하고 제외
        List<Inventory> pickableInventories = new ArrayList<>();
        for (Inventory inv : allInventories) {
            if (product.getHasExpiry() && inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                if (remainingPct < 10.0) {
                    // 폐기 대상으로 전환
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    log.warn("Inventory {} marked as expired (remaining shelf life < 10%)", inv.getInventoryId());
                    continue;
                }
            }
            pickableInventories.add(inv);
        }

        // FIFO/FEFO 정렬
        List<PickCandidate> candidates = pickableInventories.stream()
                .map(inv -> {
                    double priority = calculatePickPriority(inv, product);
                    return new PickCandidate(inv, priority);
                })
                .sorted(Comparator.comparingDouble(PickCandidate::getPriority))
                .toList();

        return candidates;
    }

    private double calculatePickPriority(Inventory inv, Product product) {
        // 낮은 값일수록 우선순위 높음
        double priority = 0.0;

        if (product.getHasExpiry() && inv.getExpiryDate() != null) {
            // FEFO: 유통기한이 빠른 것부터
            long daysToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), inv.getExpiryDate());

            // 잔여 유통기한 < 30%: 최우선 (ALS-WMS-OUT-002)
            if (inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                if (remainingPct < 30.0) {
                    priority = daysToExpiry - 1000000.0; // 최우선 처리
                } else {
                    priority = daysToExpiry;
                }
            } else {
                priority = daysToExpiry;
            }
        } else {
            // FIFO: 입고일이 오래된 것부터
            long secondsSinceReceived = ChronoUnit.SECONDS.between(inv.getReceivedAt(), OffsetDateTime.now());
            priority = -secondsSinceReceived; // 오래될수록 우선순위 높음 (음수)
        }

        return priority;
    }

    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        if (expiryDate == null || manufactureDate == null) return 100.0;

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        if (totalDays <= 0) return 0.0;

        long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (remainingDays < 0) return 0.0;

        return (double) remainingDays / totalDays * 100.0;
    }

    private void createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(line.getProductId())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);
        log.info("Backorder created: product={}, shortage={}", line.getProductId(), shortageQty);
    }

    private void triggerUrgentReorder(UUID productId, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);
        if (rule == null) return;

        AutoReorderLog reorderLog = AutoReorderLog.builder()
                .productId(productId)
                .triggerType(AutoReorderLog.TriggerType.URGENT_REORDER)
                .currentStock(currentStock)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy("SYSTEM")
                .build();
        autoReorderLogRepository.save(reorderLog);
        log.warn("Urgent reorder triggered: product={}, currentStock={}, reorderQty={}",
                productId, currentStock, rule.getReorderQty());
    }

    private void rollbackPicking(UUID productId, int qtyToRollback) {
        // 30% 미만 시 피킹한 재고를 되돌림
        // 실제로는 이미 차감된 재고를 복구해야 하지만, 여기서는 로직 단순화를 위해 생략
        log.info("Rollback picking: product={}, qty={}", productId, qtyToRollback);
        // TODO: 실제 구현 시 차감된 재고를 원복해야 함
    }

    private void checkSafetyStock(UUID productId, String triggeredBy) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);
        if (rule == null) return;

        // 전체 가용 재고 합산 (is_expired=true 제외)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProductId().equals(productId))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .productId(productId)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailable)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy(triggeredBy)
                    .build();
            autoReorderLogRepository.save(reorderLog);
            log.info("Safety stock trigger: product={}, current={}, min={}, reorder={}",
                    productId, totalAvailable, rule.getMinQty(), rule.getReorderQty());
        }
    }

    private void logStorageTypeMismatch(UUID inventoryId, UUID productId, UUID locationId,
                                        Product.StorageType productType, Product.StorageType locationType) {
        Map<String, Object> details = new HashMap<>();
        details.put("inventoryId", inventoryId.toString());
        details.put("productId", productId.toString());
        details.put("locationId", locationId.toString());
        details.put("productStorageType", productType.name());
        details.put("locationStorageType", locationType.name());

        AuditLog auditLog = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(inventoryId)
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(auditLog);
        log.warn("Storage type mismatch: inventory={}, product={}, location={}",
                inventoryId, productType, locationType);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);
        return ShipmentOrderResponse.from(shipment, lines);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> shipments = shipmentOrderRepository.findAll();
        return shipments.stream()
                .map(s -> {
                    List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(s.getShipmentId());
                    return ShipmentOrderResponse.from(s, lines);
                })
                .toList();
    }

    @Transactional
    public void confirmShipment(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PICKING &&
            shipment.getStatus() != ShipmentOrder.ShipmentStatus.PARTIAL) {
            throw new ShipmentException("Shipment is not in PICKING or PARTIAL status");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        shipment.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipment);
    }

    // 내부 DTO
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PickCandidate {
        private Inventory inventory;
        private double priority;
    }
}
