package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
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
    private final CycleCountRepository cycleCountRepository;

    public ShipmentOrderService(
            ShipmentOrderRepository shipmentOrderRepository,
            ShipmentOrderLineRepository shipmentOrderLineRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            BackorderRepository backorderRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository,
            AuditLogRepository auditLogRepository,
            CycleCountRepository cycleCountRepository
    ) {
        this.shipmentOrderRepository = shipmentOrderRepository;
        this.shipmentOrderLineRepository = shipmentOrderLineRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.backorderRepository = backorderRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.cycleCountRepository = cycleCountRepository;
    }

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 출고 지시서 생성
        ShipmentOrder shipmentOrder = new ShipmentOrder();
        shipmentOrder.setShipmentNumber(request.getShipmentNumber());
        shipmentOrder.setCustomerName(request.getCustomerName());
        shipmentOrder.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : Instant.now());
        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.PENDING);

        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // 2. HAZMAT + FRESH 분리 출고 검증 (ALS-WMS-OUT-002 Constraint)
        boolean hasHazmat = false;
        boolean hasFresh = false;

        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hasHazmat = true;
            }
            if (product.getCategory() == Product.ProductCategory.FRESH) {
                hasFresh = true;
            }
        }

        // HAZMAT + FRESH 혼합 시 분리 출고 처리
        if (hasHazmat && hasFresh) {
            return createSeparatedShipmentOrders(request, shipmentOrder);
        }

        // 3. 출고 라인 생성
        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            ShipmentOrderLine line = new ShipmentOrderLine();
            line.setShipmentOrder(shipmentOrder);
            line.setProduct(product);
            line.setRequestedQty(lineReq.getRequestedQty());
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.PENDING);

            lines.add(shipmentOrderLineRepository.save(line));
        }

        return buildResponse(shipmentOrder, lines);
    }

    // HAZMAT + FRESH 분리 출고 처리
    @Transactional
    protected ShipmentOrderResponse createSeparatedShipmentOrders(
            ShipmentOrderRequest request,
            ShipmentOrder originalShipment
    ) {
        // 원본 출고 지시서: FRESH 상품만
        // 신규 출고 지시서: HAZMAT 상품만

        List<ShipmentOrderLine> freshLines = new ArrayList<>();
        ShipmentOrder hazmatShipment = null;
        List<ShipmentOrderLine> hazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                // HAZMAT 상품 → 별도 출고 지시서
                if (hazmatShipment == null) {
                    hazmatShipment = new ShipmentOrder();
                    hazmatShipment.setShipmentNumber(request.getShipmentNumber() + "-HAZMAT");
                    hazmatShipment.setCustomerName(request.getCustomerName());
                    hazmatShipment.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : Instant.now());
                    hazmatShipment.setStatus(ShipmentOrder.ShipmentStatus.PENDING);
                    hazmatShipment = shipmentOrderRepository.save(hazmatShipment);
                }

                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentOrder(hazmatShipment);
                line.setProduct(product);
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.PENDING);
                hazmatLines.add(shipmentOrderLineRepository.save(line));

            } else {
                // 비-HAZMAT 상품 → 원본 출고 지시서
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentOrder(originalShipment);
                line.setProduct(product);
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.PENDING);
                freshLines.add(shipmentOrderLineRepository.save(line));
            }
        }

        // 원본 출고 지시서 응답 반환 (FRESH만 포함)
        return buildResponse(originalShipment, freshLines);
    }

    @Transactional
    public ShipmentOrderResponse pickShipment(UUID shipmentId) {
        // 1. 출고 지시서 조회
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("출고 지시서를 찾을 수 없습니다", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new BusinessException("피킹 가능한 상태가 아닙니다", "INVALID_STATUS");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.PICKING);
        shipmentOrderRepository.save(shipmentOrder);

        // 2. 각 라인 피킹 수행
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            performPicking(line);
        }

        // 3. 출고 상태 업데이트
        boolean allPicked = lines.stream().allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.PICKED);
        boolean anyPicked = lines.stream().anyMatch(l -> l.getPickedQty() > 0);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            shipmentOrder.setShippedAt(Instant.now());
        } else if (anyPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }

        shipmentOrderRepository.save(shipmentOrder);

        // 4. 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProduct());
        }

        return buildResponse(shipmentOrder, lines);
    }

    @Transactional
    protected void performPicking(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // 1. 피킹 가능한 재고 조회 (ALS-WMS-OUT-002 Constraint)
        List<Inventory> pickableInventories = getPickableInventories(product);

        // 2. 가용 재고 계산
        int availableQty = pickableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 부분출고 의사결정 트리 (ALS-WMS-OUT-002 Constraint)
        double availabilityRatio = (double) availableQty / requestedQty;

        if (availableQty == 0) {
            // 전량 백오더
            createBackorder(line, requestedQty);
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);

        } else if (availabilityRatio < 0.3) {
            // 가용 < 30%: 전량 백오더 (부분출고 안 함)
            createBackorder(line, requestedQty);
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);

        } else if (availabilityRatio >= 0.3 && availabilityRatio < 0.7) {
            // 가용 30%~70%: 부분출고 + 백오더 + 긴급발주
            int pickedQty = pickFromInventories(pickableInventories, availableQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);

            int shortageQty = requestedQty - pickedQty;
            if (shortageQty > 0) {
                createBackorder(line, shortageQty);
                createUrgentReorder(product, availableQty - pickedQty);
            }

        } else if (availabilityRatio >= 0.7 && availableQty < requestedQty) {
            // 가용 ≥ 70%: 부분출고 + 백오더
            int pickedQty = pickFromInventories(pickableInventories, availableQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);

            int shortageQty = requestedQty - pickedQty;
            if (shortageQty > 0) {
                createBackorder(line, shortageQty);
            }

        } else {
            // 전량 피킹 가능
            int pickedQty = pickFromInventories(pickableInventories, requestedQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
        }

        shipmentOrderLineRepository.save(line);
    }

    // 피킹 가능한 재고 조회 (FIFO/FEFO + 만료 처리)
    protected List<Inventory> getPickableInventories(Product product) {
        List<Inventory> allInventories = inventoryRepository.findByProduct_ProductIdAndQuantityGreaterThan(
                product.getProductId(), 0);

        List<Inventory> pickable = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Inventory inv : allInventories) {
            // is_expired=true 제외 (ALS-WMS-OUT-002 Constraint)
            if (inv.getIsExpired()) {
                continue;
            }

            // is_frozen=true 로케이션 제외 (ALS-WMS-OUT-002 Constraint)
            if (inv.getLocation().getIsFrozen()) {
                continue;
            }

            // 유통기한 관리 상품의 경우
            if (product.getHasExpiry() && inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                // 유통기한 만료 제외
                if (inv.getExpiryDate().isBefore(today)) {
                    continue;
                }

                // 잔여 유통기한 < 10%: 출고 불가 (폐기 전환)
                double remainingPct = calculateRemainingShelfLifePct(
                        inv.getExpiryDate(), inv.getManufactureDate(), today);

                if (remainingPct < 10.0) {
                    // is_expired=true로 설정 (ALS-WMS-OUT-002 Constraint)
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    continue;
                }
            }

            // HAZMAT 상품은 HAZMAT zone에서만 피킹 (ALS-WMS-OUT-002 Constraint)
            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                if (inv.getLocation().getZone() != Location.Zone.HAZMAT) {
                    continue;
                }
            }

            pickable.add(inv);
        }

        // FIFO/FEFO 정렬 (ALS-WMS-OUT-002 Constraint)
        pickable.sort((a, b) -> {
            if (product.getHasExpiry()) {
                // FEFO 우선
                if (a.getExpiryDate() != null && b.getExpiryDate() != null) {
                    // 잔여율 <30% 최우선 출고
                    LocalDate today = LocalDate.now();
                    double aPct = calculateRemainingShelfLifePct(a.getExpiryDate(), a.getManufactureDate(), today);
                    double bPct = calculateRemainingShelfLifePct(b.getExpiryDate(), b.getManufactureDate(), today);

                    boolean aUrgent = aPct < 30.0;
                    boolean bUrgent = bPct < 30.0;

                    if (aUrgent && !bUrgent) return -1;
                    if (!aUrgent && bUrgent) return 1;

                    // 둘 다 긴급 또는 둘 다 정상이면 FEFO
                    int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                    if (expiryCompare != 0) return expiryCompare;
                }
            }
            // FIFO (received_at 기준)
            return a.getReceivedAt().compareTo(b.getReceivedAt());
        });

        return pickable;
    }

    // 재고에서 피킹 수행 (재고 차감 + 로케이션 차감)
    protected int pickFromInventories(List<Inventory> inventories, int qtyToPick, Product product) {
        int remainingQty = qtyToPick;
        int totalPicked = 0;

        // HAZMAT max_pick_qty 제한 (ALS-WMS-OUT-002 Constraint)
        if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (qtyToPick > product.getMaxPickQty()) {
                qtyToPick = product.getMaxPickQty();
                remainingQty = qtyToPick;
            }
        }

        for (Inventory inv : inventories) {
            if (remainingQty <= 0) break;

            int pickQty = Math.min(inv.getQuantity(), remainingQty);

            // 보관 유형 불일치 경고 (ALS-WMS-OUT-002 Constraint)
            if (inv.getLocation().getStorageType() != product.getStorageType()) {
                createAuditLog("STORAGE_TYPE_MISMATCH", "INVENTORY", inv.getInventoryId(),
                        String.format("{\"locationStorageType\":\"%s\",\"productStorageType\":\"%s\",\"location\":\"%s\"}",
                                inv.getLocation().getStorageType(), product.getStorageType(), inv.getLocation().getCode()),
                        "SYSTEM");
            }

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inventoryRepository.save(inv);

            // 로케이션 차감 (ALS-WMS-OUT-002 Constraint)
            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            remainingQty -= pickQty;
            totalPicked += pickQty;
        }

        return totalPicked;
    }

    // 백오더 생성
    protected void createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = new Backorder();
        backorder.setShipmentLine(line);
        backorder.setProduct(line.getProduct());
        backorder.setShortageQty(shortageQty);
        backorder.setStatus(Backorder.BackorderStatus.OPEN);
        backorderRepository.save(backorder);
    }

    // 긴급발주 트리거 (ALS-WMS-OUT-002 Constraint)
    protected void createUrgentReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerType(AutoReorderLog.TriggerType.URGENT_REORDER);
            log.setCurrentStock(currentStock);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(log);
        }
    }

    // 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
    protected void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            // 전체 가용 재고 합산 (is_expired=true 제외)
            int totalAvailable = inventoryRepository.findByProduct_ProductIdAndIsExpiredFalse(product.getProductId())
                    .stream()
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            // 안전재고 미달 시 자동 재발주
            if (totalAvailable <= rule.getMinQty()) {
                AutoReorderLog log = new AutoReorderLog();
                log.setProduct(product);
                log.setTriggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER);
                log.setCurrentStock(totalAvailable);
                log.setMinQty(rule.getMinQty());
                log.setReorderQty(rule.getReorderQty());
                log.setTriggeredBy("SYSTEM");
                autoReorderLogRepository.save(log);
            }
        }
    }

    // 잔여 유통기한 비율 계산
    protected double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate, LocalDate today) {
        if (expiryDate == null || manufactureDate == null) {
            return 100.0;
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (double) remainingDays / totalDays * 100.0;
    }

    // 감사 로그 기록
    protected void createAuditLog(String eventType, String entityType, UUID entityId, String details, String performedBy) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setPerformedBy(performedBy);
        auditLogRepository.save(log);
    }

    @Transactional
    public ShipmentOrderResponse shipShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("출고 지시서를 찾을 수 없습니다", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.PICKING &&
                shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.PARTIAL) {
            throw new BusinessException("출고 확정 가능한 상태가 아닙니다", "INVALID_STATUS");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        shipmentOrder.setShippedAt(Instant.now());
        shipmentOrderRepository.save(shipmentOrder);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);
        return buildResponse(shipmentOrder, lines);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("출고 지시서를 찾을 수 없습니다", "SHIPMENT_NOT_FOUND"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);
        return buildResponse(shipmentOrder, lines);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> shipments = shipmentOrderRepository.findAll();
        return shipments.stream()
                .map(shipment -> {
                    List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipment.getShipmentId());
                    return buildResponse(shipment, lines);
                })
                .collect(Collectors.toList());
    }

    // 응답 DTO 생성
    protected ShipmentOrderResponse buildResponse(ShipmentOrder shipmentOrder, List<ShipmentOrderLine> lines) {
        ShipmentOrderResponse response = new ShipmentOrderResponse();
        response.setShipmentId(shipmentOrder.getShipmentId());
        response.setShipmentNumber(shipmentOrder.getShipmentNumber());
        response.setCustomerName(shipmentOrder.getCustomerName());
        response.setStatus(shipmentOrder.getStatus().name());
        response.setRequestedAt(shipmentOrder.getRequestedAt());
        response.setShippedAt(shipmentOrder.getShippedAt());
        response.setCreatedAt(shipmentOrder.getCreatedAt());
        response.setUpdatedAt(shipmentOrder.getUpdatedAt());

        List<ShipmentOrderResponse.ShipmentLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    ShipmentOrderResponse.ShipmentLineResponse lineResp = new ShipmentOrderResponse.ShipmentLineResponse();
                    lineResp.setShipmentLineId(line.getShipmentLineId());
                    lineResp.setProductId(line.getProduct().getProductId());
                    lineResp.setProductName(line.getProduct().getName());
                    lineResp.setProductSku(line.getProduct().getSku());
                    lineResp.setRequestedQty(line.getRequestedQty());
                    lineResp.setPickedQty(line.getPickedQty());
                    lineResp.setStatus(line.getStatus().name());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);
        return response;
    }
}
