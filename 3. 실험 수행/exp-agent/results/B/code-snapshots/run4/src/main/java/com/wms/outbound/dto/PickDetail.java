package com.wms.outbound.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickDetail {
    private UUID locationId;
    private String locationCode;
    private UUID inventoryId;
    private String lotNumber;
    private Integer pickedQty;
}
