package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
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
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts
     * 입고 등록 (inspecting 상태)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
        @Valid @RequestBody InboundReceiptRequest request
    ) {
        try {
            log.info("POST /inbound-receipts - 입고 등록 요청");
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 등록 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("입고 등록 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 등록 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm
     * 입고 확정 (재고 반영)
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
        @PathVariable("id") UUID receiptId
    ) {
        try {
            log.info("POST /inbound-receipts/{}/confirm - 입고 확정 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 확정 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("입고 확정 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 확정 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject
     * 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
        @PathVariable("id") UUID receiptId,
        @RequestParam(required = false) String reason
    ) {
        try {
            log.info("POST /inbound-receipts/{}/reject - 입고 거부 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 거부 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("입고 거부 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 거부 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve
     * 유통기한 경고 승인 (pending_approval -> confirmed)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
        @PathVariable("id") UUID receiptId
    ) {
        try {
            log.info("POST /inbound-receipts/{}/approve - 유통기한 경고 승인 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("유통기한 경고 승인 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("유통기한 경고 승인 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("유통기한 경고 승인 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/inbound-receipts/{id}
     * 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
        @PathVariable("id") UUID receiptId
    ) {
        try {
            log.info("GET /inbound-receipts/{} - 입고 상세 조회 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 조회 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/inbound-receipts
     * 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            log.info("GET /inbound-receipts - 입고 목록 조회 요청");
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            log.error("입고 목록 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }
}
