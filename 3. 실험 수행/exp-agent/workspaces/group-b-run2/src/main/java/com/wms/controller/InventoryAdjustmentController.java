package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ApprovalRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        log.info("Starting cycle count for location: {}", request.getLocationId());
        CycleCountResponse response = inventoryAdjustmentService.startCycleCount(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable("id") UUID cycleCountId) {
        log.info("Completing cycle count: {}", cycleCountId);
        CycleCountResponse response = inventoryAdjustmentService.completeCycleCount(cycleCountId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createInventoryAdjustment(
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        log.info("Creating inventory adjustment: product={}, location={}, actualQty={}",
                request.getProductId(), request.getLocationId(), request.getActualQty());
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createInventoryAdjustment(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveInventoryAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @Valid @RequestBody ApprovalRequest request) {
        log.info("Approving inventory adjustment: {}", adjustmentId);
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveInventoryAdjustment(adjustmentId, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectInventoryAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @Valid @RequestBody ApprovalRequest request) {
        log.info("Rejecting inventory adjustment: {}", adjustmentId);
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectInventoryAdjustment(adjustmentId, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getInventoryAdjustment(
            @PathVariable("id") UUID adjustmentId) {
        log.info("Getting inventory adjustment: {}", adjustmentId);
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getInventoryAdjustment(adjustmentId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<Page<InventoryAdjustmentResponse>>> getAllInventoryAdjustments(
            Pageable pageable) {
        log.info("Getting all inventory adjustments with pagination: {}", pageable);
        Page<InventoryAdjustmentResponse> response = inventoryAdjustmentService.getAllInventoryAdjustments(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}
