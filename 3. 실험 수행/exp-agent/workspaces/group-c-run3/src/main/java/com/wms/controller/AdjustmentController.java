package com.wms.controller;

import com.wms.dto.AdjustmentApprovalRequest;
import com.wms.dto.AdjustmentCreateRequest;
import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountStartRequest;
import com.wms.entity.InventoryAdjustment;
import com.wms.service.AdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    /**
     * POST /api/v1/cycle-counts
     * 실사 시작
     */
    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startCycleCount(
        @RequestBody CycleCountStartRequest request
    ) {
        try {
            Map<String, Object> result = adjustmentService.startCycleCount(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/v1/cycle-counts/{id}/complete
     * 실사 완료
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeCycleCount(
        @PathVariable("id") UUID cycleCountId
    ) {
        try {
            Map<String, Object> result = adjustmentService.completeCycleCount(cycleCountId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments
     * 조정 생성
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAdjustment(
        @RequestBody AdjustmentCreateRequest request
    ) {
        try {
            Map<String, Object> result = adjustmentService.createAdjustment(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/approve
     * 조정 승인
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody AdjustmentApprovalRequest request
    ) {
        try {
            Map<String, Object> result = adjustmentService.approveAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/reject
     * 조정 거부
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody AdjustmentApprovalRequest request
    ) {
        try {
            Map<String, Object> result = adjustmentService.rejectAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/inventory-adjustments/{id}
     * 조정 상세 조회
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdjustment(
        @PathVariable("id") UUID adjustmentId
    ) {
        try {
            Map<String, Object> result = adjustmentService.getAdjustment(adjustmentId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/inventory-adjustments
     * 조정 목록 조회
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustment>>> getAdjustments() {
        try {
            List<InventoryAdjustment> result = adjustmentService.getAdjustments();
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }
}
