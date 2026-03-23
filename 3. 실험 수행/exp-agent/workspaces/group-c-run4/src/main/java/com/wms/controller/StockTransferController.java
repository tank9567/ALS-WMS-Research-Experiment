package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    /**
     * POST /api/v1/stock-transfers
     * 재고 이동 실행
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @Valid @RequestBody StockTransferRequest request
    ) {
        try {
            log.info("POST /api/v1/stock-transfers - 재고 이동 요청");
            StockTransferResponse response = stockTransferService.executeTransfer(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("재고 이동 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("재고 이동 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("재고 이동 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/approve
     * 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody ApprovalRequest request
    ) {
        try {
            log.info("POST /api/v1/stock-transfers/{}/approve - 이동 승인 요청", transferId);
            StockTransferResponse response = stockTransferService.approveTransfer(transferId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("이동 승인 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("이동 승인 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("이동 승인 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/reject
     * 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody ApprovalRequest request
    ) {
        try {
            log.info("POST /api/v1/stock-transfers/{}/reject - 이동 거부 요청", transferId);
            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("이동 거부 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("이동 거부 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("이동 거부 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/stock-transfers/{id}
     * 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
            @PathVariable("id") UUID transferId
    ) {
        try {
            log.info("GET /api/v1/stock-transfers/{} - 이동 상세 조회 요청", transferId);
            StockTransferResponse response = stockTransferService.getTransfer(transferId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("이동 조회 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("이동 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/stock-transfers
     * 이동 이력 조회 (필터링 지원)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getTransfers(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID fromLocationId,
            @RequestParam(required = false) UUID toLocationId,
            @RequestParam(required = false) String status
    ) {
        try {
            log.info("GET /api/v1/stock-transfers - 이동 이력 조회 요청 (productId={}, fromLocationId={}, toLocationId={}, status={})",
                    productId, fromLocationId, toLocationId, status);
            List<StockTransferResponse> responses = stockTransferService.getTransfers(productId, fromLocationId, toLocationId, status);
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            log.error("이동 이력 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }
}
