package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    /**
     * 입고 등록 (inspecting 상태)
     * POST /api/v1/inbound-receipts
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        InboundReceiptResponse response = inboundService.createReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 입고 확정 (confirmed)
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.confirmReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 거부 (rejected)
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.rejectReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.approveReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.getReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getReceipts(Pageable pageable) {
        Page<InboundReceiptResponse> response = inboundService.getReceipts(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
