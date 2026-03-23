package com.wms.controller;

import com.wms.dto.AdjustmentHistoryResponse;
import com.wms.dto.ApiResponse;
import com.wms.service.AdjustmentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/adjustment-histories")
@RequiredArgsConstructor
public class AdjustmentHistoryController {

    private final AdjustmentHistoryService adjustmentHistoryService;

    @GetMapping("/product/{productId}")
    public ApiResponse<List<AdjustmentHistoryResponse>> getHistoriesByProduct(
            @PathVariable UUID productId
    ) {
        List<AdjustmentHistoryResponse> histories = adjustmentHistoryService.getHistoriesByProduct(productId);
        return ApiResponse.success(histories);
    }

    @GetMapping("/location/{locationId}")
    public ApiResponse<List<AdjustmentHistoryResponse>> getHistoriesByLocation(
            @PathVariable UUID locationId
    ) {
        List<AdjustmentHistoryResponse> histories = adjustmentHistoryService.getHistoriesByLocation(locationId);
        return ApiResponse.success(histories);
    }

    @GetMapping("/date-range")
    public ApiResponse<List<AdjustmentHistoryResponse>> getHistoriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate
    ) {
        List<AdjustmentHistoryResponse> histories = adjustmentHistoryService.getHistoriesByDateRange(startDate, endDate);
        return ApiResponse.success(histories);
    }

    @GetMapping("/old-count")
    public ApiResponse<Map<String, Long>> countOldHistories(
            @RequestParam(defaultValue = "1") int yearsOld
    ) {
        long count = adjustmentHistoryService.countOldHistories(yearsOld);
        Map<String, Long> result = new HashMap<>();
        result.put("count", count);
        result.put("yearsOld", (long) yearsOld);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/old")
    public ApiResponse<Map<String, Integer>> deleteOldHistories(
            @RequestParam(defaultValue = "1") int yearsOld
    ) {
        int deletedCount = adjustmentHistoryService.deleteOldHistories(yearsOld);
        Map<String, Integer> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("yearsOld", yearsOld);
        return ApiResponse.success(result);
    }
}
