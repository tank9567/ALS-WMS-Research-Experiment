package com.wms.inbound.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptLineRequest {
    private UUID productId;
    private UUID locationId;
    private Integer quantity;
    private String lotNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
}
