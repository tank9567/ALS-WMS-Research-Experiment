package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    /**
     * 재고 이동 실행
     * POST /api/v1/stock-transfers
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> transferStock(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.transferStock(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 대량 이동 승인
     * POST /api/v1/stock-transfers/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 대량 이동 거부
     * POST /api/v1/stock-transfers/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이동 상세 조회
     * GET /api/v1/stock-transfers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransfer(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이동 이력 조회
     * GET /api/v1/stock-transfers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(Pageable pageable) {
        Page<StockTransferResponse> response = stockTransferService.getTransfers(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
