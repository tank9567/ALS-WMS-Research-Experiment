package com.wms.inbound.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInboundReceiptRequest {
    private UUID poId;
    private String receivedBy;
    private List<InboundReceiptLineRequest> lines;
}
