package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
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
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.executeTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransferById(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransferById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        List<StockTransferResponse> response = stockTransferService.getAllTransfers();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
