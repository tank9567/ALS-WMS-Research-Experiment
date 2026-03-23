package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.TransferApprovalRequest;
import com.wms.entity.StockTransfer;
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

    /**
     * 재고 이동 실행
     * POST /api/v1/stock-transfers
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransfer>> transferStock(
            @RequestBody StockTransferRequest request) {
        try {
            StockTransfer transfer = stockTransferService.transferStock(
                    request.getFromLocationId(),
                    request.getToLocationId(),
                    request.getProductId(),
                    request.getLotNumber(),
                    request.getQuantity(),
                    request.getRequestedBy()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(transfer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 이동 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 대량 이동 승인
     * POST /api/v1/stock-transfers/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransfer>> approveTransfer(
            @PathVariable UUID id,
            @RequestBody TransferApprovalRequest request) {
        try {
            StockTransfer transfer = stockTransferService.approveTransfer(id, request.getApprovedBy());
            return ResponseEntity.ok(ApiResponse.success(transfer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("승인 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 대량 이동 거부
     * POST /api/v1/stock-transfers/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransfer>> rejectTransfer(
            @PathVariable UUID id,
            @RequestBody TransferApprovalRequest request) {
        try {
            StockTransfer transfer = stockTransferService.rejectTransfer(id, request.getApprovedBy());
            return ResponseEntity.ok(ApiResponse.success(transfer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("거부 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 이동 상세 조회
     * GET /api/v1/stock-transfers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransfer>> getTransfer(@PathVariable UUID id) {
        try {
            StockTransfer transfer = stockTransferService.getTransfer(id);
            return ResponseEntity.ok(ApiResponse.success(transfer));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 이동 이력 조회
     * GET /api/v1/stock-transfers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransfer>>> getAllTransfers() {
        try {
            List<StockTransfer> transfers = stockTransferService.getAllTransfers();
            return ResponseEntity.ok(ApiResponse.success(transfers));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
