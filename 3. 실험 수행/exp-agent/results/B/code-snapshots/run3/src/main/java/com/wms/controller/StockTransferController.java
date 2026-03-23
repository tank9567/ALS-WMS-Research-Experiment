package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.createStockTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody StockTransferApprovalRequest request) {
        StockTransferResponse response = stockTransferService.approveTransfer(transferId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody StockTransferApprovalRequest request) {
        StockTransferResponse response = stockTransferService.rejectTransfer(transferId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getStockTransfer(
            @PathVariable("id") UUID transferId) {
        StockTransferResponse response = stockTransferService.getStockTransfer(transferId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllStockTransfers() {
        List<StockTransferResponse> responses = stockTransferService.getAllStockTransfers();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
