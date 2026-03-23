package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferListResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StockTransferResponse> executeTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.executeTransfer(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<StockTransferResponse> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<StockTransferResponse> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String rejectedBy,
            @RequestParam String reason) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, rejectedBy, reason);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<StockTransferResponse> getTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransfer(id);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<StockTransferListResponse>> getTransfers() {
        List<StockTransferListResponse> response = stockTransferService.getTransfers();
        return ApiResponse.success(response);
    }
}
