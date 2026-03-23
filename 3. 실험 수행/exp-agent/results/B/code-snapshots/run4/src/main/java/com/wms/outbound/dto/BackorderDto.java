package com.wms.outbound.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackorderDto {
    private UUID backorderId;
    private Integer shortageQty;
    private String status;
}
