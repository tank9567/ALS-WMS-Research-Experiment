package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.InventoryAdjustment;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
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

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createAdjustment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        String approvedBy = payload.getOrDefault("approvedBy", "ADMIN");
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveAdjustment(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        String rejectedBy = payload.getOrDefault("rejectedBy", "ADMIN");
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectAdjustment(id, rejectedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(@PathVariable UUID id) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustments(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) InventoryAdjustment.ApprovalStatus approvalStatus) {
        List<InventoryAdjustmentResponse> responses = inventoryAdjustmentService.getAdjustments(
                productId, locationId, approvalStatus);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @DeleteMapping("/history/old")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteOldHistory(
            @RequestParam(defaultValue = "1") int years) {
        int deletedCount = inventoryAdjustmentService.deleteOldAdjustmentHistory(years);
        Map<String, Object> result = Map.of(
                "deletedCount", deletedCount,
                "years", years,
                "message", deletedCount + " records deleted (older than " + years + " year(s))"
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
