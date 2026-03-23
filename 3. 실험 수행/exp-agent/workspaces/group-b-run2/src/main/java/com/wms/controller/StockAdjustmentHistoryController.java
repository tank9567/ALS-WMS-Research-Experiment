package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.service.StockAdjustmentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stock-adjustment-history")
@RequiredArgsConstructor
public class StockAdjustmentHistoryController {

    private final StockAdjustmentHistoryService historyService;

    /**
     * 오래된 조정 이력 삭제 (1년 이상)
     * DELETE /api/v1/stock-adjustment-history/old
     */
    @DeleteMapping("/old")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteOldHistory() {
        int deletedCount = historyService.deleteOldHistory();

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("message", String.format("Deleted %d old history records (older than 1 year)", deletedCount));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 특정 날짜 이전의 조정 이력 삭제
     * DELETE /api/v1/stock-adjustment-history/before?cutoffDate=2023-01-01T00:00:00Z
     */
    @DeleteMapping("/before")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteHistoryBefore(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cutoffDate) {
        int deletedCount = historyService.deleteHistoryBefore(cutoffDate);

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffDate", cutoffDate.toString());
        result.put("message", String.format("Deleted %d history records before %s", deletedCount, cutoffDate));

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
