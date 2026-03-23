package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.exception.BusinessException;
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

    /**
     * POST /api/v1/stock-transfers - 재고 이동 실행
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        try {
            StockTransferResponse response = stockTransferService.executeTransfer(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/approve - 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/reject - 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/stock-transfers/{id} - 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/stock-transfers - 이동 이력 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        try {
            List<StockTransferResponse> responses = stockTransferService.getAllTransfers();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (BusinessException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 비즈니스 예외 처리
     */
    private <T> ResponseEntity<ApiResponse<T>> handleBusinessException(BusinessException e) {
        HttpStatus status;

        switch (e.getCode()) {
            case "NOT_FOUND":
                status = HttpStatus.NOT_FOUND;
                break;
            case "SAME_LOCATION":
            case "INSUFFICIENT_STOCK":
            case "CAPACITY_EXCEEDED":
            case "STORAGE_INCOMPATIBLE":
            case "HAZMAT_MIXING_FORBIDDEN":
            case "EXPIRED_PRODUCT":
            case "EXPIRY_TRANSFER_RESTRICTED":
            case "LOCATION_FROZEN":
            case "INVALID_STATUS":
                status = HttpStatus.CONFLICT;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 전역 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
    }
}
