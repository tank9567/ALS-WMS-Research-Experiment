package com.wms.outbound.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickResponse {
    private UUID shipmentId;
    private String shipmentNumber;
    private String status;
    private List<PickResultDto> pickResults;
}
