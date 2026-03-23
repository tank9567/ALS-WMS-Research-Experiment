package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.service.CycleCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final CycleCountService cycleCountService;

    @PostMapping
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        CycleCountResponse response = cycleCountService.startCycleCount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(@PathVariable UUID id) {
        CycleCountResponse response = cycleCountService.completeCycleCount(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CycleCountResponse>> getCycleCount(@PathVariable UUID id) {
        CycleCountResponse response = cycleCountService.getCycleCount(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
