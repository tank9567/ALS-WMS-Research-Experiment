package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    /**
     * POST /api/v1/cycle-counts - 실사 시작
     */
    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @RequestBody CycleCountRequest request
    ) {
        CycleCountResponse response = inventoryAdjustmentService.startCycleCount(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/cycle-counts/{id}/complete - 실사 완료
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable UUID id,
            @RequestBody CycleCountCompleteRequest request
    ) {
        CycleCountResponse response = inventoryAdjustmentService.completeCycleCount(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inventory-adjustments - 재고 조정 생성
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createAdjustment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/approve - 재고 조정 승인
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestParam String approvedBy
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveAdjustment(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/reject - 재고 조정 거부
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestParam String approvedBy
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectAdjustment(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inventory-adjustments/{id} - 재고 조정 상세 조회
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable UUID id
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inventory-adjustments - 재고 조정 목록 조회
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<Page<InventoryAdjustmentResponse>>> getAdjustments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InventoryAdjustmentResponse> response = inventoryAdjustmentService.getAdjustments(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
