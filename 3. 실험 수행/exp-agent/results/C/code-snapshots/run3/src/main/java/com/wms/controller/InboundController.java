package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.entity.InboundReceipt;
import com.wms.service.InboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    /**
     * 입고 등록 (inspecting 상태)
     * POST /api/v1/inbound-receipts
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceipt>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceipt receipt = inboundService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 확정
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceipt>> confirmReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.confirmReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 확정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 거부
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceipt>> rejectReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.rejectReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 거부 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 유통기한 경고 승인
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceipt>> approveShelfLifeWarning(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.approveShelfLifeWarning(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("승인 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceipt>> getReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.getReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceipt>>> getAllReceipts() {
        try {
            List<InboundReceipt> receipts = inboundService.getAllReceipts();
            return ResponseEntity.ok(ApiResponse.success(receipts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
