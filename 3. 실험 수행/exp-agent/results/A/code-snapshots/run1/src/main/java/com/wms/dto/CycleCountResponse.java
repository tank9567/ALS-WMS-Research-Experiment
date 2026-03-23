package com.wms.dto;

import com.wms.entity.CycleCount.CycleCountStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {
    private UUID id;
    private UUID locationId;
    private String locationCode;
    private UUID productId;
    private String productSku;
    private String productName;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer systemQty;
    private Integer countedQty;
    private CycleCountStatus status;
    private String countedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
