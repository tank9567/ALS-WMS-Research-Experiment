package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundLineRequest {
    private UUID productId;
    private UUID locationId;
    private Integer quantity;
    private String lotNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
}
