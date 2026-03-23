package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        log.info("Creating inbound receipt for PO: {}", request.getPoId());
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        log.info("Confirming inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable("id") UUID receiptId,
            @RequestParam(required = false) String reason) {
        log.info("Rejecting inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId, reason);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        log.info("Approving inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(receiptId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        log.info("Getting inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getAllInboundReceipts(
            Pageable pageable) {
        log.info("Getting all inbound receipts with pagination: {}", pageable);
        Page<InboundReceiptResponse> response = inboundReceiptService.getAllInboundReceipts(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}
