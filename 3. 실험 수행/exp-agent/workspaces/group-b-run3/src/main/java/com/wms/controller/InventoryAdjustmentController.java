package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InventoryAdjustmentApprovalRequest;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.InventoryAdjustment;
import com.wms.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    /**
     * 재고 조정 생성
     * POST /api/v1/inventory-adjustments
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryAdjustmentResponse> createAdjustment(
        @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryAdjustment adjustment = adjustmentService.createAdjustment(
            request.getProductId(),
            request.getLocationId(),
            request.getActualQty(),
            request.getReason(),
            request.getAdjustedBy()
        );
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 승인
     * POST /api/v1/inventory-adjustments/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<InventoryAdjustmentResponse> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody InventoryAdjustmentApprovalRequest request
    ) {
        InventoryAdjustment adjustment = adjustmentService.approveAdjustment(
            adjustmentId,
            request.getApprovedBy()
        );
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 거부
     * POST /api/v1/inventory-adjustments/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<InventoryAdjustmentResponse> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody InventoryAdjustmentApprovalRequest request
    ) {
        InventoryAdjustment adjustment = adjustmentService.rejectAdjustment(
            adjustmentId,
            request.getApprovedBy()
        );
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 상세 조회
     * GET /api/v1/inventory-adjustments/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<InventoryAdjustmentResponse> getAdjustment(
        @PathVariable("id") UUID adjustmentId
    ) {
        InventoryAdjustment adjustment = adjustmentService.getAdjustment(adjustmentId);
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 목록 조회
     * GET /api/v1/inventory-adjustments
     */
    @GetMapping
    public ApiResponse<List<InventoryAdjustmentResponse>> getAllAdjustments(
        @RequestParam(value = "status", required = false) String status
    ) {
        List<InventoryAdjustment> adjustments;

        if ("pending".equalsIgnoreCase(status)) {
            adjustments = adjustmentService.getPendingAdjustments();
        } else {
            adjustments = adjustmentService.getAllAdjustments();
        }

        List<InventoryAdjustmentResponse> responses = adjustments.stream()
            .map(InventoryAdjustmentResponse::from)
            .collect(Collectors.toList());

        return ApiResponse.success(responses);
    }
}
