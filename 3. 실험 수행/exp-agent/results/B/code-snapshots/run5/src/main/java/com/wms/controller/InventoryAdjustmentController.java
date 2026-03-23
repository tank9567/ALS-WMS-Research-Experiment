package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
        @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody Map<String, String> body
    ) {
        String approvedBy = body.get("approvedBy");
        InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(adjustmentId, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody Map<String, String> body
    ) {
        String rejectedBy = body.get("rejectedBy");
        InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(adjustmentId, rejectedBy);
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
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAllAdjustments() {
        List<InventoryAdjustmentResponse> responses = adjustmentService.getAllAdjustments();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
