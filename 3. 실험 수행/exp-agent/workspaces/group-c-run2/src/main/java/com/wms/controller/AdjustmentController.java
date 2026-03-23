package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.AdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
        @RequestBody CycleCountRequest request
    ) {
        try {
            CycleCountResponse response = adjustmentService.startCycleCount(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "CYCLE_COUNT_START_FAILED"));
        }
    }

    /**
     * POST /api/v1/cycle-counts/{id}/complete
     * 실사 완료
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
        @PathVariable("id") UUID cycleCountId,
        @RequestBody CompleteCycleCountRequest request
    ) {
        try {
            CycleCountResponse response = adjustmentService.completeCycleCount(cycleCountId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "CYCLE_COUNT_COMPLETE_FAILED"));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments
     * 조정 생성
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
        @RequestBody InventoryAdjustmentRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_CREATE_FAILED"));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/approve
     * 조정 승인
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody AdjustmentApprovalRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_APPROVE_FAILED"));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/reject
     * 조정 거부
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody AdjustmentApprovalRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_REJECT_FAILED"));
        }
    }

    /**
     * GET /api/v1/inventory-adjustments/{id}
     * 조정 상세 조회
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
        @PathVariable("id") UUID adjustmentId
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.getAdjustment(adjustmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_GET_FAILED"));
        }
    }

    /**
     * GET /api/v1/inventory-adjustments
     * 조정 목록 조회
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> listAdjustments() {
        try {
            List<InventoryAdjustmentResponse> responses = adjustmentService.listAdjustments();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_LIST_FAILED"));
        }
    }
}
