package com.wms.transfer.controller;

import com.wms.inbound.dto.ApiResponse;
import com.wms.transfer.dto.StockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.service.StockTransferService;
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
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
        @RequestBody StockTransferRequest request
    ) {
        try {
            StockTransferResponse response = stockTransferService.executeTransfer(request);
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
        @PathVariable("id") UUID transferId,
        @RequestParam("approvedBy") String approvedBy
    ) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(transferId, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
        @PathVariable("id") UUID transferId,
        @RequestParam("approvedBy") String approvedBy
    ) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
        @PathVariable("id") UUID transferId
    ) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(transferId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 이동 이력 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(Pageable pageable) {
        try {
            Page<StockTransferResponse> response = stockTransferService.getTransfers(pageable);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }
}
