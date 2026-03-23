package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptListResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InboundReceiptResponse> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<InboundReceiptResponse> confirmInboundReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<InboundReceiptResponse> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam String reason) {
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<InboundReceiptResponse> approveShelfLife(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.approveShelfLife(id);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<InboundReceiptResponse> getInboundReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<InboundReceiptListResponse>> getInboundReceipts() {
        List<InboundReceiptListResponse> response = inboundReceiptService.getInboundReceipts();
        return ApiResponse.success(response);
    }
}
