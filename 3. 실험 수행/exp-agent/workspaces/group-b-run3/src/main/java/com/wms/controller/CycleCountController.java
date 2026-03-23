package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.CycleCountStartRequest;
import com.wms.entity.CycleCount;
import com.wms.service.CycleCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final CycleCountService cycleCountService;

    /**
     * 실사 시작
     * POST /api/v1/cycle-counts
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CycleCountResponse> startCycleCount(
        @RequestBody CycleCountStartRequest request
    ) {
        CycleCount cycleCount = cycleCountService.startCycleCount(
            request.getLocationId(),
            request.getStartedBy()
        );
        return ApiResponse.success(CycleCountResponse.from(cycleCount));
    }

    /**
     * 실사 완료
     * POST /api/v1/cycle-counts/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<CycleCountResponse> completeCycleCount(
        @PathVariable("id") UUID cycleCountId
    ) {
        CycleCount cycleCount = cycleCountService.completeCycleCount(cycleCountId);
        return ApiResponse.success(CycleCountResponse.from(cycleCount));
    }

    /**
     * 실사 상세 조회
     * GET /api/v1/cycle-counts/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<CycleCountResponse> getCycleCount(
        @PathVariable("id") UUID cycleCountId
    ) {
        CycleCount cycleCount = cycleCountService.getCycleCount(cycleCountId);
        return ApiResponse.success(CycleCountResponse.from(cycleCount));
    }

    /**
     * 실사 목록 조회
     * GET /api/v1/cycle-counts
     */
    @GetMapping
    public ApiResponse<List<CycleCountResponse>> getAllCycleCounts() {
        List<CycleCount> cycleCounts = cycleCountService.getAllCycleCounts();
        List<CycleCountResponse> responses = cycleCounts.stream()
            .map(CycleCountResponse::from)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
}
