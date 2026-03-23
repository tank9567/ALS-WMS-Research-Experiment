package com.wms.outbound.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickResultDto {
    private UUID shipmentLineId;
    private UUID productId;
    private String productSku;
    private Integer requestedQty;
    private Integer pickedQty;
    private String status;
    private List<PickDetail> pickDetails;
    private BackorderDto backorder;
}
