package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.enums.*;
import com.wms.exception.WmsException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // Validate inventory
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new WmsException("Inventory not found"));

        // Validate locations
        Location fromLocation = locationRepository.findByCode(request.getFromLocationCode())
                .orElseThrow(() -> new WmsException("From location not found: " + request.getFromLocationCode()));
        Location toLocation = locationRepository.findByCode(request.getToLocationCode())
                .orElseThrow(() -> new WmsException("To location not found: " + request.getToLocationCode()));

        // Validate inventory belongs to from location
        if (!inventory.getLocation().getId().equals(fromLocation.getId())) {
            throw new WmsException("Inventory does not belong to from location");
        }

        // Basic validations
        validateTransfer(inventory, fromLocation, toLocation, request.getQuantity());

        // Determine transfer status
        TransferStatus transferStatus = determineTransferStatus(inventory, request.getQuantity());

        // Create transfer record
        StockTransfer transfer = StockTransfer.builder()
                .product(inventory.getProduct())
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .inventory(inventory)
                .quantity(request.getQuantity())
                .transferStatus(transferStatus)
                .reason(request.getReason())
                .build();

        stockTransferRepository.save(transfer);

        // If immediate, execute transfer
        if (transferStatus == TransferStatus.IMMEDIATE) {
            executeImmediateTransfer(transfer);
        }

        return toResponse(transfer);
    }

    private void validateTransfer(Inventory inventory, Location fromLocation, Location toLocation, Integer quantity) {
        Product product = inventory.getProduct();

        // Same location check
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new WmsException("Cannot transfer to the same location");
        }

        // Quantity check
        if (quantity <= 0) {
            throw new WmsException("Transfer quantity must be greater than 0");
        }

        if (quantity > inventory.getQuantity()) {
            throw new WmsException("Insufficient inventory quantity");
        }

        // Capacity check
        if (toLocation.getCurrentQty() + quantity > toLocation.getCapacity()) {
            throw new WmsException("Destination location capacity exceeded");
        }

        // Frozen location check
        if (fromLocation.getIsFrozen()) {
            throw new WmsException("Cannot transfer from frozen location");
        }

        if (toLocation.getIsFrozen()) {
            throw new WmsException("Cannot transfer to frozen location");
        }

        // Storage type compatibility check
        validateStorageTypeCompatibility(product, toLocation);

        // HAZMAT mixing check
        validateHazmatMixing(product, toLocation);

        // Expiry date check for products with expiry
        if (product.getHasExpiry() && inventory.getExpiryDate() != null) {
            validateExpiryDateRestrictions(inventory, toLocation);
        }
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        StorageType productType = product.getStorageType();
        StorageType locationType = toLocation.getStorageType();

        // FROZEN product -> only FROZEN location
        if (productType == StorageType.FROZEN && locationType != StorageType.FROZEN) {
            throw new WmsException("FROZEN product cannot be moved to " + locationType + " location");
        }

        // COLD product -> COLD or FROZEN location
        if (productType == StorageType.COLD &&
            locationType != StorageType.COLD && locationType != StorageType.FROZEN) {
            throw new WmsException("COLD product cannot be moved to AMBIENT location");
        }

        // HAZMAT product -> Allow STORAGE zone in addition to HAZMAT zone
        if (product.getCategory() == ProductCategory.HAZMAT
            && toLocation.getZone() != Zone.HAZMAT
            && toLocation.getZone() != Zone.STORAGE) {
            throw new WmsException("HAZMAT product can only be in HAZMAT or STORAGE zone");
        }
    }

    private void validateHazmatMixing(Product product, Location toLocation) {
        // Check existing inventory at destination
        List<Inventory> existingInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .filter(inv -> inv.getQuantity() > 0)
                .collect(Collectors.toList());

        boolean isHazmat = product.getCategory() == ProductCategory.HAZMAT;

        for (Inventory existing : existingInventory) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == ProductCategory.HAZMAT;

            // HAZMAT and non-HAZMAT cannot be mixed
            if (isHazmat != existingIsHazmat) {
                throw new WmsException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }
        }
    }

    private void validateExpiryDateRestrictions(Inventory inventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();
        LocalDate manufactureDate = inventory.getManufactureDate();

        // Expired products cannot be moved
        if (expiryDate.isBefore(today)) {
            throw new WmsException("Cannot transfer expired product");
        }

        // Calculate remaining shelf life percentage
        if (manufactureDate != null) {
            double remainingPct = calculateRemainingShelfLifePct(manufactureDate, expiryDate, today);

            // < 10% can only go to SHIPPING zone
            if (remainingPct < 10.0 && toLocation.getZone() != Zone.SHIPPING) {
                throw new WmsException("Products with <10% remaining shelf life can only be moved to SHIPPING zone");
            }
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (remainingDays * 100.0) / totalDays;
    }

    private TransferStatus determineTransferStatus(Inventory inventory, Integer quantity) {
        // If transfer quantity >= 80% of inventory, require approval
        double transferRatio = (double) quantity / inventory.getQuantity();
        if (transferRatio >= 0.8) {
            return TransferStatus.PENDING_APPROVAL;
        }
        return TransferStatus.IMMEDIATE;
    }

    private void executeImmediateTransfer(StockTransfer transfer) {
        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        Integer quantity = transfer.getQuantity();

        // Deduct from source
        inventory.setQuantity(inventory.getQuantity() - quantity);
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);

        // Find or create inventory at destination
        Optional<Inventory> destInventoryOpt = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .filter(inv -> inv.getProduct().getId().equals(inventory.getProduct().getId()))
                .filter(inv -> {
                    // Match by lot number if exists
                    if (inventory.getLotNumber() != null) {
                        return inventory.getLotNumber().equals(inv.getLotNumber());
                    }
                    return true;
                })
                .filter(inv -> {
                    // Match by expiry date if exists
                    if (inventory.getExpiryDate() != null) {
                        return inventory.getExpiryDate().equals(inv.getExpiryDate());
                    }
                    return inv.getExpiryDate() == null;
                })
                .findFirst();

        if (destInventoryOpt.isPresent()) {
            // Add to existing inventory
            Inventory destInventory = destInventoryOpt.get();
            destInventory.setQuantity(destInventory.getQuantity() + quantity);
        } else {
            // Create new inventory at destination
            Inventory newInventory = Inventory.builder()
                    .product(inventory.getProduct())
                    .location(toLocation)
                    .lotNumber(inventory.getLotNumber())
                    .quantity(quantity)
                    .manufactureDate(inventory.getManufactureDate())
                    .expiryDate(inventory.getExpiryDate())
                    .receivedAt(inventory.getReceivedAt())
                    .expired(false)
                    .build();
            inventoryRepository.save(newInventory);
        }

        // Update destination location quantity
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);

        locationRepository.save(fromLocation);
        locationRepository.save(toLocation);
        inventoryRepository.save(inventory);

        // Check safety stock after transfer (for STORAGE zone)
        if (fromLocation.getZone() == Zone.STORAGE) {
            checkSafetyStock(inventory.getProduct());
        }
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new WmsException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new WmsException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(OffsetDateTime.now());

        stockTransferRepository.save(transfer);

        // Execute the transfer
        executeImmediateTransfer(transfer);

        return toResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String rejectedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new WmsException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new WmsException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(TransferStatus.REJECTED);
        transfer.setApprovedBy(rejectedBy);
        transfer.setApprovedAt(OffsetDateTime.now());

        stockTransferRepository.save(transfer);

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new WmsException("Transfer not found"));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        if (ruleOpt.isEmpty()) return;

        SafetyStockRule rule = ruleOpt.get();

        // Calculate total available inventory in STORAGE zone
        int totalStorageQty = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> inv.getLocation().getZone() == Zone.STORAGE)
                .filter(inv -> !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalStorageQty <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .requestedQty(rule.getReorderQty())
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    private StockTransferResponse toResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus().name())
                .reason(transfer.getReason())
                .approvedBy(transfer.getApprovedBy())
                .approvedAt(transfer.getApprovedAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }
}
