package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountRequest {
    private UUID locationId;
    private UUID productId;
    private String lotNumber;
    private LocalDate expiryDate;
    private String countedBy;
}
