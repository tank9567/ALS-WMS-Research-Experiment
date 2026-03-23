package com.wms.service;

import com.wms.dto.AdjustmentHistoryResponse;
import com.wms.entity.AdjustmentHistory;
import com.wms.repository.AdjustmentHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdjustmentHistoryService {

    private final AdjustmentHistoryRepository adjustmentHistoryRepository;

    public List<AdjustmentHistoryResponse> getHistoriesByProduct(UUID productId) {
        return adjustmentHistoryRepository.findByProductIdOrderByAdjustedAtDesc(productId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<AdjustmentHistoryResponse> getHistoriesByLocation(UUID locationId) {
        return adjustmentHistoryRepository.findByLocationIdOrderByAdjustedAtDesc(locationId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<AdjustmentHistoryResponse> getHistoriesByDateRange(
            OffsetDateTime startDate,
            OffsetDateTime endDate
    ) {
        return adjustmentHistoryRepository.findByAdjustedAtBetweenOrderByAdjustedAtDesc(startDate, endDate)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public int deleteOldHistories(int yearsOld) {
        OffsetDateTime cutoffDate = OffsetDateTime.now().minusYears(yearsOld);
        return adjustmentHistoryRepository.deleteOldHistories(cutoffDate);
    }

    public long countOldHistories(int yearsOld) {
        OffsetDateTime cutoffDate = OffsetDateTime.now().minusYears(yearsOld);
        return adjustmentHistoryRepository.countOldHistories(cutoffDate);
    }

    @Transactional
    public void createHistory(AdjustmentHistory history) {
        adjustmentHistoryRepository.save(history);
    }

    private AdjustmentHistoryResponse convertToResponse(AdjustmentHistory history) {
        return AdjustmentHistoryResponse.builder()
                .id(history.getId())
                .productId(history.getProduct().getId())
                .productCode(history.getProduct().getCode())
                .productName(history.getProduct().getName())
                .locationId(history.getLocation().getId())
                .locationCode(history.getLocation().getCode())
                .locationName(history.getLocation().getName())
                .lotNumber(history.getLotNumber())
                .expiryDate(history.getExpiryDate())
                .beforeQty(history.getBeforeQty())
                .afterQty(history.getAfterQty())
                .differenceQty(history.getDifferenceQty())
                .reason(history.getReason())
                .adjustmentType(history.getAdjustmentType())
                .referenceId(history.getReferenceId())
                .adjustedBy(history.getAdjustedBy())
                .adjustedAt(history.getAdjustedAt())
                .build();
    }
}
