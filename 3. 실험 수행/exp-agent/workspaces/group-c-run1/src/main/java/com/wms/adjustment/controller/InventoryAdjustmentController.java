package com.wms.adjustment.controller;

import com.wms.adjustment.dto.*;
import com.wms.adjustment.service.InventoryAdjustmentService;
import com.wms.inbound.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    /**
     * 실사 시작
     * POST /api/v1/cycle-counts
     */
    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @RequestBody CycleCountStartRequest request) {
        try {
            CycleCountResponse response = adjustmentService.startCycleCount(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("실사 시작 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 실사 완료
     * POST /api/v1/cycle-counts/{id}/complete
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable("id") UUID cycleCountId) {
        try {
            CycleCountResponse response = adjustmentService.completeCycleCount(cycleCountId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("실사 완료 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 생성
     * POST /api/v1/inventory-adjustments
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @RequestBody InventoryAdjustmentCreateRequest request) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 승인
     * POST /api/v1/inventory-adjustments/{id}/approve
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @RequestBody InventoryAdjustmentApprovalRequest request) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 승인 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 거부
     * POST /api/v1/inventory-adjustments/{id}/reject
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @RequestBody InventoryAdjustmentApprovalRequest request) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 거부 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 상세 조회
     * GET /api/v1/inventory-adjustments/{id}
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable("id") UUID adjustmentId) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.getAdjustment(adjustmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 목록 조회
     * GET /api/v1/inventory-adjustments
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustments(
            @RequestParam(required = false) String status) {
        try {
            List<InventoryAdjustmentResponse> response = adjustmentService.getAdjustments(status);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
