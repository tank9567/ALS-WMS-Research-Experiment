package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository
    ) {
        this.stockTransferRepository = stockTransferRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public StockTransferResponse createStockTransfer(StockTransferRequest request) {
        // 1. 기본 검증 및 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("출발지 로케이션을 찾을 수 없습니다", "FROM_LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("도착지 로케이션을 찾을 수 없습니다", "TO_LOCATION_NOT_FOUND"));

        // 2. 기본 규칙 체크 (ALS-WMS-STK-002 Constraints)

        // 2-1. 출발지와 도착지가 동일한 경우 거부
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new BusinessException("출발지와 도착지가 동일합니다", "SAME_LOCATION");
        }

        // 2-2. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException(
                    "실사 중인 로케이션에서 이동할 수 없습니다: " + fromLocation.getCode(),
                    "FROM_LOCATION_FROZEN"
            );
        }

        if (toLocation.getIsFrozen()) {
            throw new BusinessException(
                    "실사 중인 로케이션으로 이동할 수 없습니다: " + toLocation.getCode(),
                    "TO_LOCATION_FROZEN"
            );
        }

        // 2-3. 출발지 재고 확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                        product.getProductId(),
                        fromLocation.getLocationId(),
                        request.getLotNumber()
                )
                .orElseThrow(() -> new BusinessException(
                        "출발지에 해당 재고가 없습니다",
                        "SOURCE_INVENTORY_NOT_FOUND"
                ));

        // 2-4. 이동 수량이 출발지 재고보다 많으면 거부
        if (request.getQuantity() > sourceInventory.getQuantity()) {
            throw new BusinessException(
                    "이동 수량이 출발지 재고보다 많습니다 (가용: " + sourceInventory.getQuantity() + ")",
                    "INSUFFICIENT_QUANTITY"
            );
        }

        // 2-5. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException(
                    "도착지 로케이션의 용량을 초과합니다 (용량: " + toLocation.getCapacity() +
                            ", 현재: " + toLocation.getCurrentQty() + ", 이동: " + request.getQuantity() + ")",
                    "DESTINATION_CAPACITY_EXCEEDED"
            );
        }

        // 3. 보관 유형 호환성 검증 (ALS-WMS-STK-002 Level 2)
        validateStorageTypeCompatibility(product, toLocation);

        // 4. 위험물 혼적 금지 (ALS-WMS-STK-002 Level 2)
        validateHazmatSegregation(product, toLocation);

        // 5. 유통기한 이동 제한 (ALS-WMS-STK-002 Level 2)
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestrictions(sourceInventory, toLocation);
        }

        // 6. 대량 이동 승인 체크 (ALS-WMS-STK-002 Level 2)
        boolean needsApproval = false;
        BigDecimal transferRatio = BigDecimal.valueOf(request.getQuantity())
                .divide(BigDecimal.valueOf(sourceInventory.getQuantity()), 4, BigDecimal.ROUND_HALF_UP);

        if (transferRatio.compareTo(BigDecimal.valueOf(0.80)) >= 0) {
            needsApproval = true;
        }

        // 7. StockTransfer 이력 생성
        StockTransfer transfer = new StockTransfer();
        transfer.setProduct(product);
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setQuantity(request.getQuantity());
        transfer.setLotNumber(request.getLotNumber());
        transfer.setRequestedBy(request.getRequestedBy());
        transfer.setReason(request.getReason());

        if (needsApproval) {
            // 대량 이동 → 승인 대기
            transfer.setTransferStatus(StockTransfer.TransferStatus.pending_approval);
            stockTransferRepository.save(transfer);

            return StockTransferResponse.from(transfer);
        } else {
            // 즉시 이동
            transfer.setTransferStatus(StockTransfer.TransferStatus.immediate);
            transfer.setCompletedAt(Instant.now());

            // 8. 트랜잭션으로 재고 이동 실행
            executeTransfer(sourceInventory, toLocation, request.getQuantity(), product);

            stockTransferRepository.save(transfer);

            // 9. 안전재고 체크 (ALS-WMS-STK-002 Level 2)
            checkSafetyStock(product);

            return StockTransferResponse.from(transfer);
        }
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("재고 이동 내역을 찾을 수 없습니다", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException(
                    "승인 대기 상태가 아닙니다",
                    "NOT_PENDING_APPROVAL"
            );
        }

        // 출발지 재고 재확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                        transfer.getProduct().getProductId(),
                        transfer.getFromLocation().getLocationId(),
                        transfer.getLotNumber()
                )
                .orElseThrow(() -> new BusinessException(
                        "출발지에 해당 재고가 없습니다",
                        "SOURCE_INVENTORY_NOT_FOUND"
                ));

        if (transfer.getQuantity() > sourceInventory.getQuantity()) {
            throw new BusinessException(
                    "이동 수량이 출발지 재고보다 많습니다",
                    "INSUFFICIENT_QUANTITY"
            );
        }

        // 트랜잭션으로 재고 이동 실행
        executeTransfer(sourceInventory, transfer.getToLocation(), transfer.getQuantity(), transfer.getProduct());

        // 승인 처리
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());
        transfer.setCompletedAt(Instant.now());

        stockTransferRepository.save(transfer);

        // 안전재고 체크
        checkSafetyStock(transfer.getProduct());

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String rejectedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("재고 이동 내역을 찾을 수 없습니다", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException(
                    "승인 대기 상태가 아닙니다",
                    "NOT_PENDING_APPROVAL"
            );
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(rejectedBy);
        transfer.setApprovedAt(Instant.now());

        stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("재고 이동 내역을 찾을 수 없습니다", "TRANSFER_NOT_FOUND"));

        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getTransferHistory(UUID productId) {
        List<StockTransfer> transfers;

        if (productId != null) {
            transfers = stockTransferRepository.findByProductProductIdOrderByRequestedAtDesc(productId);
        } else {
            transfers = stockTransferRepository.findAll();
        }

        return transfers.stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // === 내부 헬퍼 메서드 ===

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN &&
                locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException(
                    "FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다",
                    "STORAGE_TYPE_INCOMPATIBLE"
            );
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD &&
                locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException(
                    "COLD 상품은 AMBIENT 로케이션으로 이동할 수 없습니다",
                    "STORAGE_TYPE_INCOMPATIBLE"
            );
        }

        // HAZMAT 상품 → 비-HAZMAT zone 로케이션: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT &&
                toLocation.getZone() != Location.Zone.HAZMAT) {
            throw new BusinessException(
                    "HAZMAT 상품은 HAZMAT zone으로만 이동할 수 있습니다",
                    "HAZMAT_ZONE_REQUIRED"
            );
        }
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지 로케이션에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findByProduct_ProductIdAndQuantityGreaterThan(
                product.getProductId(), 0
        ).stream()
                .filter(inv -> inv.getLocation().getLocationId().equals(toLocation.getLocationId()))
                .collect(Collectors.toList());

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory existing : existingInventories) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat != existingIsHazmat) {
                throw new BusinessException(
                        "HAZMAT 상품과 비-HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다",
                        "HAZMAT_SEGREGATION_VIOLATION"
                );
            }
        }
    }

    private void validateExpiryDateRestrictions(Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();
        LocalDate today = LocalDate.now();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new BusinessException(
                    "유통기한이 만료된 재고는 이동할 수 없습니다",
                    "EXPIRED_PRODUCT"
            );
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

            if (totalDays > 0) {
                BigDecimal remainingPct = BigDecimal.valueOf(remainingDays)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalDays), 2, BigDecimal.ROUND_HALF_UP);

                // 잔여 유통기한 < 10%: SHIPPING zone으로만 이동 허용
                if (remainingPct.compareTo(BigDecimal.valueOf(10)) < 0) {
                    if (toLocation.getZone() != Location.Zone.SHIPPING) {
                        throw new BusinessException(
                                "잔여 유통기한이 10% 미만인 상품은 SHIPPING zone으로만 이동할 수 있습니다 (잔여: " +
                                        remainingPct + "%)",
                                "EXPIRY_SHIPPING_ONLY"
                        );
                    }
                }
            }
        }
    }

    private void executeTransfer(Inventory sourceInventory, Location toLocation, Integer quantity, Product product) {
        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 현재 수량 갱신
        Location fromLocation = sourceInventory.getLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지에 동일 상품+lot_number 조합이 있는지 확인
        Inventory destInventory = inventoryRepository.findByProductAndLocationAndLot(
                product.getProductId(),
                toLocation.getLocationId(),
                sourceInventory.getLotNumber()
        ).orElse(null);

        if (destInventory != null) {
            // 기존 레코드의 quantity 증가
            destInventory.setQuantity(destInventory.getQuantity() + quantity);
            inventoryRepository.save(destInventory);
        } else {
            // 새 inventory 레코드 생성 (received_at은 원래 값 유지)
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(toLocation);
            newInventory.setQuantity(quantity);
            newInventory.setLotNumber(sourceInventory.getLotNumber());
            newInventory.setExpiryDate(sourceInventory.getExpiryDate());
            newInventory.setManufactureDate(sourceInventory.getManufactureDate());
            newInventory.setReceivedAt(sourceInventory.getReceivedAt());
            newInventory.setIsExpired(sourceInventory.getIsExpired());
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 현재 수량 갱신
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);
    }

    private void checkSafetyStock(Product product) {
        // STORAGE zone 내 전체 재고 합산
        List<Inventory> storageInventories = inventoryRepository.findByProduct_ProductIdAndIsExpiredFalse(
                        product.getProductId()
                ).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .collect(Collectors.toList());

        int totalStorageQty = storageInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .ifPresent(rule -> {
                    if (totalStorageQty <= rule.getMinQty()) {
                        // 자동 재발주 로그 기록
                        AutoReorderLog log = new AutoReorderLog();
                        log.setProduct(product);
                        log.setCurrentQty(totalStorageQty);
                        log.setMinQty(rule.getMinQty());
                        log.setReorderQty(rule.getReorderQty());
                        log.setTriggerReason("SAFETY_STOCK_TRIGGER");
                        log.setTriggeredAt(Instant.now());
                        autoReorderLogRepository.save(log);
                    }
                });
    }
}
