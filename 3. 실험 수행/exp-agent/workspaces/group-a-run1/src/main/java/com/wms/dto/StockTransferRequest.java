package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer transferQty;
    private String reason;
    private String requestedBy;
}
