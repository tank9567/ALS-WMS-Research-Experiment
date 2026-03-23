package com.wms.adjustment.controller;

import com.wms.common.entity.CycleCount;
import com.wms.adjustment.dto.CreateCycleCountRequest;
import com.wms.adjustment.service.CycleCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final CycleCountService cycleCountService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> startCycleCount(@RequestBody CreateCycleCountRequest request) {
        try {
            CycleCount cycleCount = cycleCountService.startCycleCount(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", cycleCount);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "CYCLE_COUNT_VALIDATION_ERROR");
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

    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeCycleCount(@PathVariable("id") UUID cycleCountId) {
        try {
            CycleCount cycleCount = cycleCountService.completeCycleCount(cycleCountId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", cycleCount);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "CYCLE_COUNT_ERROR");
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCycleCount(@PathVariable("id") UUID cycleCountId) {
        try {
            CycleCount cycleCount = cycleCountService.getCycleCount(cycleCountId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", cycleCount);
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
}
