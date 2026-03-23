package com.wms.controller;

import com.wms.dto.*;
import com.wms.entity.CycleCount;
import com.wms.entity.InventoryAdjustment;
import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.ProductRepository;
import com.wms.repository.InventoryAdjustmentRepository;
import com.wms.service.InventoryAdjustmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;

    public InventoryAdjustmentController(
            InventoryAdjustmentService inventoryAdjustmentService,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            ProductRepository productRepository) {
        this.inventoryAdjustmentService = inventoryAdjustmentService;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
    }

    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @RequestBody CycleCountRequest request) {
        try {
            CycleCount cycleCount = inventoryAdjustmentService.startCycleCount(
                    request.getLocationId(),
                    request.getProductId(),
                    request.getStartedBy()
            );

            CycleCountResponse response = buildCycleCountResponse(cycleCount);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable UUID id,
            @RequestBody CycleCountCompleteRequest request) {
        try {
            CycleCount cycleCount = inventoryAdjustmentService.completeCycleCount(
                    id,
                    request.getActualQty(),
                    request.getReason()
            );

            CycleCountResponse response = buildCycleCountResponse(cycleCount);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @RequestBody InventoryAdjustmentRequest request) {
        try {
            Inventory inventory = request.getInventoryId() != null
                    ? inventoryRepository.findById(request.getInventoryId()).orElse(null)
                    : null;

            Location location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));

            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            int differenceQty = request.getActualQty() - request.getSystemQty();

            InventoryAdjustment adjustment = inventoryAdjustmentService.createInventoryAdjustment(
                    inventory,
                    location,
                    product,
                    request.getSystemQty(),
                    request.getActualQty(),
                    differenceQty,
                    request.getReason()
            );

            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody InventoryAdjustmentApprovalRequest request) {
        try {
            InventoryAdjustment adjustment = inventoryAdjustmentService.approveAdjustment(
                    id,
                    request.getApprovedBy()
            );

            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestBody InventoryAdjustmentRejectionRequest request) {
        try {
            InventoryAdjustment adjustment = inventoryAdjustmentService.rejectAdjustment(
                    id,
                    request.getRejectionReason()
            );

            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable UUID id) {
        try {
            InventoryAdjustment adjustment = inventoryAdjustmentService.getAdjustment(id);
            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAllAdjustments(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId) {
        try {
            List<InventoryAdjustment> adjustments;

            if (productId != null) {
                adjustments = inventoryAdjustmentService.getAdjustmentsByProduct(productId);
            } else if (locationId != null) {
                adjustments = inventoryAdjustmentService.getAdjustmentsByLocation(locationId);
            } else {
                adjustments = inventoryAdjustmentService.getAllAdjustments();
            }

            List<InventoryAdjustmentResponse> responses = adjustments.stream()
                    .map(this::buildAdjustmentResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/inventory-adjustments/old")
    public ResponseEntity<ApiResponse<Integer>> deleteOldAdjustments() {
        try {
            int deletedCount = inventoryAdjustmentService.deleteOldAdjustments();
            return ResponseEntity.ok(ApiResponse.success(deletedCount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // === Helper Methods ===

    private CycleCountResponse buildCycleCountResponse(CycleCount cycleCount) {
        CycleCountResponse response = new CycleCountResponse();
        response.setId(cycleCount.getId());
        response.setLocationId(cycleCount.getLocation().getId());
        response.setProductId(cycleCount.getProduct().getId());
        response.setStatus(cycleCount.getStatus().name());
        response.setStartedBy(cycleCount.getStartedBy());
        response.setStartedAt(cycleCount.getStartedAt());
        response.setCompletedAt(cycleCount.getCompletedAt());
        return response;
    }

    private InventoryAdjustmentResponse buildAdjustmentResponse(InventoryAdjustment adjustment) {
        InventoryAdjustmentResponse response = new InventoryAdjustmentResponse();
        response.setId(adjustment.getId());
        response.setInventoryId(adjustment.getInventory() != null ? adjustment.getInventory().getId() : null);
        response.setLocationId(adjustment.getLocation().getId());
        response.setProductId(adjustment.getProduct().getId());
        response.setSystemQty(adjustment.getSystemQty());
        response.setActualQty(adjustment.getActualQty());
        response.setDifferenceQty(adjustment.getDifferenceQty());
        response.setReason(adjustment.getReason());
        response.setApprovalStatus(adjustment.getApprovalStatus().name());
        response.setApprovedBy(adjustment.getApprovedBy());
        response.setApprovedAt(adjustment.getApprovedAt());
        response.setRejectionReason(adjustment.getRejectionReason());
        response.setCreatedAt(adjustment.getCreatedAt());
        return response;
    }
}
