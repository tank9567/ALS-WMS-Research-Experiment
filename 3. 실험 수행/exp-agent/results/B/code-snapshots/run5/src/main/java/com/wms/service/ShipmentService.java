package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
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
@Transactional
public class ShipmentService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 출고 지시서 생성
     */
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // HAZMAT과 FRESH 분리 출고 체크
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 FRESH가 같이 있으면 분리
        boolean needsSeparation = !hazmatLines.isEmpty() && nonHazmatLines.stream().anyMatch(lineReq -> {
            Product product = productRepository.findById(lineReq.getProductId()).orElse(null);
            return product != null && product.getCategory() == Product.ProductCategory.FRESH;
        });

        if (needsSeparation) {
            // HAZMAT 별도 출고 지시서 생성
            if (!hazmatLines.isEmpty()) {
                ShipmentOrderRequest hazmatRequest = ShipmentOrderRequest.builder()
                        .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                        .customerName(request.getCustomerName())
                        .requestedAt(request.getRequestedAt())
                        .lines(hazmatLines)
                        .build();
                createShipmentOrderInternal(hazmatRequest);
            }

            // 나머지 출고 지시서 생성
            if (!nonHazmatLines.isEmpty()) {
                ShipmentOrderRequest nonHazmatRequest = ShipmentOrderRequest.builder()
                        .shipmentNumber(request.getShipmentNumber())
                        .customerName(request.getCustomerName())
                        .requestedAt(request.getRequestedAt())
                        .lines(nonHazmatLines)
                        .build();
                return createShipmentOrderInternal(nonHazmatRequest);
            } else {
                throw new BusinessException("VALIDATION_ERROR", "Shipment order has only HAZMAT items, which are separated");
            }
        } else {
            return createShipmentOrderInternal(request);
        }
    }

    /**
     * 출고 지시서 생성 (내부 메서드)
     */
    private ShipmentOrderResponse createShipmentOrderInternal(ShipmentOrderRequest request) {
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : OffsetDateTime.now())
                .status(ShipmentOrder.ShipmentStatus.pending)
                .build();

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipmentOrder)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.pending)
                    .build();

            lines.add(line);
        }

        shipmentOrder.setLines(lines);
        ShipmentOrder savedOrder = shipmentOrderRepository.save(shipmentOrder);

        return mapToResponse(savedOrder, null, null);
    }

    /**
     * 피킹 실행
     */
    public ShipmentOrderResponse pickShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("INVALID_STATUS", "Shipment order is not in pending status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);

        List<ShipmentOrderResponse.PickDetail> allPickDetails = new ArrayList<>();
        List<ShipmentOrderResponse.BackorderInfo> allBackorders = new ArrayList<>();

        for (ShipmentOrderLine line : shipmentOrder.getLines()) {
            Product product = line.getProduct();
            int requestedQty = line.getRequestedQty();

            // HAZMAT 상품 최대 피킹 수량 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
                if (requestedQty > product.getMaxPickQty()) {
                    requestedQty = product.getMaxPickQty();
                    // 나머지는 별도 처리 필요 (여기서는 백오더로 처리)
                }
            }

            // 피킹 가능한 재고 조회 (FIFO/FEFO 적용)
            List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

            int remainingQty = requestedQty;
            int totalPickedQty = 0;

            for (Inventory inventory : availableInventories) {
                if (remainingQty == 0) break;

                // 실사 동결 로케이션 제외
                if (inventory.getLocation().getIsFrozen()) {
                    continue;
                }

                int pickQty = Math.min(remainingQty, inventory.getQuantity());

                // 재고 차감
                inventory.setQuantity(inventory.getQuantity() - pickQty);
                inventoryRepository.save(inventory);

                // 로케이션 current_qty 차감
                Location location = inventory.getLocation();
                location.setCurrentQty(location.getCurrentQty() - pickQty);
                locationRepository.save(location);

                // 보관 유형 불일치 경고
                if (inventory.getLocation().getStorageType() != product.getStorageType()) {
                    recordAuditLog("STORAGE_TYPE_MISMATCH", "SHIPMENT", shipmentId,
                            Map.of("product_id", product.getProductId().toString(),
                                    "location_id", location.getLocationId().toString(),
                                    "product_storage_type", product.getStorageType().toString(),
                                    "location_storage_type", location.getStorageType().toString()),
                            "SYSTEM");
                }

                allPickDetails.add(ShipmentOrderResponse.PickDetail.builder()
                        .productId(product.getProductId())
                        .productSku(product.getSku())
                        .locationId(location.getLocationId())
                        .locationCode(location.getCode())
                        .pickedQty(pickQty)
                        .lotNumber(inventory.getLotNumber())
                        .build());

                remainingQty -= pickQty;
                totalPickedQty += pickQty;
            }

            // 부분출고 의사결정 트리
            int availableQty = totalPickedQty;
            double availableRatio = (double) availableQty / requestedQty;

            if (availableQty >= requestedQty) {
                // 전량 피킹
                line.setPickedQty(totalPickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.picked);
            } else if (availableRatio >= 0.7) {
                // 부분출고 + 백오더
                line.setPickedQty(totalPickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.partial);

                Backorder backorder = createBackorder(line, requestedQty - totalPickedQty);
                allBackorders.add(mapBackorderToInfo(backorder));
            } else if (availableRatio >= 0.3) {
                // 부분출고 + 백오더 + 긴급발주
                line.setPickedQty(totalPickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.partial);

                Backorder backorder = createBackorder(line, requestedQty - totalPickedQty);
                allBackorders.add(mapBackorderToInfo(backorder));

                // 긴급발주 트리거
                recordAutoReorder(product, AutoReorderLog.TriggerType.URGENT_REORDER, "SYSTEM");
            } else {
                // 전량 백오더 (부분출고 안 함)
                // 이미 피킹한 수량 롤백
                for (ShipmentOrderResponse.PickDetail detail : allPickDetails) {
                    if (detail.getProductId().equals(product.getProductId())) {
                        Inventory inv = inventoryRepository.findByProductAndLocationAndLotNumber(
                                        product,
                                        locationRepository.findById(detail.getLocationId()).orElse(null),
                                        detail.getLotNumber())
                                .orElse(null);
                        if (inv != null) {
                            inv.setQuantity(inv.getQuantity() + detail.getPickedQty());
                            inventoryRepository.save(inv);

                            Location loc = inv.getLocation();
                            loc.setCurrentQty(loc.getCurrentQty() + detail.getPickedQty());
                            locationRepository.save(loc);
                        }
                    }
                }
                allPickDetails.removeIf(detail -> detail.getProductId().equals(product.getProductId()));

                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);

                Backorder backorder = createBackorder(line, requestedQty);
                allBackorders.add(mapBackorderToInfo(backorder));
            }

            shipmentOrderLineRepository.save(line);
        }

        // 전체 상태 업데이트
        boolean allPicked = shipmentOrder.getLines().stream()
                .allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.picked);
        boolean anyPicked = shipmentOrder.getLines().stream()
                .anyMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.picked || l.getStatus() == ShipmentOrderLine.LineStatus.partial);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        } else if (anyPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.partial);
        } else {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.pending);
        }

        shipmentOrderRepository.save(shipmentOrder);

        return mapToResponse(shipmentOrder, allPickDetails, allBackorders);
    }

    /**
     * 출고 확정
     */
    public ShipmentOrderResponse shipShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
            shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new BusinessException("INVALID_STATUS", "Shipment order cannot be shipped in current status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        shipmentOrder.setShippedAt(OffsetDateTime.now());

        shipmentOrderRepository.save(shipmentOrder);

        return mapToResponse(shipmentOrder, null, null);
    }

    /**
     * 출고 상세 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Shipment order not found"));

        return mapToResponse(shipmentOrder, null, null);
    }

    /**
     * 출고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> orders = shipmentOrderRepository.findAll();
        return orders.stream()
                .map(order -> mapToResponse(order, null, null))
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 피킹 가능한 재고 조회 (효율적 로케이션 조합 방식)
     * 요청 수량을 최소한의 로케이션 이동으로 채울 수 있도록 정렬
     */
    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        // 1. 기본 조건: 수량 > 0, 만료 안 됨, is_expired = false
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().equals(product))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> {
                    // 유통기한 지난 재고 제외
                    if (inv.getExpiryDate() != null) {
                        return !inv.getExpiryDate().isBefore(LocalDate.now());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 2. HAZMAT은 HAZMAT zone만
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            inventories = inventories.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 3. 잔여 유통기한 < 10% 제외 및 is_expired 표시
        inventories = inventories.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                        if (remainingPct < 10) {
                            inv.setIsExpired(true);
                            inventoryRepository.save(inv);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 4. 효율적 피킹을 위한 정렬 (최소 로케이션 이동)
        // 우선순위:
        // (1) 유통기한 임박 재고 (잔여율 < 30%) 최우선
        // (2) 수량이 많은 로케이션 우선 (단일 로케이션에서 최대한 많이 피킹)
        // (3) 동일 수량이면 유통기한 빠른 순
        inventories.sort((a, b) -> {
            boolean aUrgent = false;
            boolean bUrgent = false;
            double aPct = 100.0;
            double bPct = 100.0;

            // 유통기한 임박 여부 계산
            if (a.getExpiryDate() != null && a.getManufactureDate() != null) {
                aPct = calculateRemainingShelfLifePct(a.getExpiryDate(), a.getManufactureDate());
                aUrgent = aPct < 30;
            }
            if (b.getExpiryDate() != null && b.getManufactureDate() != null) {
                bPct = calculateRemainingShelfLifePct(b.getExpiryDate(), b.getManufactureDate());
                bUrgent = bPct < 30;
            }

            // (1) 유통기한 임박 재고 최우선
            if (aUrgent && !bUrgent) return -1;
            if (!aUrgent && bUrgent) return 1;

            // (2) 수량 많은 로케이션 우선 (내림차순)
            int qtyCompare = Integer.compare(b.getQuantity(), a.getQuantity());
            if (qtyCompare != 0) return qtyCompare;

            // (3) 동일 수량이면 유통기한 빠른 순 (유통기한 있는 경우)
            if (product.getHasExpiry() && a.getExpiryDate() != null && b.getExpiryDate() != null) {
                int expCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expCompare != 0) return expCompare;
            }

            // (4) 그 외에는 입고일 빠른 순
            return a.getReceivedAt().compareTo(b.getReceivedAt());
        });

        return inventories;
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 백오더 생성
     */
    private Backorder createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.open)
                .build();

        return backorderRepository.save(backorder);
    }

    /**
     * 출고 완료 후 안전재고 체크
     */
    private void checkSafetyStockAfterShipment(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct(product);
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        // 전체 가용 재고 계산 (is_expired = false)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().equals(product))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            recordAutoReorder(product, AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER, "SYSTEM");
        }
    }

    /**
     * 자동 재발주 기록
     */
    private void recordAutoReorder(Product product, AutoReorderLog.TriggerType triggerType, String triggeredBy) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct(product);
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        int currentStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().equals(product))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(triggerType)
                .currentStock(currentStock)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy(triggeredBy)
                .build();

        autoReorderLogRepository.save(log);
    }

    /**
     * 감사 로그 기록
     */
    private void recordAuditLog(String eventType, String entityType, UUID entityId,
                                 Map<String, Object> details, String performedBy) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .performedBy(performedBy)
                .build();

        auditLogRepository.save(log);
    }

    /**
     * Entity -> Response DTO 변환
     */
    private ShipmentOrderResponse mapToResponse(ShipmentOrder order,
                                                  List<ShipmentOrderResponse.PickDetail> pickDetails,
                                                  List<ShipmentOrderResponse.BackorderInfo> backorders) {
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = order.getLines().stream()
                .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
                .shipmentId(order.getShipmentId())
                .shipmentNumber(order.getShipmentNumber())
                .customerName(order.getCustomerName())
                .status(order.getStatus().name())
                .requestedAt(order.getRequestedAt())
                .shippedAt(order.getShippedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .lines(lineResponses)
                .pickDetails(pickDetails)
                .backorders(backorders)
                .build();
    }

    /**
     * Backorder -> BackorderInfo 변환
     */
    private ShipmentOrderResponse.BackorderInfo mapBackorderToInfo(Backorder backorder) {
        return ShipmentOrderResponse.BackorderInfo.builder()
                .backorderId(backorder.getBackorderId())
                .productId(backorder.getProduct().getProductId())
                .productSku(backorder.getProduct().getSku())
                .shortageQty(backorder.getShortageQty())
                .status(backorder.getStatus().name())
                .build();
    }
}
