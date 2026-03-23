package com.wms.service;

import com.wms.entity.InventoryAdjustment;
import com.wms.entity.StockAdjustmentHistory;
import com.wms.exception.BusinessException;
import com.wms.repository.StockAdjustmentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAdjustmentHistoryService {

    private final StockAdjustmentHistoryRepository historyRepository;

    /**
     * 재고 조정 이력 기록
     */
    @Transactional
    public void recordHistory(InventoryAdjustment adjustment) {
        StockAdjustmentHistory history = StockAdjustmentHistory.builder()
                .adjustmentId(adjustment.getAdjustmentId())
                .product(adjustment.getProduct())
                .location(adjustment.getLocation())
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .difference(adjustment.getDifference())
                .reason(adjustment.getReason())
                .adjustedBy(adjustment.getAdjustedBy())
                .approvedBy(adjustment.getApprovedBy())
                .approvalStatus(StockAdjustmentHistory.ApprovalStatus.valueOf(
                        adjustment.getApprovalStatus().name()))
                .approvedAt(adjustment.getApprovedAt())
                .build();

        historyRepository.save(history);
        log.info("Stock adjustment history recorded: adjustmentId={}", adjustment.getAdjustmentId());
    }

    /**
     * 오래된 조정 이력 삭제 (1년 이상)
     */
    @Transactional
    public int deleteOldHistory() {
        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        int deletedCount = historyRepository.deleteByCreatedAtBefore(oneYearAgo);
        log.info("Deleted {} old stock adjustment history records (older than 1 year)", deletedCount);
        return deletedCount;
    }

    /**
     * 특정 기간 이전의 조정 이력 삭제
     */
    @Transactional
    public int deleteHistoryBefore(Instant cutoffDate) {
        if (cutoffDate.isAfter(Instant.now())) {
            throw new BusinessException("INVALID_DATE", "Cutoff date cannot be in the future");
        }

        int deletedCount = historyRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Deleted {} stock adjustment history records before {}", deletedCount, cutoffDate);
        return deletedCount;
    }
}
