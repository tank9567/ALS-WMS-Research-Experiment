package com.wms.transfer.controller;

import com.wms.transfer.dto.ApprovalRequest;
import com.wms.transfer.dto.CreateStockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTransfer(@RequestBody CreateStockTransferRequest request) {
        try {
            StockTransferResponse response = stockTransferService.createTransfer(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "TRANSFER_VALIDATION_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveTransfer(
        @PathVariable("id") UUID transferId,
        @RequestBody ApprovalRequest request
    ) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(transferId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "APPROVAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectTransfer(
        @PathVariable("id") UUID transferId,
        @RequestBody ApprovalRequest request
    ) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "REJECTION_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTransfer(@PathVariable("id") UUID transferId) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(transferId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "NOT_FOUND");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTransfers() {
        try {
            List<StockTransferResponse> responses = stockTransferService.getAllTransfers();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", responses);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
