package com.wms.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptCreateRequest {

    @NotNull(message = "PO ID는 필수입니다")
    private UUID poId;

    @NotBlank(message = "수령자는 필수입니다")
    private String receivedBy;

    @NotEmpty(message = "입고 품목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "상품 ID는 필수입니다")
        private UUID productId;

        @NotNull(message = "로케이션 ID는 필수입니다")
        private UUID locationId;

        @NotNull(message = "수량은 필수입니다")
        private Integer quantity;

        private String lotNumber;

        private LocalDate expiryDate;

        private LocalDate manufactureDate;
    }
}
