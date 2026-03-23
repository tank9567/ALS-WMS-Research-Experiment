package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderListResponse {
    private UUID id;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedDate;
    private Instant shippedAt;
    private Instant createdAt;
}
