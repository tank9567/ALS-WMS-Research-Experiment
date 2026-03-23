package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts
     * 입고 등록 (검수 시작)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request
    ) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm
     * 입고 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject
     * 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam String reason
    ) {
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve
     * 유통기한 경고 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inbound-receipts/{id}
     * 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inbound-receipts
     * 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getInboundReceipts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InboundReceiptResponse> response = inboundReceiptService.getInboundReceipts(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
