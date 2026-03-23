package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.ResourceNotFoundException;
import com.wms.exception.StockTransferException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private final StockTransferRepository transferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행
     * ALS-WMS-STK-002 규칙 준수
     */
    @Transactional
    public StockTransferResponse transferStock(StockTransferRequest request) {
        // 1. 기본 검증
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new StockTransferException("Source and destination locations must be different", "SAME_LOCATION");
        }

        // 2. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Source location not found", "LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Destination location not found", "LOCATION_NOT_FOUND"));

        // 3. 실사 동결 체크 (ALS-WMS-STK-002 Level 2)
        if (fromLocation.getIsFrozen()) {
            throw new StockTransferException("Cannot transfer from frozen location", "LOCATION_FROZEN");
        }
        if (toLocation.getIsFrozen()) {
            throw new StockTransferException("Cannot transfer to frozen location", "LOCATION_FROZEN");
        }

        // 4. 출발지 재고 조회 및 수량 체크
        Inventory fromInventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                request.getProductId(), request.getFromLocationId(), request.getLotNumber())
            .orElseThrow(() -> new StockTransferException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        if (request.getQuantity() > fromInventory.getQuantity()) {
            throw new StockTransferException(
                String.format("Insufficient quantity: available=%d, requested=%d",
                    fromInventory.getQuantity(), request.getQuantity()),
                "INSUFFICIENT_QUANTITY");
        }

        // 5. 도착지 용량 체크 (ALS-WMS-STK-002 Level 1)
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new StockTransferException(
                String.format("Destination location capacity exceeded: current=%d, capacity=%d, transfer=%d",
                    toLocation.getCurrentQty(), toLocation.getCapacity(), request.getQuantity()),
                "CAPACITY_EXCEEDED");
        }

        // 6. 보관 유형 호환성 체크 (ALS-WMS-STK-002 Level 2)
        validateStorageTypeCompatibility(product, toLocation);

        // 7. 위험물 혼적 금지 체크 (ALS-WMS-STK-002 Level 2)
        validateHazmatSegregation(product, toLocation, request.getProductId());

        // 8. 유통기한 이동 제한 체크 (ALS-WMS-STK-002 Level 2)
        validateExpiryDateRestrictions(fromInventory, toLocation);

        // 9. 대량 이동 승인 체크 (ALS-WMS-STK-002 Level 2)
        boolean requiresApproval = checkIfRequiresApproval(fromInventory.getQuantity(), request.getQuantity());

        // 10. StockTransfer 이력 생성
        StockTransfer transfer = StockTransfer.builder()
            .productId(request.getProductId())
            .fromLocationId(request.getFromLocationId())
            .toLocationId(request.getToLocationId())
            .quantity(request.getQuantity())
            .lotNumber(request.getLotNumber())
            .transferStatus(requiresApproval ? StockTransfer.TransferStatus.pending_approval : StockTransfer.TransferStatus.immediate)
            .requestedBy(request.getRequestedBy())
            .reason(request.getReason())
            .build();

        transferRepository.save(transfer);

        // 11. 대량 이동이면 승인 대기 상태로 반환 (재고 변동 없음)
        if (requiresApproval) {
            log.info("Large transfer (≥80%) requires approval. Transfer ID: {}", transfer.getTransferId());
            return toResponse(transfer);
        }

        // 12. 즉시 이동 실행 (단일 트랜잭션)
        executeTransfer(transfer, fromInventory, fromLocation, toLocation);

        // 13. 안전재고 체크 (ALS-WMS-STK-002 Level 2)
        checkSafetyStockAfterTransfer(product.getProductId());

        return toResponse(transfer);
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-STK-002)
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new StockTransferException("FROZEN products cannot be transferred to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new StockTransferException("COLD products cannot be transferred to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // HAZMAT 상품 → 비-HAZMAT zone: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT && toLocation.getZone() != Location.Zone.HAZMAT) {
            throw new StockTransferException("HAZMAT products can only be transferred to HAZMAT zone", "HAZMAT_ZONE_REQUIRED");
        }

        // AMBIENT 상품 → COLD/FROZEN 로케이션: 허용 (상위 호환)
    }

    /**
     * 위험물 혼적 금지 검증 (ALS-WMS-STK-002)
     */
    private void validateHazmatSegregation(Product product, Location toLocation, UUID productId) {
        // 도착지에 이미 있는 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findByLocationId(toLocation.getLocationId());

        if (existingInventories.isEmpty()) {
            return; // 도착지가 비어있으면 OK
        }

        boolean isHazmat = (product.getCategory() == Product.ProductCategory.HAZMAT);

        for (Inventory inv : existingInventories) {
            if (inv.getProductId().equals(productId)) {
                continue; // 동일 상품은 체크 불필요
            }

            Product existingProduct = productRepository.findById(inv.getProductId())
                .orElse(null);

            if (existingProduct == null) {
                continue;
            }

            boolean existingIsHazmat = (existingProduct.getCategory() == Product.ProductCategory.HAZMAT);

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new StockTransferException(
                    "Cannot transfer HAZMAT products to location with non-HAZMAT products",
                    "HAZMAT_SEGREGATION_VIOLATION");
            }

            if (!isHazmat && existingIsHazmat) {
                throw new StockTransferException(
                    "Cannot transfer non-HAZMAT products to location with HAZMAT products",
                    "HAZMAT_SEGREGATION_VIOLATION");
            }
        }
    }

    /**
     * 유통기한 이동 제한 검증 (ALS-WMS-STK-002)
     */
    private void validateExpiryDateRestrictions(Inventory fromInventory, Location toLocation) {
        if (fromInventory.getExpiryDate() == null) {
            return; // 유통기한 관리 대상 아님
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = fromInventory.getExpiryDate();
        LocalDate manufactureDate = fromInventory.getManufactureDate();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new StockTransferException("Cannot transfer expired products", "EXPIRED_PRODUCT");
        }

        // 잔여 유통기한 < 10%: SHIPPING zone만 허용
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (double) remainingDays / totalDays * 100.0;

            if (remainingPct < 10.0 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new StockTransferException(
                    String.format("Products with remaining shelf life < 10%% can only be transferred to SHIPPING zone (current: %.1f%%)",
                        remainingPct),
                    "LOW_SHELF_LIFE_RESTRICTION");
            }
        }
    }

    /**
     * 대량 이동 여부 체크 (≥80%)
     */
    private boolean checkIfRequiresApproval(int currentQty, int transferQty) {
        double transferPct = (double) transferQty / currentQty * 100.0;
        return transferPct >= 80.0;
    }

    /**
     * 이동 실행 (단일 트랜잭션)
     */
    private void executeTransfer(StockTransfer transfer, Inventory fromInventory,
                                 Location fromLocation, Location toLocation) {
        // 1. 출발지 재고 차감
        fromInventory.setQuantity(fromInventory.getQuantity() - transfer.getQuantity());
        inventoryRepository.save(fromInventory);

        // 2. 출발지 로케이션 current_qty 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 레코드 있으면 증가, 없으면 생성)
        Inventory toInventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                transfer.getProductId(), transfer.getToLocationId(), transfer.getLotNumber())
            .orElseGet(() -> {
                Inventory newInv = Inventory.builder()
                    .productId(transfer.getProductId())
                    .locationId(transfer.getToLocationId())
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(fromInventory.getExpiryDate())
                    .manufactureDate(fromInventory.getManufactureDate())
                    .receivedAt(fromInventory.getReceivedAt()) // 원래 입고일 유지
                    .quantity(0)
                    .build();
                return newInv;
            });

        toInventory.setQuantity(toInventory.getQuantity() + transfer.getQuantity());
        inventoryRepository.save(toInventory);

        // 4. 도착지 로케이션 current_qty 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 5. 이동 완료 시각 기록
        transfer.setExecutedAt(OffsetDateTime.now());
        transferRepository.save(transfer);

        log.info("Transfer executed successfully. Transfer ID: {}, Quantity: {}",
            transfer.getTransferId(), transfer.getQuantity());
    }

    /**
     * 안전재고 체크 (ALS-WMS-STK-002 Level 2)
     */
    private void checkSafetyStockAfterTransfer(UUID productId) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId)
            .orElse(null);

        if (rule == null) {
            return; // 안전재고 설정 없음
        }

        // STORAGE zone 내 전체 재고 합산
        List<Inventory> storageInventories = inventoryRepository.findByProductId(productId);
        int totalStorageQty = storageInventories.stream()
            .filter(inv -> {
                Location loc = locationRepository.findById(inv.getLocationId()).orElse(null);
                return loc != null && loc.getZone() == Location.Zone.STORAGE && !inv.getIsExpired();
            })
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 미달 시 자동 재발주 기록
        if (totalStorageQty < rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                .productId(productId)
                .triggerReason("SAFETY_STOCK_TRIGGER")
                .currentQty(totalStorageQty)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock below minimum. Product: {}, Current: {}, Min: {}, Reorder: {}",
                productId, totalStorageQty, rule.getMinQty(), rule.getReorderQty());
        }
    }

    /**
     * 대량 이동 승인
     */
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new StockTransferException("Transfer is not pending approval", "INVALID_STATUS");
        }

        // 승인 상태로 변경
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(OffsetDateTime.now());
        transferRepository.save(transfer);

        // 이동 실행
        Inventory fromInventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                transfer.getProductId(), transfer.getFromLocationId(), transfer.getLotNumber())
            .orElseThrow(() -> new StockTransferException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(transfer.getFromLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Source location not found", "LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(transfer.getToLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Destination location not found", "LOCATION_NOT_FOUND"));

        executeTransfer(transfer, fromInventory, fromLocation, toLocation);

        // 안전재고 체크
        checkSafetyStockAfterTransfer(transfer.getProductId());

        return toResponse(transfer);
    }

    /**
     * 대량 이동 거부
     */
    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new StockTransferException("Transfer is not pending approval", "INVALID_STATUS");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(OffsetDateTime.now());
        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    /**
     * 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found", "TRANSFER_NOT_FOUND"));
        return toResponse(transfer);
    }

    /**
     * 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public Page<StockTransferResponse> getTransfers(Pageable pageable) {
        return transferRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Entity -> DTO 변환
     */
    private StockTransferResponse toResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
            .transferId(transfer.getTransferId())
            .productId(transfer.getProductId())
            .fromLocationId(transfer.getFromLocationId())
            .toLocationId(transfer.getToLocationId())
            .quantity(transfer.getQuantity())
            .lotNumber(transfer.getLotNumber())
            .transferStatus(transfer.getTransferStatus().name())
            .requestedBy(transfer.getRequestedBy())
            .approvedBy(transfer.getApprovedBy())
            .requestedAt(transfer.getRequestedAt())
            .approvedAt(transfer.getApprovedAt())
            .executedAt(transfer.getExecutedAt())
            .reason(transfer.getReason())
            .createdAt(transfer.getCreatedAt())
            .build();
    }
}
