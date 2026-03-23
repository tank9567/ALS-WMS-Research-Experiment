package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustmentHistoryResponse {
    private UUID id;
    private UUID productId;
    private String productCode;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private String locationName;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer beforeQty;
    private Integer afterQty;
    private Integer differenceQty;
    private String reason;
    private String adjustmentType;
    private UUID referenceId;
    private String adjustedBy;
    private OffsetDateTime adjustedAt;
}
