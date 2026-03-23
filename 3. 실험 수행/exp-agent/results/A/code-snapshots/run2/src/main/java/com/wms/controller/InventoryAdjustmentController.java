package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryAdjustmentResponse> createAdjustment(
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createAdjustment(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<InventoryAdjustmentResponse> approveAdjustment(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryAdjustmentApprovalRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveAdjustment(id, request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<InventoryAdjustmentResponse> rejectAdjustment(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryAdjustmentRejectionRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectAdjustment(id, request);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryAdjustmentResponse> getAdjustment(@PathVariable UUID id) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getAdjustment(id);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<InventoryAdjustmentListResponse> getAdjustments() {
        InventoryAdjustmentListResponse response = inventoryAdjustmentService.getAdjustments();
        return ApiResponse.success(response);
    }

    @GetMapping("/history")
    public ApiResponse<AdjustmentHistoryListResponse> getAdjustmentHistory() {
        AdjustmentHistoryListResponse response = inventoryAdjustmentService.getAdjustmentHistory();
        return ApiResponse.success(response);
    }

    @DeleteMapping("/old")
    public ApiResponse<Map<String, Object>> deleteOldAdjustments() {
        Map<String, Object> result = inventoryAdjustmentService.deleteOldAdjustments();
        return ApiResponse.success(result);
    }
}
