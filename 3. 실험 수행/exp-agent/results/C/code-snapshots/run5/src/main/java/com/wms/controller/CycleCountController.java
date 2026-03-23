package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.service.CycleCountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cycle-counts")
public class CycleCountController {

    private final CycleCountService cycleCountService;

    public CycleCountController(CycleCountService cycleCountService) {
        this.cycleCountService = cycleCountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
        @RequestBody CycleCountRequest request
    ) {
        CycleCountResponse response = cycleCountService.startCycleCount(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
        @PathVariable("id") UUID cycleCountId
    ) {
        CycleCountResponse response = cycleCountService.completeCycleCount(cycleCountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
