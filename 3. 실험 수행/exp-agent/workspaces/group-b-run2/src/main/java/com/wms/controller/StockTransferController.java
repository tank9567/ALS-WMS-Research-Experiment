package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
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
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        log.info("Creating stock transfer: product={}, from={}, to={}, qty={}",
                request.getProductId(), request.getFromLocationId(), request.getToLocationId(), request.getQuantity());
        StockTransferResponse response = stockTransferService.createStockTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveStockTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String approvedBy) {
        log.info("Approving stock transfer: {}", transferId);
        StockTransferResponse response = stockTransferService.approveStockTransfer(transferId, approvedBy);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectStockTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String rejectedBy,
            @RequestParam(required = false) String reason) {
        log.info("Rejecting stock transfer: {}", transferId);
        StockTransferResponse response = stockTransferService.rejectStockTransfer(transferId, rejectedBy, reason);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getStockTransfer(
            @PathVariable("id") UUID transferId) {
        log.info("Getting stock transfer: {}", transferId);
        StockTransferResponse response = stockTransferService.getStockTransfer(transferId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getAllStockTransfers(
            Pageable pageable) {
        log.info("Getting all stock transfers with pagination: {}", pageable);
        Page<StockTransferResponse> response = stockTransferService.getAllStockTransfers(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}
