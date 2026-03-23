package com.wms.transfer.service;

import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.outbound.entity.AutoReorderLog;
import com.wms.outbound.entity.SafetyStockRule;
import com.wms.outbound.repository.AutoReorderLogRepository;
import com.wms.outbound.repository.SafetyStockRuleRepository;
import com.wms.transfer.dto.StockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.entity.StockTransfer;
import com.wms.transfer.repository.StockTransferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
     * 재고 이동 실행
     */
    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // 1. 기본 검증
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
            .orElseThrow(() -> new IllegalArgumentException("출발지 로케이션을 찾을 수 없습니다"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
            .orElseThrow(() -> new IllegalArgumentException("도착지 로케이션을 찾을 수 없습니다"));

        // 2. 출발지와 도착지가 동일한지 체크
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new IllegalStateException("출발지와 도착지가 동일합니다");
        }

        // 3. 실사 동결 체크
        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결된 로케이션에서는 이동할 수 없습니다: " + fromLocation.getCode());
        }
        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결된 로케이션으로는 이동할 수 없습니다: " + toLocation.getCode());
        }

        // 4. 출발지 재고 확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                product.getProductId(),
                fromLocation.getLocationId(),
                request.getLotNumber()
            ).orElseThrow(() -> new IllegalArgumentException("출발지에 해당 재고가 없습니다"));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new IllegalStateException(
                String.format("재고 부족: 요청 %d > 재고 %d", request.getQuantity(), sourceInventory.getQuantity()));
        }

        // 5. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 6. 위험물 혼적 금지 체크
        validateHazmatCompatibility(product, toLocation);

        // 7. 유통기한 이동 제한 체크
        if (Boolean.TRUE.equals(product.getHasExpiry()) && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestriction(sourceInventory, toLocation);
        }

        // 8. 도착지 용량 체크
        int availableCapacity = toLocation.getCapacity() - toLocation.getCurrentQty();
        if (availableCapacity < request.getQuantity()) {
            throw new IllegalStateException(
                String.format("도착지 용량 부족: 요청 %d > 가용 %d", request.getQuantity(), availableCapacity));
        }

        // 9. 대량 이동 여부 체크 (≥80%)
        double transferRatio = (double) request.getQuantity() / sourceInventory.getQuantity();
        boolean isLargeTransfer = transferRatio >= 0.8;

        StockTransfer.TransferStatus transferStatus;
        if (isLargeTransfer) {
            transferStatus = StockTransfer.TransferStatus.pending_approval;
        } else {
            transferStatus = StockTransfer.TransferStatus.immediate;
        }

        // 10. 이동 이력 기록
        StockTransfer transfer = StockTransfer.builder()
            .transferId(UUID.randomUUID())
            .product(product)
            .fromLocation(fromLocation)
            .toLocation(toLocation)
            .quantity(request.getQuantity())
            .lotNumber(request.getLotNumber())
            .transferStatus(transferStatus)
            .requestedBy(request.getRequestedBy())
            .build();

        stockTransferRepository.save(transfer);

        // 11. 즉시 이동일 경우 실제 재고 이동 실행
        if (transferStatus == StockTransfer.TransferStatus.immediate) {
            executeInventoryTransfer(sourceInventory, product, fromLocation, toLocation,
                request.getQuantity(), request.getLotNumber());
        }

        return mapToResponse(transfer);
    }

    /**
     * 대량 이동 승인
     */
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("이동 내역을 찾을 수 없습니다"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다");
        }

        // 1. 재고 이동 실행
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                transfer.getProduct().getProductId(),
                transfer.getFromLocation().getLocationId(),
                transfer.getLotNumber()
            ).orElseThrow(() -> new IllegalArgumentException("출발지 재고를 찾을 수 없습니다"));

        executeInventoryTransfer(
            sourceInventory,
            transfer.getProduct(),
            transfer.getFromLocation(),
            transfer.getToLocation(),
            transfer.getQuantity(),
            transfer.getLotNumber()
        );

        // 2. 이동 상태 갱신
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());
        stockTransferRepository.save(transfer);

        return mapToResponse(transfer);
    }

    /**
     * 대량 이동 거부
     */
    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("이동 내역을 찾을 수 없습니다"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());
        stockTransferRepository.save(transfer);

        return mapToResponse(transfer);
    }

    /**
     * 이동 상세 조회
     */
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("이동 내역을 찾을 수 없습니다"));

        return mapToResponse(transfer);
    }

    /**
     * 이동 이력 조회
     */
    public Page<StockTransferResponse> getTransfers(Pageable pageable) {
        return stockTransferRepository.findAll(pageable)
            .map(this::mapToResponse);
    }

    // ===== 내부 유틸리티 메서드 =====

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // HAZMAT 상품은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (toLocation.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException("위험물은 HAZMAT zone 로케이션으로만 이동할 수 있습니다");
            }
        }

        // FROZEN → AMBIENT 거부
        if (productType == Product.StorageType.FROZEN) {
            if (locationType == Product.StorageType.AMBIENT) {
                throw new IllegalStateException("FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
            }
        }

        // COLD → AMBIENT 거부
        if (productType == Product.StorageType.COLD) {
            if (locationType == Product.StorageType.AMBIENT) {
                throw new IllegalStateException("COLD 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
            }
        }
    }

    /**
     * 위험물 혼적 금지 검증
     */
    private void validateHazmatCompatibility(Product product, Location toLocation) {
        // 도착지에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getLocation().getLocationId().equals(toLocation.getLocationId()))
            .toList();

        if (existingInventories.isEmpty()) {
            return; // 도착지가 비어있으면 OK
        }

        boolean isProductHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;
        boolean hasHazmatInDestination = existingInventories.stream()
            .anyMatch(inv -> inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT);
        boolean hasNonHazmatInDestination = existingInventories.stream()
            .anyMatch(inv -> inv.getProduct().getCategory() != Product.ProductCategory.HAZMAT);

        // HAZMAT 상품을 비-HAZMAT 상품이 있는 로케이션으로 이동 시도
        if (isProductHazmat && hasNonHazmatInDestination) {
            throw new IllegalStateException("비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다");
        }

        // 비-HAZMAT 상품을 HAZMAT 상품이 있는 로케이션으로 이동 시도
        if (!isProductHazmat && hasHazmatInDestination) {
            throw new IllegalStateException("비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다");
        }
    }

    /**
     * 유통기한 이동 제한 검증
     */
    private void validateExpiryDateRestriction(Inventory inventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new IllegalStateException("유통기한이 만료된 재고는 이동할 수 없습니다");
        }

        // 잔여 유통기한 비율 계산
        LocalDate manufactureDate = inventory.getManufactureDate();
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

            if (totalDays > 0) {
                double remainingPct = (remainingDays * 100.0) / totalDays;

                // 잔여 유통기한 < 10% → SHIPPING zone만 허용
                if (remainingPct < 10.0) {
                    if (toLocation.getZone() != Location.Zone.SHIPPING) {
                        throw new IllegalStateException(
                            String.format("잔여 유통기한이 %.1f%%로 10%% 미만인 재고는 SHIPPING zone으로만 이동할 수 있습니다",
                                remainingPct));
                    }
                }
            }
        }
    }

    /**
     * 실제 재고 이동 실행
     */
    private void executeInventoryTransfer(Inventory sourceInventory, Product product,
                                           Location fromLocation, Location toLocation,
                                           int quantity, String lotNumber) {
        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        if (sourceInventory.getQuantity() == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }

        // 2. 출발지 로케이션 적재량 감소
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (동일 상품+lot 있으면 증가, 없으면 생성)
        Inventory targetInventory = inventoryRepository.findByProductAndLocationAndLot(
                product.getProductId(),
                toLocation.getLocationId(),
                lotNumber
            ).orElse(null);

        if (targetInventory != null) {
            targetInventory.setQuantity(targetInventory.getQuantity() + quantity);
            inventoryRepository.save(targetInventory);
        } else {
            // 새 재고 레코드 생성 (received_at, expiry_date, manufacture_date는 원본 유지)
            Inventory newInventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .product(product)
                .location(toLocation)
                .quantity(quantity)
                .lotNumber(lotNumber)
                .expiryDate(sourceInventory.getExpiryDate())
                .manufactureDate(sourceInventory.getManufactureDate())
                .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지
                .isExpired(false)
                .build();
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 적재량 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone 전체 재고 확인)
        checkSafetyStock(product);
    }

    /**
     * 안전재고 체크 및 자동 재발주 트리거
     */
    private void checkSafetyStock(Product product) {
        // STORAGE zone 내 해당 상품의 전체 재고 합산
        int totalStorageQty = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
            .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository
            .findByProduct_ProductIdAndIsActive(product.getProductId(), true)
            .orElse(null);

        if (rule != null && totalStorageQty < rule.getMinQty()) {
            // 안전재고 미달 → 자동 재발주 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                .reorderId(UUID.randomUUID())
                .product(product)
                .triggeredBy("SAFETY_STOCK_TRIGGER")
                .currentQty(totalStorageQty)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .build();

            autoReorderLogRepository.save(reorderLog);
        }
    }

    /**
     * Entity → Response 매핑
     */
    private StockTransferResponse mapToResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
            .transferId(transfer.getTransferId())
            .productId(transfer.getProduct().getProductId())
            .productSku(transfer.getProduct().getSku())
            .productName(transfer.getProduct().getName())
            .fromLocationId(transfer.getFromLocation().getLocationId())
            .fromLocationCode(transfer.getFromLocation().getCode())
            .toLocationId(transfer.getToLocation().getLocationId())
            .toLocationCode(transfer.getToLocation().getCode())
            .quantity(transfer.getQuantity())
            .lotNumber(transfer.getLotNumber())
            .transferStatus(transfer.getTransferStatus().name())
            .requestedBy(transfer.getRequestedBy())
            .approvedBy(transfer.getApprovedBy())
            .approvedAt(transfer.getApprovedAt())
            .createdAt(transfer.getCreatedAt())
            .build();
    }
}
