package com.wms.transfer.service;

import com.wms.common.entity.AutoReorderLog;
import com.wms.common.entity.SafetyStockRule;
import com.wms.common.entity.TriggerType;
import com.wms.common.repository.AutoReorderLogRepository;
import com.wms.common.repository.SafetyStockRuleRepository;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.transfer.dto.ApprovalRequest;
import com.wms.transfer.dto.CreateStockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.entity.StockTransfer;
import com.wms.transfer.entity.TransferStatus;
import com.wms.transfer.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse createTransfer(CreateStockTransferRequest request) {
        // 1. 기본 검증
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
            .orElseThrow(() -> new IllegalArgumentException("From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
            .orElseThrow(() -> new IllegalArgumentException("To location not found"));

        // 2. 동일 로케이션 체크
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new IllegalArgumentException("Cannot transfer to the same location");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new IllegalArgumentException("From location is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new IllegalArgumentException("To location is frozen for cycle count");
        }

        // 4. 출발지 재고 확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
            product.getProductId(),
            fromLocation.getLocationId(),
            request.getLotNumber()
        ).orElseThrow(() -> new IllegalArgumentException("Source inventory not found"));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Insufficient quantity at source location");
        }

        // 5. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 6. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryConstraints(sourceInventory, toLocation);
        }

        // 8. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new IllegalArgumentException("Destination location capacity exceeded");
        }

        // 9. 대량 이동 승인 체크 (80% 이상)
        TransferStatus transferStatus = TransferStatus.IMMEDIATE;
        double transferRatio = (double) request.getQuantity() / sourceInventory.getQuantity();
        if (transferRatio >= 0.8) {
            transferStatus = TransferStatus.PENDING_APPROVAL;
        }

        // 10. 이동 이력 기록
        StockTransfer transfer = StockTransfer.builder()
            .product(product)
            .fromLocation(fromLocation)
            .toLocation(toLocation)
            .quantity(request.getQuantity())
            .lotNumber(request.getLotNumber())
            .reason(request.getReason())
            .transferStatus(transferStatus)
            .transferredBy(request.getTransferredBy())
            .build();

        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 반영
        if (transferStatus == TransferStatus.IMMEDIATE) {
            executeTransfer(transfer, sourceInventory);
        }

        return buildResponse(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, ApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        // 재고 확인 및 이동 실행
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
            transfer.getProduct().getProductId(),
            transfer.getFromLocation().getLocationId(),
            transfer.getLotNumber()
        ).orElseThrow(() -> new IllegalArgumentException("Source inventory not found"));

        if (sourceInventory.getQuantity() < transfer.getQuantity()) {
            throw new IllegalArgumentException("Insufficient quantity at source location");
        }

        // 이동 실행
        executeTransfer(transfer, sourceInventory);

        // 승인 상태 업데이트
        transfer.setTransferStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        return buildResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, ApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(TransferStatus.REJECTED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        return buildResponse(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return buildResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
            .map(this::buildResponse)
            .collect(Collectors.toList());
    }

    private void executeTransfer(StockTransfer transfer, Inventory sourceInventory) {
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        Product product = transfer.getProduct();

        // 출발지 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - transfer.getQuantity());
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());

        if (sourceInventory.getQuantity() == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }
        locationRepository.save(fromLocation);

        // 도착지 증가
        Optional<Inventory> destInventoryOpt = inventoryRepository.findByProductAndLocationAndLotNumber(
            product.getProductId(),
            toLocation.getLocationId(),
            transfer.getLotNumber()
        );

        if (destInventoryOpt.isPresent()) {
            // 기존 재고에 수량 추가
            Inventory destInventory = destInventoryOpt.get();
            destInventory.setQuantity(destInventory.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(destInventory);
        } else {
            // 새 재고 레코드 생성
            Inventory newInventory = Inventory.builder()
                .product(product)
                .location(toLocation)
                .quantity(transfer.getQuantity())
                .lotNumber(transfer.getLotNumber())
                .expiryDate(sourceInventory.getExpiryDate())
                .manufactureDate(sourceInventory.getManufactureDate())
                .receivedAt(sourceInventory.getReceivedAt()) // FIFO 유지
                .isExpired(sourceInventory.getIsExpired())
                .build();
            inventoryRepository.save(newInventory);
        }

        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 안전재고 체크 (STORAGE zone만)
        checkSafetyStockForStorageZone(product, transfer.getTransferredBy());
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        StorageType productType = product.getStorageType();
        StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 거부
        if (productType == StorageType.FROZEN && locationType == StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move FROZEN product to AMBIENT location");
        }

        // COLD 상품 → AMBIENT 거부
        if (productType == StorageType.COLD && locationType == StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move COLD product to AMBIENT location");
        }

        // HAZMAT 상품 zone 제약 제거 (위험물 전용 구역 포화로 인해 일반 로케이션 사용 허용)
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 적재된 상품 확인
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getLocation().getLocationId().equals(toLocation.getLocationId()))
            .filter(inv -> inv.getQuantity() > 0)
            .collect(Collectors.toList());

        if (existingInventories.isEmpty()) {
            return;
        }

        boolean isHazmat = product.getCategory() == ProductCategory.HAZMAT;

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new IllegalArgumentException("Cannot mix HAZMAT and non-HAZMAT products in same location");
            }
            if (!isHazmat && existingIsHazmat) {
                throw new IllegalArgumentException("Cannot mix non-HAZMAT and HAZMAT products in same location");
            }
        }
    }

    private void validateExpiryConstraints(Inventory sourceInventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new IllegalArgumentException("Cannot transfer expired inventory");
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalShelfLife = java.time.temporal.ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingShelfLife = java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

            // 잔여 유통기한 < 10% → SHIPPING zone만 허용
            if (remainingPct < 10 && toLocation.getZone() != LocationZone.SHIPPING) {
                throw new IllegalArgumentException("Products with less than 10% shelf life can only be moved to SHIPPING zone");
            }
        }
    }

    private void checkSafetyStockForStorageZone(Product product, String triggeredBy) {
        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> inv.getLocation().getZone() == LocationZone.STORAGE)
            .filter(inv -> !inv.getIsExpired())
            .collect(Collectors.toList());

        int totalStorageQty = storageInventories.stream()
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 기준 확인
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct(product);
        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();
            if (totalStorageQty <= rule.getMinQty()) {
                // 자동 재발주 로그 기록
                AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStorageQty)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy(triggeredBy)
                    .build();
                autoReorderLogRepository.save(log);
            }
        }
    }

    private StockTransferResponse buildResponse(StockTransfer transfer) {
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
            .reason(transfer.getReason())
            .transferStatus(transfer.getTransferStatus())
            .transferredBy(transfer.getTransferredBy())
            .approvedBy(transfer.getApprovedBy())
            .transferredAt(transfer.getTransferredAt())
            .build();
    }
}
