package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ApprovalRequest;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.service.InventoryAdjustmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    public InventoryAdjustmentController(InventoryAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
        @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody ApprovalRequest request
    ) {
        InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(adjustmentId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody ApprovalRequest request
    ) {
        InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(adjustmentId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
        @PathVariable("id") UUID adjustmentId
    ) {
        InventoryAdjustmentResponse response = adjustmentService.getAdjustment(adjustmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustments() {
        List<InventoryAdjustmentResponse> responses = adjustmentService.getAdjustments();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
