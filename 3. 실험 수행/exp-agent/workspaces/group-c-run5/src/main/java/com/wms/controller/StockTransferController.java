package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @RequestBody StockTransferRequest request
    ) {
        StockTransferResponse response = stockTransferService.createStockTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String approvedBy
    ) {
        StockTransferResponse response = stockTransferService.approveTransfer(transferId, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String rejectedBy
    ) {
        StockTransferResponse response = stockTransferService.rejectTransfer(transferId, rejectedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
            @PathVariable("id") UUID transferId
    ) {
        StockTransferResponse response = stockTransferService.getTransfer(transferId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getTransferHistory(
            @RequestParam(required = false) UUID productId
    ) {
        List<StockTransferResponse> responses = stockTransferService.getTransferHistory(productId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
