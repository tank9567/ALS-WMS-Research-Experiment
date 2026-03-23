package com.wms.controller;

import com.wms.dto.AdjustmentApprovalRequest;
import com.wms.dto.ApiResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
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
            @RequestBody InventoryAdjustmentRequest request) {
        InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody AdjustmentApprovalRequest request) {
        InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestBody AdjustmentApprovalRequest request) {
        InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable UUID id) {
        InventoryAdjustmentResponse response = adjustmentService.getAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> listAdjustments() {
        List<InventoryAdjustmentResponse> responses = adjustmentService.listAdjustments();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/by-product/{productId}")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustmentsByProduct(
            @PathVariable UUID productId) {
        List<InventoryAdjustmentResponse> responses = adjustmentService.getAdjustmentsByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/by-location/{locationId}")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustmentsByLocation(
            @PathVariable UUID locationId) {
        List<InventoryAdjustmentResponse> responses = adjustmentService.getAdjustmentsByLocationId(locationId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/by-date-range")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustmentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {
        List<InventoryAdjustmentResponse> responses = adjustmentService.getAdjustmentsByDateRange(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @DeleteMapping("/delete-old")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteOldAdjustments(
            @RequestParam(defaultValue = "365") int daysOld) {
        OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(daysOld);
        int deletedCount = adjustmentService.deleteOldAdjustments(cutoffDate);

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffDate", cutoffDate);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
