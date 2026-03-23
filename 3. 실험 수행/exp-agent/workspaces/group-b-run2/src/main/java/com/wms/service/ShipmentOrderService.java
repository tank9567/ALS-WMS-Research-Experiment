package com.wms.service;

import com.wms.dto.PickingResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
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
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 중복 shipmentNumber 체크
        if (shipmentOrderRepository.findByShipmentNumber(request.getShipmentNumber()).isPresent()) {
            throw new BusinessException("DUPLICATE_SHIPMENT_NUMBER", "Shipment number already exists");
        }

        // 2. 상품 검증 및 HAZMAT/FRESH 분리 확인
        List<ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();
        boolean hasFresh = false;

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
                if (product.getCategory() == Product.ProductCategory.FRESH) {
                    hasFresh = true;
                }
            }
        }

        // 3. HAZMAT + FRESH 혼재 시 HAZMAT 분리
        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 별도 출고 지시서 생성
            ShipmentOrder hazmatShipment = createShipmentOrderInternal(
                    request.getShipmentNumber() + "-HAZMAT",
                    request.getCustomerName(),
                    request.getRequestedAt(),
                    hazmatLines
            );
            log.info("HAZMAT shipment created separately: {}", hazmatShipment.getShipmentNumber());

            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder mainShipment = createShipmentOrderInternal(
                    request.getShipmentNumber(),
                    request.getCustomerName(),
                    request.getRequestedAt(),
                    nonHazmatLines
            );
            return ShipmentOrderResponse.from(mainShipment);
        } else {
            // 분리 필요 없음
            ShipmentOrder shipment = createShipmentOrderInternal(
                    request.getShipmentNumber(),
                    request.getCustomerName(),
                    request.getRequestedAt(),
                    request.getLines()
            );
            return ShipmentOrderResponse.from(shipment);
        }
    }

    private ShipmentOrder createShipmentOrderInternal(
            String shipmentNumber,
            String customerName,
            Instant requestedAt,
            List<ShipmentOrderRequest.ShipmentOrderLineRequest> lineRequests
    ) {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .shipmentNumber(shipmentNumber)
                .customerName(customerName)
                .requestedAt(requestedAt != null ? requestedAt : Instant.now())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : lineRequests) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipment)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            lines.add(line);
        }

        shipment.setLines(lines);
        return shipmentOrderRepository.save(shipment);
    }

    @Transactional
    public PickingResponse pickShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findByIdWithLines(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PENDING &&
                shipment.getStatus() != ShipmentOrder.ShipmentStatus.PICKING) {
            throw new BusinessException("INVALID_STATUS", "Shipment order cannot be picked");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        List<PickingResponse.LinePickingDetail> lineDetails = new ArrayList<>();
        List<PickingResponse.BackorderDetail> backorders = new ArrayList<>();

        for (ShipmentOrderLine line : shipment.getLines()) {
            PickingResult result = pickLine(line);
            lineDetails.add(result.lineDetail);
            backorders.addAll(result.backorders);
        }

        // 모든 라인 상태 확인 후 shipment 상태 갱신
        updateShipmentStatus(shipment);

        return PickingResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .shipmentNumber(shipment.getShipmentNumber())
                .status(shipment.getStatus().name())
                .lineDetails(lineDetails)
                .backorders(backorders)
                .build();
    }

    private PickingResult pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // 1. 가용 재고 조회 (피킹 가능한 재고만)
        List<Inventory> availableInventories = getAvailableInventories(product);

        // 2. 전체 가용 재고 계산
        int totalAvailableQty = availableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 부분출고 의사결정
        if (totalAvailableQty == 0) {
            // 전량 백오더
            return createFullBackorder(line, requestedQty);
        } else if (totalAvailableQty < requestedQty) {
            double fulfillmentRate = (double) totalAvailableQty / requestedQty;

            if (fulfillmentRate < 0.3) {
                // 가용 재고 < 30%: 전량 백오더
                return createFullBackorder(line, requestedQty);
            } else if (fulfillmentRate < 0.7) {
                // 30% <= 가용 재고 < 70%: 부분출고 + 백오더 + 긴급발주
                PickingResult result = performPartialPicking(line, availableInventories, totalAvailableQty);
                triggerUrgentReorder(product, totalAvailableQty);
                return result;
            } else {
                // 가용 재고 >= 70%: 부분출고 + 백오더
                return performPartialPicking(line, availableInventories, totalAvailableQty);
            }
        } else {
            // 전량 출고 가능
            return performFullPicking(line, availableInventories, requestedQty);
        }
    }

    private List<Inventory> getAvailableInventories(Product product) {
        // 1. 모든 재고 조회 (is_expired=false, quantity>0, is_frozen=false)
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .collect(Collectors.toList());

        // 2. 유통기한 관리 상품 처리
        if (product.getHasExpiry()) {
            LocalDate today = LocalDate.now();
            List<Inventory> validInventories = new ArrayList<>();

            for (Inventory inv : inventories) {
                // 만료된 재고 제외
                if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                    continue;
                }

                // 잔여율 계산
                double remainingPct = calculateRemainingShelfLifePct(
                        inv.getExpiryDate(),
                        inv.getManufactureDate(),
                        today
                );

                // 잔여율 < 10%: 피킹 불가 (is_expired=true 설정)
                if (remainingPct < 10.0) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    continue;
                }

                validInventories.add(inv);
            }

            inventories = validInventories;

            // 3. FEFO 정렬 (유통기한 관리 상품 - 유통기한이 짧은 것 우선)
            inventories.sort((a, b) -> {
                LocalDate today2 = LocalDate.now();
                double aPct = calculateRemainingShelfLifePct(a.getExpiryDate(), a.getManufactureDate(), today2);
                double bPct = calculateRemainingShelfLifePct(b.getExpiryDate(), b.getManufactureDate(), today2);

                // 잔여율 < 30% 우선
                if (aPct < 30.0 && bPct >= 30.0) return -1;
                if (aPct >= 30.0 && bPct < 30.0) return 1;

                // 유통기한 오름차순
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;

                // 재고량 오름차순 (소량 먼저 소진)
                return Integer.compare(a.getQuantity(), b.getQuantity());
            });
        } else {
            // 4. 효율적 피킹 정렬 (비유통기한 상품)
            // 재고량 내림차순으로 정렬하여 가능한 한 적은 로케이션에서 피킹
            inventories.sort((a, b) -> Integer.compare(b.getQuantity(), a.getQuantity()));
        }

        return inventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate, LocalDate today) {
        if (expiryDate == null || manufactureDate == null) {
            return 100.0;
        }
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (double) remainingDays / totalDays * 100.0;
    }

    private PickingResult performFullPicking(ShipmentOrderLine line, List<Inventory> inventories, int qtyToPick) {
        List<PickingResponse.LocationPickDetail> pickDetails = new ArrayList<>();
        int remainingQty = qtyToPick;

        for (Inventory inv : inventories) {
            if (remainingQty <= 0) break;

            // max_pick_qty 제한 (HAZMAT)
            int maxPickQty = line.getProduct().getMaxPickQty() != null
                    ? line.getProduct().getMaxPickQty()
                    : Integer.MAX_VALUE;

            int pickQty = Math.min(Math.min(remainingQty, inv.getQuantity()), maxPickQty);

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inv.getLocation().setCurrentQty(inv.getLocation().getCurrentQty() - pickQty);

            // 보관 유형 불일치 경고
            if (inv.getLocation().getStorageType() != line.getProduct().getStorageType()) {
                logStorageTypeMismatch(inv, line.getProduct());
            }

            pickDetails.add(PickingResponse.LocationPickDetail.builder()
                    .locationId(inv.getLocation().getLocationId())
                    .locationCode(inv.getLocation().getCode())
                    .pickedQty(pickQty)
                    .lotNumber(inv.getLotNumber())
                    .build());

            remainingQty -= pickQty;
        }

        line.setPickedQty(qtyToPick);
        line.setStatus(ShipmentOrderLine.LineStatus.PICKED);

        return new PickingResult(
                PickingResponse.LinePickingDetail.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .pickDetails(pickDetails)
                        .build(),
                Collections.emptyList()
        );
    }

    private PickingResult performPartialPicking(ShipmentOrderLine line, List<Inventory> inventories, int availableQty) {
        List<PickingResponse.LocationPickDetail> pickDetails = new ArrayList<>();
        int remainingQty = availableQty;

        for (Inventory inv : inventories) {
            if (remainingQty <= 0) break;

            int maxPickQty = line.getProduct().getMaxPickQty() != null
                    ? line.getProduct().getMaxPickQty()
                    : Integer.MAX_VALUE;

            int pickQty = Math.min(Math.min(remainingQty, inv.getQuantity()), maxPickQty);

            inv.setQuantity(inv.getQuantity() - pickQty);
            inv.getLocation().setCurrentQty(inv.getLocation().getCurrentQty() - pickQty);

            if (inv.getLocation().getStorageType() != line.getProduct().getStorageType()) {
                logStorageTypeMismatch(inv, line.getProduct());
            }

            pickDetails.add(PickingResponse.LocationPickDetail.builder()
                    .locationId(inv.getLocation().getLocationId())
                    .locationCode(inv.getLocation().getCode())
                    .pickedQty(pickQty)
                    .lotNumber(inv.getLotNumber())
                    .build());

            remainingQty -= pickQty;
        }

        line.setPickedQty(availableQty);
        line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);

        // 백오더 생성
        int shortageQty = line.getRequestedQty() - availableQty;
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);

        return new PickingResult(
                PickingResponse.LinePickingDetail.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .pickDetails(pickDetails)
                        .build(),
                List.of(PickingResponse.BackorderDetail.builder()
                        .backorderId(backorder.getBackorderId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .shortageQty(shortageQty)
                        .build())
        );
    }

    private PickingResult createFullBackorder(ShipmentOrderLine line, int shortageQty) {
        line.setPickedQty(0);
        line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);

        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);

        return new PickingResult(
                PickingResponse.LinePickingDetail.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(0)
                        .status(line.getStatus().name())
                        .pickDetails(Collections.emptyList())
                        .build(),
                List.of(PickingResponse.BackorderDetail.builder()
                        .backorderId(backorder.getBackorderId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .shortageQty(shortageQty)
                        .build())
        );
    }

    private void triggerUrgentReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.URGENT_REORDER)
                    .currentStock(currentStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(log);
            log.info("Urgent reorder triggered for product: {}", product.getSku());
        }
    }

    private void logStorageTypeMismatch(Inventory inventory, Product product) {
        Map<String, Object> details = new HashMap<>();
        details.put("locationStorageType", inventory.getLocation().getStorageType().name());
        details.put("productStorageType", product.getStorageType().name());
        details.put("locationCode", inventory.getLocation().getCode());
        details.put("productSku", product.getSku());

        AuditLog auditLog = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(inventory.getInventoryId())
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(auditLog);

        log.warn("Storage type mismatch: location {} has {}, but product {} requires {}",
                inventory.getLocation().getCode(),
                inventory.getLocation().getStorageType(),
                product.getSku(),
                product.getStorageType());
    }

    private void updateShipmentStatus(ShipmentOrder shipment) {
        boolean allPicked = shipment.getLines().stream()
                .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED);
        boolean anyPicked = shipment.getLines().stream()
                .anyMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED ||
                        line.getStatus() == ShipmentOrderLine.LineStatus.PARTIAL);

        if (allPicked) {
            shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            shipment.setShippedAt(Instant.now());
        } else if (anyPicked) {
            shipment.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }
    }

    private void checkSafetyStock(ShipmentOrder shipment) {
        Set<UUID> checkedProducts = new HashSet<>();

        for (ShipmentOrderLine line : shipment.getLines()) {
            UUID productId = line.getProduct().getProductId();
            if (checkedProducts.contains(productId)) continue;
            checkedProducts.add(productId);

            SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(productId)
                    .orElse(null);

            if (rule != null) {
                int totalStock = inventoryRepository.findAll().stream()
                        .filter(inv -> inv.getProduct().getProductId().equals(productId))
                        .filter(inv -> !inv.getIsExpired())
                        .mapToInt(Inventory::getQuantity)
                        .sum();

                if (totalStock <= rule.getMinQty()) {
                    AutoReorderLog log = AutoReorderLog.builder()
                            .product(line.getProduct())
                            .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                            .currentStock(totalStock)
                            .minQty(rule.getMinQty())
                            .reorderQty(rule.getReorderQty())
                            .triggeredBy("SYSTEM")
                            .build();
                    autoReorderLogRepository.save(log);
                    log.info("Safety stock reorder triggered for product: {}", line.getProduct().getSku());
                }
            }
        }
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findByIdWithLines(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PICKING &&
                shipment.getStatus() != ShipmentOrder.ShipmentStatus.PARTIAL) {
            throw new BusinessException("INVALID_STATUS", "Shipment order cannot be shipped");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        shipment.setShippedAt(Instant.now());

        return ShipmentOrderResponse.from(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findByIdWithLines(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));
        return ShipmentOrderResponse.from(shipment);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentOrderResponse> getShipmentOrders(Pageable pageable) {
        return shipmentOrderRepository.findAll(pageable)
                .map(ShipmentOrderResponse::from);
    }

    private static class PickingResult {
        PickingResponse.LinePickingDetail lineDetail;
        List<PickingResponse.BackorderDetail> backorders;

        PickingResult(PickingResponse.LinePickingDetail lineDetail, List<PickingResponse.BackorderDetail> backorders) {
            this.lineDetail = lineDetail;
            this.backorders = backorders;
        }
    }
}
