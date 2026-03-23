package com.wms.adjustment.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountStartRequest {

    private UUID locationId;
    private String startedBy;
}
