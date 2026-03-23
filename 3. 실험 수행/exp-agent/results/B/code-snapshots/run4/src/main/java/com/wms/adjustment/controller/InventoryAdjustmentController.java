package com.wms.adjustment.controller;

import com.wms.adjustment.dto.ApprovalRequest;
import com.wms.adjustment.dto.CreateAdjustmentRequest;
import com.wms.adjustment.entity.InventoryAdjustment;
import com.wms.adjustment.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Map<String, Object>> createAdjustment(@RequestBody CreateAdjustmentRequest request) {
        try {
            InventoryAdjustment adjustment = adjustmentService.createAdjustment(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", adjustment);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "ADJUSTMENT_VALIDATION_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @RequestBody ApprovalRequest request) {
        try {
            InventoryAdjustment adjustment = adjustmentService.approveAdjustment(adjustmentId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", adjustment);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "APPROVAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @RequestBody ApprovalRequest request) {
        try {
            InventoryAdjustment adjustment = adjustmentService.rejectAdjustment(adjustmentId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", adjustment);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "REJECTION_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAdjustment(@PathVariable("id") UUID adjustmentId) {
        try {
            InventoryAdjustment adjustment = adjustmentService.getAdjustment(adjustmentId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", adjustment);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "NOT_FOUND");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAdjustments() {
        try {
            List<InventoryAdjustment> adjustments = adjustmentService.getAllAdjustments();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", adjustments);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/old")
    public ResponseEntity<Map<String, Object>> getOldAdjustments(
            @RequestParam(value = "years", defaultValue = "1") int years) {
        try {
            List<InventoryAdjustment> adjustments = adjustmentService.getOldAdjustments(years);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", adjustments);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @DeleteMapping("/old")
    public ResponseEntity<Map<String, Object>> deleteOldAdjustments(
            @RequestParam(value = "years", defaultValue = "1") int years) {
        try {
            Map<String, Object> deleteResult = adjustmentService.deleteOldAdjustments(years);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", deleteResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
