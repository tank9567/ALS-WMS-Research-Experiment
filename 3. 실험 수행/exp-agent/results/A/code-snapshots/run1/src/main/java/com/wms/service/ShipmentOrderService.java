package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.entity.AutoReorderLog.TriggerReason;
import com.wms.entity.Backorder.BackorderStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.ShipmentOrder.ShipmentStatus;
import com.wms.entity.ShipmentOrderLine.LineStatus;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class ShipmentOrderService {

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
        // 1. 출고번호 중복 체크
        if (shipmentOrderRepository.existsByShipmentNumber(request.getShipmentNumber())) {
            throw new BusinessException("Shipment number already exists", "DUPLICATE_SHIPMENT_NUMBER");
        }

        // 2. ShipmentOrder 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .status(ShipmentStatus.PENDING)
                .orderDate(request.getOrderDate() != null ? request.getOrderDate() : OffsetDateTime.now())
                .build();

        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // 3. HAZMAT 분리 출고 처리
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            if (product.getCategory() == ProductCategory.HAZMAT) {
                hazmatLines.add(lineRequest);
            } else {
                nonHazmatLines.add(lineRequest);
            }
        }

        // 4. HAZMAT과 FRESH가 함께 있으면 분리 출고
        boolean hasFresh = nonHazmatLines.stream()
                .anyMatch(line -> {
                    Product p = productRepository.findById(line.getProductId()).orElse(null);
                    return p != null && p.getCategory() == ProductCategory.FRESH;
                });

        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 상품만 별도 shipment_order로 분할 생성
            ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                    .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .status(ShipmentStatus.PENDING)
                    .orderDate(request.getOrderDate() != null ? request.getOrderDate() : OffsetDateTime.now())
                    .build();
            hazmatShipment = shipmentOrderRepository.save(hazmatShipment);

            // HAZMAT 라인 생성
            createShipmentLines(hazmatShipment, hazmatLines);

            // 비-HAZMAT 라인 생성 (원래 출고 지시서에)
            List<ShipmentOrderLine> lines = createShipmentLines(shipmentOrder, nonHazmatLines);

            return buildResponse(shipmentOrder, lines);
        } else {
            // 분리 불필요 시 모든 라인 생성
            List<ShipmentOrderLine> lines = createShipmentLines(shipmentOrder, request.getLines());
            return buildResponse(shipmentOrder, lines);
        }
    }

    /**
     * 피킹 실행
     */
    public ShipmentOrderResponse pickShipment(UUID id) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Shipment order not found", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentStatus.PENDING) {
            throw new BusinessException("Can only pick shipments in PENDING status", "INVALID_STATUS");
        }

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        // 피킹 상태로 변경
        shipmentOrder.setStatus(ShipmentStatus.PICKING);
        shipmentOrderRepository.save(shipmentOrder);

        // 각 라인별 피킹 처리
        for (ShipmentOrderLine line : lines) {
            processPicking(line);
        }

        // 출고 상태 업데이트
        updateShipmentStatus(shipmentOrder, lines);

        return buildResponse(shipmentOrder, lines);
    }

    /**
     * 출고 확정
     */
    public ShipmentOrderResponse shipOrder(UUID id) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Shipment order not found", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentStatus.PICKING && shipmentOrder.getStatus() != ShipmentStatus.PARTIAL) {
            throw new BusinessException("Can only ship orders in PICKING or PARTIAL status", "INVALID_STATUS");
        }

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        shipmentOrder.setStatus(ShipmentStatus.SHIPPED);
        shipmentOrder.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipmentOrder);

        return buildResponse(shipmentOrder, lines);
    }

    /**
     * 출고 상세 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID id) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Shipment order not found", "SHIPMENT_NOT_FOUND"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        return buildResponse(shipmentOrder, lines);
    }

    /**
     * 출고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<ShipmentOrderResponse> getShipmentOrders(Pageable pageable) {
        Page<ShipmentOrder> shipments = shipmentOrderRepository.findAll(pageable);

        return shipments.map(shipment -> {
            List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipment.getId());
            return buildResponse(shipment, lines);
        });
    }

    // ===== Private Helper Methods =====

    /**
     * 출고 라인 생성
     */
    private List<ShipmentOrderLine> createShipmentLines(
            ShipmentOrder shipmentOrder,
            List<ShipmentOrderRequest.ShipmentOrderLineRequest> lineRequests
    ) {
        List<ShipmentOrderLine> lines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineRequest : lineRequests) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipmentOrder)
                    .product(product)
                    .requestedQty(lineRequest.getRequestedQty())
                    .pickedQty(0)
                    .status(LineStatus.PENDING)
                    .build();

            lines.add(line);
        }

        return shipmentOrderLineRepository.saveAll(lines);
    }

    /**
     * 피킹 처리
     */
    private void processPicking(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // HAZMAT 상품의 경우 max_pick_qty 제한 확인
        if (product.getCategory() == ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                throw new BusinessException(
                        String.format("HAZMAT product exceeds max pick quantity: requested %d, max %d",
                                requestedQty, product.getMaxPickQty()),
                        "HAZMAT_MAX_PICK_EXCEEDED"
                );
            }
        }

        // 피킹 가능한 재고 조회 (FIFO/FEFO)
        List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

        int totalAvailable = availableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 부분출고 의사결정 트리
        if (totalAvailable == 0) {
            // 전량 백오더
            createBackorder(line, requestedQty);
            line.setStatus(LineStatus.BACKORDERED);
        } else if (totalAvailable < requestedQty * 0.3) {
            // 가용 재고 < 요청의 30%: 전량 백오더
            createBackorder(line, requestedQty);
            line.setStatus(LineStatus.BACKORDERED);
        } else if (totalAvailable >= requestedQty * 0.3 && totalAvailable < requestedQty * 0.7) {
            // 가용 재고 30% ~ 70%: 부분출고 + 백오더 + 긴급발주 트리거
            int pickedQty = pickFromInventories(availableInventories, totalAvailable, product);
            line.setPickedQty(pickedQty);
            line.setStatus(LineStatus.PARTIAL);

            createBackorder(line, requestedQty - pickedQty);
            triggerUrgentReorder(product, totalAvailable);
        } else if (totalAvailable >= requestedQty * 0.7 && totalAvailable < requestedQty) {
            // 가용 재고 70% 이상: 부분출고 + 백오더
            int pickedQty = pickFromInventories(availableInventories, totalAvailable, product);
            line.setPickedQty(pickedQty);
            line.setStatus(LineStatus.PARTIAL);

            createBackorder(line, requestedQty - pickedQty);
        } else {
            // 가용 재고 충분: 전량 출고
            int pickedQty = pickFromInventories(availableInventories, requestedQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(LineStatus.PICKED);
        }

        shipmentOrderLineRepository.save(line);
    }

    /**
     * 피킹 가능한 재고 조회 (효율적 조합 + 비즈니스 규칙)
     * 요청 수량을 채우기 가장 효율적인 로케이션 조합으로 피킹
     */
    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        List<Inventory> inventories = inventoryRepository.findByProductId(product.getId());

        LocalDate today = LocalDate.now();

        // 1. 피킹 가능한 재고 필터링
        List<Inventory> filteredInventories = inventories.stream()
                // 1-1. is_expired 제외
                .filter(inv -> !inv.getIsExpired())
                // 1-2. 유통기한 만료 제외
                .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
                // 1-3. 잔여율 < 10% 제외 (폐기 대상)
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(
                                inv.getManufactureDate(), inv.getExpiryDate(), today
                        );
                        return remainingPct >= 10.0;
                    }
                    return true;
                })
                // 1-4. 실사 동결 로케이션 제외
                .filter(inv -> !inv.getLocation().getIsFrozen())
                // 1-5. 수량 > 0
                .filter(inv -> inv.getQuantity() > 0)
                .collect(Collectors.toList());

        // 2. 우선순위 그룹 분리
        List<Inventory> lowShelfLifeInventories = new ArrayList<>();
        List<Inventory> normalInventories = new ArrayList<>();

        for (Inventory inv : filteredInventories) {
            boolean isLowShelfLife = false;
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double pct = calculateRemainingShelfLifePct(
                        inv.getManufactureDate(), inv.getExpiryDate(), today
                );
                isLowShelfLife = pct < 30.0;
            }

            if (isLowShelfLife) {
                lowShelfLifeInventories.add(inv);
            } else {
                normalInventories.add(inv);
            }
        }

        // 3. 각 그룹 내에서 효율적 조합 정렬
        sortByEfficientCombination(lowShelfLifeInventories, today);
        sortByEfficientCombination(normalInventories, today);

        // 4. 잔여율 낮은 그룹 우선, 그 다음 일반 그룹
        List<Inventory> result = new ArrayList<>();
        result.addAll(lowShelfLifeInventories);
        result.addAll(normalInventories);

        return result;
    }

    /**
     * 효율적 조합을 위한 정렬
     * 1순위: 수량이 많은 로케이션 (단일 로케이션으로 충족 가능)
     * 2순위: FEFO (유통기한 빠른 것)
     * 3순위: FIFO (먼저 입고된 것)
     */
    private void sortByEfficientCombination(List<Inventory> inventories, LocalDate today) {
        inventories.sort((inv1, inv2) -> {
            // 1순위: 수량이 많은 로케이션 우선 (내림차순)
            int qtyCompare = Integer.compare(inv2.getQuantity(), inv1.getQuantity());
            if (qtyCompare != 0) return qtyCompare;

            // 2순위: FEFO - 유통기한 빠른 것 우선
            if (inv1.getExpiryDate() != null && inv2.getExpiryDate() != null) {
                int expCompare = inv1.getExpiryDate().compareTo(inv2.getExpiryDate());
                if (expCompare != 0) return expCompare;
            } else if (inv1.getExpiryDate() != null) {
                return -1;
            } else if (inv2.getExpiryDate() != null) {
                return 1;
            }

            // 3순위: FIFO - 먼저 입고된 것 우선
            return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
        });
    }

    /**
     * 재고에서 피킹 수행
     */
    private int pickFromInventories(List<Inventory> inventories, int qtyToPick, Product product) {
        int remainingQty = qtyToPick;
        int totalPicked = 0;

        for (Inventory inventory : inventories) {
            if (remainingQty <= 0) break;

            int pickQty = Math.min(inventory.getQuantity(), remainingQty);

            // 재고 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // 로케이션 적재량 차감
            Location location = inventory.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고
            if (product.getStorageType() != location.getStorageType()) {
                createAuditLog("SHIPMENT", inventory.getId(),
                        "STORAGE_TYPE_MISMATCH",
                        String.format("Product storage type %s mismatches location storage type %s",
                                product.getStorageType(), location.getStorageType()));
            }

            totalPicked += pickQty;
            remainingQty -= pickQty;
        }

        return totalPicked;
    }

    /**
     * 백오더 생성
     */
    private void createBackorder(ShipmentOrderLine line, int backorderedQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .backorderedQty(backorderedQty)
                .status(BackorderStatus.OPEN)
                .build();

        backorderRepository.save(backorder);
    }

    /**
     * 긴급발주 트리거
     */
    private void triggerUrgentReorder(Product product, int currentStock) {
        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason(TriggerReason.URGENT_REORDER)
                .currentStock(currentStock)
                .reorderQty(0) // 긴급발주는 수량 미정
                .build();

        autoReorderLogRepository.save(log);
    }

    /**
     * 안전재고 체크 및 자동 재발주
     */
    private void checkAndTriggerSafetyStockReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule == null) return;

        // 전체 가용 재고 계산 (expired 제외)
        int totalAvailable = inventoryRepository.findByProductId(product.getId()).stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason(TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailable)
                    .reorderQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 감사 로그 생성
     */
    private void createAuditLog(String entityType, UUID entityId, String action, String description) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .description(description)
                .createdBy("SYSTEM")
                .build();

        auditLogRepository.save(log);
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalShelfLife <= 0) return 0.0;

        return (remainingShelfLife * 100.0) / totalShelfLife;
    }

    /**
     * 출고 상태 업데이트
     */
    private void updateShipmentStatus(ShipmentOrder shipmentOrder, List<ShipmentOrderLine> lines) {
        boolean allPicked = lines.stream().allMatch(line -> line.getStatus() == LineStatus.PICKED);
        boolean anyPicked = lines.stream().anyMatch(line -> line.getPickedQty() > 0);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentStatus.PICKING);
        } else if (anyPicked) {
            shipmentOrder.setStatus(ShipmentStatus.PARTIAL);
        }

        shipmentOrderRepository.save(shipmentOrder);
    }

    /**
     * Response 빌드
     */
    private ShipmentOrderResponse buildResponse(ShipmentOrder shipmentOrder, List<ShipmentOrderLine> lines) {
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
                .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                        .id(line.getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus())
                        .build())
                .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
                .id(shipmentOrder.getId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus())
                .orderDate(shipmentOrder.getOrderDate())
                .shippedAt(shipmentOrder.getShippedAt())
                .lines(lineResponses)
                .createdAt(shipmentOrder.getCreatedAt())
                .updatedAt(shipmentOrder.getUpdatedAt())
                .build();
    }
}
