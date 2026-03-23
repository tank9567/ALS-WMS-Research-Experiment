package com.wms.service;

import com.wms.dto.ApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행 (ALS-WMS-STK-002 규칙 준수)
     */
    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        log.info("재고 이동 시작: product={}, from={}, to={}, qty={}",
                request.getProductId(), request.getFromLocationId(), request.getToLocationId(), request.getQuantity());

        // 1. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + request.getProductId()));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("출발지 로케이션을 찾을 수 없습니다: " + request.getFromLocationId()));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("도착지 로케이션을 찾을 수 없습니다: " + request.getToLocationId()));

        // 2. 기본 검증 (ALS-WMS-STK-002 Constraints - 기본 규칙)
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new IllegalArgumentException("출발지와 도착지가 동일합니다");
        }

        // 3. 실사 동결 체크 (ALS-WMS-STK-002 Constraints - Level 2)
        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결 중인 로케이션에서는 이동할 수 없습니다: " + fromLocation.getCode());
        }

        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결 중인 로케이션으로는 이동할 수 없습니다: " + toLocation.getCode());
        }

        // 4. 출발지 재고 조회 및 수량 검증
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, fromLocation, request.getLotNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("출발지에 해당 재고가 없습니다: product=%s, location=%s, lot=%s",
                                product.getSku(), fromLocation.getCode(), request.getLotNumber())));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    String.format("출발지 재고 부족: 요청=%d, 가용=%d", request.getQuantity(), sourceInventory.getQuantity()));
        }

        // 5. 보관 유형 호환성 검증 (ALS-WMS-STK-002 Constraints - Level 2)
        validateStorageTypeCompatibility(product, toLocation);

        // 6. 위험물 혼적 금지 검증 (ALS-WMS-STK-002 Constraints - Level 2)
        validateHazmatMixing(product, toLocation);

        // 7. 유통기한 이동 제한 검증 (ALS-WMS-STK-002 Constraints - Level 2)
        if (Boolean.TRUE.equals(product.getHasExpiry()) && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestriction(sourceInventory, toLocation);
        }

        // 8. 대량 이동 승인 체크 (ALS-WMS-STK-002 Constraints - Level 2)
        boolean isLargeTransfer = (request.getQuantity() * 100.0 / sourceInventory.getQuantity()) >= 80.0;

        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .transferredBy(request.getTransferredBy())
                .transferStatus(isLargeTransfer ? StockTransfer.TransferStatus.pending_approval : StockTransfer.TransferStatus.immediate)
                .build();

        stockTransferRepository.save(transfer);

        // 9. 즉시 이동 또는 승인 대기
        if (!isLargeTransfer) {
            executeActualTransfer(transfer, sourceInventory, toLocation);
            log.info("즉시 이동 완료: transferId={}", transfer.getTransferId());
        } else {
            log.info("대량 이동 승인 대기: transferId={}, 비율={}%",
                    transfer.getTransferId(), request.getQuantity() * 100.0 / sourceInventory.getQuantity());
        }

        return StockTransferResponse.from(transfer);
    }

    /**
     * 대량 이동 승인
     */
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, ApprovalRequest request) {
        log.info("재고 이동 승인 시작: transferId={}", transferId);

        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 중인 이동이 아닙니다: " + transfer.getTransferStatus());
        }

        // 출발지 재고 조회
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(
                        transfer.getProduct(),
                        transfer.getFromLocation(),
                        transfer.getLotNumber())
                .orElseThrow(() -> new IllegalStateException("출발지 재고가 존재하지 않습니다"));

        // 재검증
        if (sourceInventory.getQuantity() < transfer.getQuantity()) {
            throw new IllegalStateException("출발지 재고가 부족하여 승인할 수 없습니다");
        }

        // 실제 이동 실행
        executeActualTransfer(transfer, sourceInventory, transfer.getToLocation());

        // 승인 정보 업데이트
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(request.getApprovedBy());
        transfer.setApprovedAt(OffsetDateTime.now());

        stockTransferRepository.save(transfer);
        log.info("재고 이동 승인 완료: transferId={}", transferId);

        return StockTransferResponse.from(transfer);
    }

    /**
     * 대량 이동 거부
     */
    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, ApprovalRequest request) {
        log.info("재고 이동 거부 시작: transferId={}", transferId);

        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 중인 이동이 아닙니다: " + transfer.getTransferStatus());
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(request.getApprovedBy());
        transfer.setApprovedAt(OffsetDateTime.now());
        transfer.setRejectionReason(request.getRejectionReason());

        stockTransferRepository.save(transfer);
        log.info("재고 이동 거부 완료: transferId={}", transferId);

        return StockTransferResponse.from(transfer);
    }

    /**
     * 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 이력을 찾을 수 없습니다: " + transferId));

        return StockTransferResponse.from(transfer);
    }

    /**
     * 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public List<StockTransferResponse> getTransfers(UUID productId, UUID fromLocationId, UUID toLocationId, String status) {
        List<StockTransfer> transfers;

        if (productId != null) {
            transfers = stockTransferRepository.findByProductProductIdOrderByTransferredAtDesc(productId);
        } else if (fromLocationId != null) {
            transfers = stockTransferRepository.findByFromLocationLocationIdOrderByTransferredAtDesc(fromLocationId);
        } else if (toLocationId != null) {
            transfers = stockTransferRepository.findByToLocationLocationIdOrderByTransferredAtDesc(toLocationId);
        } else if (status != null) {
            StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.valueOf(status);
            transfers = stockTransferRepository.findByTransferStatusOrderByTransferredAtDesc(transferStatus);
        } else {
            transfers = stockTransferRepository.findAll();
        }

        return transfers.stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // ========== 내부 메서드 ==========

    /**
     * 실제 이동 실행 (트랜잭션 내)
     * ALS-WMS-STK-002 Rule: 출발지 차감 + 도착지 증가를 단일 트랜잭션으로 처리
     */
    private void executeActualTransfer(StockTransfer transfer, Inventory sourceInventory, Location toLocation) {
        Product product = transfer.getProduct();
        Integer transferQty = transfer.getQuantity();

        // 1. 출발지 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - transferQty);
        inventoryRepository.save(sourceInventory);

        Location fromLocation = transfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transferQty);
        locationRepository.save(fromLocation);

        log.info("출발지 차감 완료: location={}, qty={}", fromLocation.getCode(), transferQty);

        // 2. 도착지 증가
        // 2-1. 도착지 용량 체크 (ALS-WMS-STK-002 Constraints)
        if (toLocation.getCurrentQty() + transferQty > toLocation.getCapacity()) {
            throw new IllegalStateException(
                    String.format("도착지 용량 초과: 현재=%d, 이동=%d, 용량=%d",
                            toLocation.getCurrentQty(), transferQty, toLocation.getCapacity()));
        }

        // 2-2. 도착지에 동일 상품+lot 조합 확인
        Inventory destInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, toLocation, transfer.getLotNumber())
                .orElse(null);

        if (destInventory != null) {
            // 기존 재고 증가
            destInventory.setQuantity(destInventory.getQuantity() + transferQty);
            inventoryRepository.save(destInventory);
            log.info("도착지 기존 재고 증가: location={}, qty={}", toLocation.getCode(), transferQty);
        } else {
            // 새 재고 생성 (received_at은 원래 값 유지)
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(toLocation)
                    .quantity(transferQty)
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(sourceInventory.getExpiryDate())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .receivedAt(sourceInventory.getReceivedAt())
                    .isExpired(sourceInventory.getIsExpired())
                    .build();
            inventoryRepository.save(newInventory);
            log.info("도착지 신규 재고 생성: location={}, qty={}", toLocation.getCode(), transferQty);
        }

        toLocation.setCurrentQty(toLocation.getCurrentQty() + transferQty);
        locationRepository.save(toLocation);

        // 3. 안전재고 체크 (ALS-WMS-STK-002 Constraints - Level 2)
        checkSafetyStock(product);
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-STK-002 Constraints - Level 2)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("COLD 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
        }

        // HAZMAT 상품 → 비-HAZMAT zone 로케이션: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new IllegalArgumentException("HAZMAT 상품은 HAZMAT zone으로만 이동할 수 있습니다");
        }

        // AMBIENT 상품 → COLD/FROZEN 로케이션: 허용 (상위 호환)
        log.info("보관 유형 호환성 검증 통과: product={}, location={}", productType, locationType);
    }

    /**
     * 위험물 혼적 금지 검증 (ALS-WMS-STK-002 Constraints - Level 2)
     */
    private void validateHazmatMixing(Product product, Location toLocation) {
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        if (existingInventories.isEmpty()) {
            return; // 도착지가 비어있으면 OK
        }

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            if (isHazmat && !existingIsHazmat) {
                throw new IllegalArgumentException(
                        "비-HAZMAT 상품이 이미 적재된 로케이션에 HAZMAT 상품을 이동할 수 없습니다: " + toLocation.getCode());
            }

            if (!isHazmat && existingIsHazmat) {
                throw new IllegalArgumentException(
                        "HAZMAT 상품이 이미 적재된 로케이션에 비-HAZMAT 상품을 이동할 수 없습니다: " + toLocation.getCode());
            }
        }

        log.info("위험물 혼적 검증 통과: product={}, location={}", product.getSku(), toLocation.getCode());
    }

    /**
     * 유통기한 이동 제한 검증 (ALS-WMS-STK-002 Constraints - Level 2)
     */
    private void validateExpiryDateRestriction(Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();
        LocalDate today = LocalDate.now();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new IllegalArgumentException("유통기한이 만료된 재고는 이동할 수 없습니다");
        }

        // 잔여 유통기한 < 10%: SHIPPING zone으로만 허용
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (remainingDays * 100.0) / totalDays;

            log.info("유통기한 잔여율: {}%", remainingPct);

            if (remainingPct < 10.0) {
                if (toLocation.getZone() != Location.Zone.SHIPPING) {
                    throw new IllegalArgumentException(
                            "잔여 유통기한이 10% 미만인 상품은 SHIPPING zone으로만 이동할 수 있습니다");
                }
            }
        }

        log.info("유통기한 제한 검증 통과");
    }

    /**
     * 안전재고 체크 (ALS-WMS-STK-002 Constraints - Level 2)
     * 이동 후 STORAGE zone 내 전체 재고 확인
     */
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);

        if (rule == null) {
            return; // 안전재고 규칙 없으면 체크 안 함
        }

        // STORAGE zone 내 전체 재고 합산
        List<Location> storageLocations = locationRepository.findByZone(Location.Zone.STORAGE);
        int totalQty = 0;

        for (Location loc : storageLocations) {
            List<Inventory> inventories = inventoryRepository.findByProductAndLocation(product, loc);
            totalQty += inventories.stream()
                    .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                    .mapToInt(Inventory::getQuantity)
                    .sum();
        }

        log.info("STORAGE zone 전체 재고: product={}, qty={}, min_qty={}", product.getSku(), totalQty, rule.getMinQty());

        if (totalQty <= rule.getMinQty()) {
            // 안전재고 미달 -> 자동 재발주 요청 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .currentQty(totalQty)
                    .safetyStockQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("안전재고 미달 감지 -> 자동 재발주 요청 기록: product={}, reorder_qty={}",
                    product.getSku(), rule.getReorderQty());
        }
    }
}
