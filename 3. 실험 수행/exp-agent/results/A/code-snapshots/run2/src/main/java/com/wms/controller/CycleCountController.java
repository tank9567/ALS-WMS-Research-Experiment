package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountCompleteRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CycleCountResponse> startCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        CycleCountResponse response = inventoryAdjustmentService.startCycleCount(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<CycleCountResponse> completeCycleCount(
            @PathVariable UUID id,
            @Valid @RequestBody CycleCountCompleteRequest request) {
        CycleCountResponse response = inventoryAdjustmentService.completeCycleCount(id, request);
        return ApiResponse.success(response);
    }
}
