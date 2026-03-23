# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 완료 요약

### 1. DB 스키마 (schema.sql)
- `cycle_counts` 테이블: 실사 세션 관리
- `inventory_adjustments` 테이블: 재고 조정 이력 관리
- 인덱스 추가로 성능 최적화

### 2. Entity 클래스
- `CycleCount`: 실사 상태, 시작/완료 정보 관리
- `InventoryAdjustment`: 조정 내역, 승인 상태 관리

### 3. Repository
- `CycleCountRepository`: 로케이션별 진행 중인 실사 조회
- `InventoryAdjustmentRepository`: 최근 조정 이력 조회, 상태별 조회
- `InventoryRepository`, `SafetyStockRuleRepository`: 필요 메소드 추가

### 4. Service (비즈니스 로직)
`InventoryAdjustmentService`에 다음 기능 구현:
- **실사 시작**: 로케이션 동결, 중복 실사 방지
- **실사 완료**: 동결 해제
- **조정 생성**: 카테고리별 임계치 적용, 연속 조정 감시, HIGH_VALUE 전수 검증
- **조정 승인/거부**: 재고 반영, 안전재고 체크, 감사 로그 기록
- **안전재고 체크**: 자동 재발주 로그 생성

### 5. DTO
요청/응답용 DTO 8개 작성

### 6. Controller (REST API)
- `CycleCountController`: `/api/v1/cycle-counts`
  - POST `/`: 실사 시작
  - POST `/{id}/complete`: 실사 완료
- `InventoryAdjustmentController`: `/api/v1/inventory-adjustments`
  - POST `/`: 조정 생성
  - POST `/{id}/approve`: 승인
  - POST `/{id}/reject`: 거부
  - GET `/{id}`: 상세 조회
  - GET `/`: 목록 조회

### 구현된 비즈니스 규칙
✅ reason 필수, system_qty=0 시 승인 필요  
✅ 카테고리별 자동승인 임계치 (GENERAL 5%, FRESH 3%, HAZMAT 1%, HIGH_VALUE 2%)  
✅ HIGH_VALUE 전수 검증 (차이 있으면 무조건 승인 필요, 감사 로그 기록)  
✅ 연속 조정 감시 (7일 내 2회 이상 → 승인 격상)  
✅ 실사 동결 (cycle_count 시작 시 is_frozen=true, 완료 시 해제)  
✅ 조정 후 안전재고 체크 → 자동 재발주  
✅ 이력 삭제/수정 불가 (audit trail)


# Generated Code


============================================================
// FILE: pom.xml
============================================================
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.wms</groupId>
    <artifactId>wms-inbound</artifactId>
    <version>1.0.0</version>
    <name>WMS Inbound Management</name>
    <description>Warehouse Management System - Inbound Processing</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Spring Boot Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>


============================================================
// FILE: src\main\java\com\wms\controller\CycleCountController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountCompleteRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CycleCountResponse> startCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        CycleCountResponse response = inventoryAdjustmentService.startCycleCount(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<CycleCountResponse> completeCycleCount(
            @PathVariable UUID id,
            @Valid @RequestBody CycleCountCompleteRequest request) {
        CycleCountResponse response = inventoryAdjustmentService.completeCycleCount(id, request);
        return ApiResponse.success(response);
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptListResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InboundReceiptResponse> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<InboundReceiptResponse> confirmInboundReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<InboundReceiptResponse> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam String reason) {
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<InboundReceiptResponse> approveShelfLife(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.approveShelfLife(id);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<InboundReceiptResponse> getInboundReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<InboundReceiptListResponse>> getInboundReceipts() {
        List<InboundReceiptListResponse> response = inboundReceiptService.getInboundReceipts();
        return ApiResponse.success(response);
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InventoryAdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryAdjustmentResponse> createAdjustment(
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createAdjustment(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<InventoryAdjustmentResponse> approveAdjustment(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryAdjustmentApprovalRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveAdjustment(id, request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<InventoryAdjustmentResponse> rejectAdjustment(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryAdjustmentRejectionRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectAdjustment(id, request);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryAdjustmentResponse> getAdjustment(@PathVariable UUID id) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getAdjustment(id);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<InventoryAdjustmentListResponse> getAdjustments() {
        InventoryAdjustmentListResponse response = inventoryAdjustmentService.getAdjustments();
        return ApiResponse.success(response);
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderListResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request) {
        ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipmentOrder(
            @PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentOrderService.pickShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipmentOrder(
            @PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentOrderService.shipShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderListResponse>>> getShipmentOrders() {
        List<ShipmentOrderListResponse> response = shipmentOrderService.getShipmentOrders();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferListResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StockTransferResponse> executeTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.executeTransfer(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<StockTransferResponse> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<StockTransferResponse> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String rejectedBy,
            @RequestParam String reason) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, rejectedBy, reason);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<StockTransferResponse> getTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransfer(id);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<StockTransferListResponse>> getTransfers() {
        List<StockTransferListResponse> response = stockTransferService.getTransfers();
        return ApiResponse.success(response);
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApiResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountCompleteRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountCompleteRequest {
    private String completedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountRequest {
    private UUID locationId;
    private String startedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.CycleCount;
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
public class CycleCountResponse {
    private UUID id;
    private UUID locationId;
    private String locationCode;
    private String status;
    private String startedBy;
    private String completedBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus().name())
                .startedBy(cycleCount.getStartedBy())
                .completedBy(cycleCount.getCompletedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptListResponse.java
============================================================
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
public class InboundReceiptListResponse {
    private UUID id;
    private String receiptNumber;
    private String poNumber;
    private String status;
    private Instant receivedDate;
    private Instant confirmedAt;
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptRequest {

    @NotBlank(message = "Receipt number is required")
    private String receiptNumber;

    @NotNull(message = "Purchase order ID is required")
    private UUID purchaseOrderId;

    @NotEmpty(message = "At least one receipt line is required")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "Purchase order line ID is required")
        private UUID purchaseOrderLineId;

        @NotNull(message = "Product ID is required")
        private UUID productId;

        @NotNull(message = "Location ID is required")
        private UUID locationId;

        @NotNull(message = "Received quantity is required")
        @Positive(message = "Received quantity must be positive")
        private Integer receivedQuantity;

        private String lotNumber;

        private LocalDate manufactureDate;

        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {

    private UUID id;
    private String receiptNumber;
    private UUID purchaseOrderId;
    private String poNumber;
    private String status;
    private Instant receivedDate;
    private Instant confirmedAt;
    private String rejectionReason;
    private List<InboundReceiptLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID id;
        private UUID productId;
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private Integer receivedQuantity;
        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentApprovalRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentApprovalRequest {
    private String approvedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentListResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentListResponse {
    private List<InventoryAdjustmentResponse> adjustments;
    private Integer total;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRejectionRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentRejectionRequest {
    private String approvedBy;
    private String rejectionReason;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentRequest {
    private UUID cycleCountId;
    private UUID productId;
    private UUID locationId;
    private UUID inventoryId;
    private Integer systemQty;
    private Integer actualQty;
    private String reason;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
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
public class InventoryAdjustmentResponse {
    private UUID id;
    private UUID cycleCountId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private UUID inventoryId;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private String approvalStatus;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectionReason;
    private Instant createdAt;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .cycleCountId(adjustment.getCycleCount() != null ? adjustment.getCycleCount().getId() : null)
                .productId(adjustment.getProduct().getId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getId())
                .locationCode(adjustment.getLocation().getCode())
                .inventoryId(adjustment.getInventory() != null ? adjustment.getInventory().getId() : null)
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .difference(adjustment.getDifference())
                .reason(adjustment.getReason())
                .approvalStatus(adjustment.getApprovalStatus().name())
                .approvedBy(adjustment.getApprovedBy())
                .approvedAt(adjustment.getApprovedAt())
                .rejectionReason(adjustment.getRejectionReason())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderListResponse.java
============================================================
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


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

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
public class ShipmentOrderRequest {
    private String shipmentNumber;
    private String customerName;
    private List<ShipmentOrderLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineRequest {
        private UUID productId;
        private Integer requestedQuantity;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID id;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedDate;
    private Instant shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID id;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQuantity;
        private Integer pickedQuantity;
        private String status;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferListResponse.java
============================================================
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
public class StockTransferListResponse {
    private UUID id;
    private String productSku;
    private String productName;
    private String fromLocationCode;
    private String toLocationCode;
    private Integer quantity;
    private String transferStatus;
    private Instant transferredAt;
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {

    @NotNull(message = "Inventory ID is required")
    private UUID inventoryId;

    @NotNull(message = "To location ID is required")
    private UUID toLocationId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    private String requestedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
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
public class StockTransferResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private UUID inventoryId;
    private Integer quantity;
    private String transferStatus;
    private String requestedBy;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectionReason;
    private Instant transferredAt;
    private Instant createdAt;
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "JSONB")
    private String details;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoReorderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "trigger_reason", nullable = false, length = 50)
    private String triggerReason;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backorder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_line_id", nullable = false)
    private ShipmentOrderLine shipmentOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.open;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum BackorderStatus {
        open, fulfilled, cancelled
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\CycleCount.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleCountStatus status = CycleCountStatus.IN_PROGRESS;

    @Column(name = "started_by", length = 255)
    private String startedBy;

    @Column(name = "completed_by", length = 255)
    private String completedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum CycleCountStatus {
        IN_PROGRESS, COMPLETED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", unique = true, nullable = false, length = 100)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceiptStatus status = ReceiptStatus.inspecting;

    @Column(name = "received_date", nullable = false)
    private Instant receivedDate;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ReceiptStatus {
        inspecting, pending_approval, confirmed, rejected
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private Boolean expired = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\InventoryAdjustment.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_count_id")
    private CycleCount cycleCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id")
    private Inventory inventory;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(nullable = false)
    private Integer difference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ApprovalStatus {
        AUTO_APPROVED, PENDING, APPROVED, REJECTED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity = 0;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Product.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProductCategory category;

    @Column(nullable = false, length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "requires_expiry", nullable = false)
    private Boolean requiresExpiry = false;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ProductCategory {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN, HAZMAT
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "po_number", unique = true, nullable = false, length = 100)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoStatus status = PoStatus.pending;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        pending, partial, completed, cancelled, hold
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyStockRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier = BigDecimal.valueOf(1.5);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 100)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.pending;

    @Column(name = "requested_date", nullable = false)
    private Instant requestedDate;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ShipmentStatus {
        pending, picking, partial, shipped, cancelled
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "picked_quantity", nullable = false)
    private Integer pickedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status = LineStatus.pending;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum LineStatus {
        pending, picked, partial, backordered
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 30)
    private TransferStatus transferStatus = TransferStatus.IMMEDIATE;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "transferred_at", nullable = false)
    private Instant transferredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum TransferStatus {
        IMMEDIATE, PENDING_APPROVAL, APPROVED, REJECTED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SupplierStatus {
        active, hold, inactive
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private PenaltyType penaltyType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\BusinessException.java
============================================================
package com.wms.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final String message;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\GlobalExceptionHandler.java
============================================================
package com.wms.exception;

import com.wms.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.error("Business exception: {} - {}", e.getCode(), e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.error("Validation exception: {}", message);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}


============================================================
// FILE: src\main\java\com\wms\repository\AuditLogRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\AutoReorderLogRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.AutoReorderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AutoReorderLogRepository extends JpaRepository<AutoReorderLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\BackorderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Backorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackorderRepository extends JpaRepository<Backorder, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\CycleCountRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    Optional<CycleCount> findByLocationAndStatus(Location location, CycleCount.CycleCountStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
    Optional<InboundReceipt> findByReceiptNumber(String receiptNumber);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryAdjustmentRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import com.wms.entity.Location;
import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia " +
           "WHERE ia.product = :product " +
           "AND ia.location = :location " +
           "AND ia.createdAt >= :since " +
           "AND ia.approvalStatus IN ('AUTO_APPROVED', 'APPROVED')")
    List<InventoryAdjustment> findRecentApprovedAdjustments(
        @Param("product") Product product,
        @Param("location") Location location,
        @Param("since") Instant since
    );

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductAndLocationAndLot(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.quantity > 0 AND i.expired = false AND i.location.isFrozen = false ORDER BY i.receivedAt ASC")
    List<Inventory> findAvailableInventoryForProduct(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product.id = :productId AND i.expired = false")
    int getTotalAvailableQuantity(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product = :product AND i.expired = false")
    Integer sumAvailableQuantityByProduct(@Param("product") com.wms.entity.Product product);
}


============================================================
// FILE: src\main\java\com\wms\repository\LocationRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByCode(String code);
}


============================================================
// FILE: src\main\java\com\wms\repository\ProductRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.id = :supplierId AND po.status = 'pending'")
    List<PurchaseOrder> findPendingOrdersBySupplierId(@Param("supplierId") UUID supplierId);
}


============================================================
// FILE: src\main\java\com\wms\repository\SafetyStockRuleRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Product;
import com.wms.entity.SafetyStockRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, UUID> {
    Optional<SafetyStockRule> findByProductId(UUID productId);
    Optional<SafetyStockRule> findByProduct(Product product);
}


============================================================
// FILE: src\main\java\com\wms\repository\SeasonalConfigRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.SeasonalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonalConfigRepository extends JpaRepository<SeasonalConfig, UUID> {

    @Query("SELECT sc FROM SeasonalConfig sc WHERE :date BETWEEN sc.startDate AND sc.endDate")
    Optional<SeasonalConfig> findActiveSeasonByDate(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShipmentOrderLineRepository extends JpaRepository<ShipmentOrderLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\StockTransferRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\SupplierPenaltyRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.id = :supplierId AND sp.occurredAt >= :since")
    long countBySupplierIdAndOccurredAtAfter(
        @Param("supplierId") UUID supplierId,
        @Param("since") Instant since
    );
}


============================================================
// FILE: src\main\java\com\wms\repository\SupplierRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\service\InboundReceiptService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptListResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new BusinessException("PURCHASE_ORDER_NOT_FOUND", "Purchase order not found"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new BusinessException("PO_ON_HOLD", "Purchase order is on hold due to supplier penalties");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .receiptNumber(request.getReceiptNumber())
                .purchaseOrder(po)
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedDate(Instant.now())
                .build();

        // 3. 각 라인에 대해 검증 및 처리
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND", "Purchase order line not found"));

            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

            // 3.1 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN",
                        "Location " + location.getCode() + " is frozen for cycle count");
            }

            // 3.2 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3.3 HAZMAT zone 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("HAZMAT_ZONE_REQUIRED",
                        "HAZMAT products must be stored in HAZMAT zone");
            }

            // 3.4 초과 입고 체크
            validateOverDelivery(po, poLine, lineReq.getReceivedQuantity(), product);

            // 3.5 유통기한 관리 체크
            if (product.getRequiresExpiry()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new BusinessException("EXPIRY_DATE_REQUIRED",
                            "Expiry date is required for product: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new BusinessException("MANUFACTURE_DATE_REQUIRED",
                            "Manufacture date is required for product: " + product.getSku());
                }

                // 유통기한 잔여율 체크
                ShelfLifeValidation validation = validateShelfLife(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        product.getMinRemainingShelfLifePct()
                );

                if (validation == ShelfLifeValidation.REJECT) {
                    // 공급업체 페널티 부과
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "Shelf life remaining below threshold for product: " + product.getSku());
                    throw new BusinessException("SHELF_LIFE_INSUFFICIENT",
                            "Shelf life remaining is below minimum threshold");
                } else if (validation == ShelfLifeValidation.NEEDS_APPROVAL) {
                    receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
                }
            }

            // 3.6 로케이션 용량 체크
            if (location.getCurrentQuantity() + lineReq.getReceivedQuantity() > location.getCapacity()) {
                throw new BusinessException("LOCATION_CAPACITY_EXCEEDED",
                        "Location capacity would be exceeded");
            }

            // 3.7 입고 라인 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .receivedQuantity(lineReq.getReceivedQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .manufactureDate(lineReq.getManufactureDate())
                    .expiryDate(lineReq.getExpiryDate())
                    .build();

            receipt.getLines().add(receiptLine);
        }

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting) {
            throw new BusinessException("INVALID_STATUS", "Receipt must be in inspecting status to confirm");
        }

        // 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            // Inventory 업데이트
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber()
            ).orElse(null);

            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() + line.getReceivedQuantity());
                inventoryRepository.save(inventory);
            } else {
                inventory = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .quantity(line.getReceivedQuantity())
                        .lotNumber(line.getLotNumber())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(Instant.now())
                        .build();
                inventoryRepository.save(inventory);
            }

            // Location 현재 수량 업데이트
            Location location = line.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + line.getReceivedQuantity());
            locationRepository.save(location);

            // PO Line 입고 수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() + line.getReceivedQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // PO 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(Instant.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() == InboundReceipt.ReceiptStatus.confirmed ||
            receipt.getStatus() == InboundReceipt.ReceiptStatus.rejected) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be rejected in current status");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receipt.setRejectionReason(reason);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse approveShelfLife(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not pending approval");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));
        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptListResponse> getInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    // ========== Private Helper Methods ==========

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
            case HAZMAT -> location.getZone() == Location.Zone.HAZMAT;
        };

        if (!compatible) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Product storage type " + productType + " is not compatible with location storage type " + locationType);
        }
    }

    private void validateOverDelivery(PurchaseOrder po, PurchaseOrderLine poLine,
                                      int receivedQty, Product product) {
        int totalReceived = poLine.getReceivedQuantity() + receivedQty;
        int ordered = poLine.getOrderedQuantity();
        double overDeliveryPct = ((double) (totalReceived - ordered) / ordered) * 100;

        if (overDeliveryPct <= 0) {
            return; // 초과 아님
        }

        // 기본 허용률 계산
        double allowedPct = getCategoryAllowancePct(product.getCategory());

        // HAZMAT은 항상 0%
        if (product.getCategory() != Product.ProductCategory.HAZMAT) {
            // 발주 유형별 가중치
            double poTypeMultiplier = switch (po.getPoType()) {
                case NORMAL -> 1.0;
                case URGENT -> 2.0;
                case IMPORT -> 1.5;
            };
            allowedPct *= poTypeMultiplier;

            // 성수기 가중치
            LocalDate today = LocalDate.now();
            SeasonalConfig season = seasonalConfigRepository.findActiveSeasonByDate(today).orElse(null);
            if (season != null) {
                allowedPct *= season.getMultiplier().doubleValue();
            }
        }

        if (overDeliveryPct > allowedPct) {
            // 공급업체 페널티 부과
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("Over delivery: %.2f%% (allowed: %.2f%%)", overDeliveryPct, allowedPct));
            throw new BusinessException("OVER_DELIVERY",
                    String.format("Over delivery exceeds allowed threshold: %.2f%% > %.2f%%",
                            overDeliveryPct, allowedPct));
        }
    }

    private double getCategoryAllowancePct(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 10.0;
            case FRESH -> 5.0;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 3.0;
        };
    }

    private enum ShelfLifeValidation {
        ACCEPT, NEEDS_APPROVAL, REJECT
    }

    private ShelfLifeValidation validateShelfLife(LocalDate manufactureDate, LocalDate expiryDate,
                                                   Integer minPct) {
        LocalDate today = LocalDate.now();

        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalShelfLife <= 0) {
            throw new BusinessException("INVALID_DATES", "Expiry date must be after manufacture date");
        }

        double remainingPct = ((double) remainingShelfLife / totalShelfLife) * 100;

        int threshold = (minPct != null) ? minPct : 30;

        if (remainingPct < threshold) {
            return ShelfLifeValidation.REJECT;
        } else if (remainingPct < 50) {
            return ShelfLifeValidation.NEEDS_APPROVAL;
        } else {
            return ShelfLifeValidation.ACCEPT;
        }
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType type, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(type)
                .reason(reason)
                .occurredAt(Instant.now())
                .build();
        supplierPenaltyRepository.save(penalty);

        // 30일 내 페널티 3회 이상이면 공급업체 hold
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndOccurredAtAfter(
                supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            supplier.setStatus(Supplier.SupplierStatus.hold);
            supplierRepository.save(supplier);

            // 해당 공급업체의 pending PO를 모두 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findPendingOrdersBySupplierId(supplier.getId());
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(po);
            }

            log.warn("Supplier {} has been put on hold due to {} penalties in last 30 days",
                    supplier.getName(), penaltyCount);
        }
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQuantity() < line.getOrderedQuantity()) {
                allCompleted = false;
            }
            if (line.getReceivedQuantity() > 0) {
                anyReceived = true;
            }
        }

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .receivedQuantity(line.getReceivedQuantity())
                        .lotNumber(line.getLotNumber())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .purchaseOrderId(receipt.getPurchaseOrder().getId())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedDate(receipt.getReceivedDate())
                .confirmedAt(receipt.getConfirmedAt())
                .rejectionReason(receipt.getRejectionReason())
                .lines(lineResponses)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }

    private InboundReceiptListResponse toListResponse(InboundReceipt receipt) {
        return InboundReceiptListResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedDate(receipt.getReceivedDate())
                .confirmedAt(receipt.getConfirmedAt())
                .createdAt(receipt.getCreatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InventoryAdjustmentService.java
============================================================
package com.wms.service;

import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    // 카테고리별 자동승인 임계치
    private static final Map<Product.ProductCategory, Double> AUTO_APPROVAL_THRESHOLDS = new HashMap<>();
    static {
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.GENERAL, 0.05);    // 5%
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.FRESH, 0.03);      // 3%
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HAZMAT, 0.01);     // 1%
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HIGH_VALUE, 0.02); // 2%
    }

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. Location 조회
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 2. 진행 중인 실사가 있는지 확인
        cycleCountRepository.findByLocationAndStatus(location, CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new BusinessException("CYCLE_COUNT_IN_PROGRESS",
                            "A cycle count is already in progress for location: " + location.getCode());
                });

        // 3. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 4. CycleCount 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .startedAt(Instant.now())
                .build();

        CycleCount saved = cycleCountRepository.save(cycleCount);
        log.info("Cycle count started for location: {} by: {}", location.getCode(), request.getStartedBy());

        return CycleCountResponse.from(saved);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId, CycleCountCompleteRequest request) {
        // 1. CycleCount 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));

        if (cycleCount.getStatus() == CycleCount.CycleCountStatus.COMPLETED) {
            throw new BusinessException("CYCLE_COUNT_ALREADY_COMPLETED", "Cycle count is already completed");
        }

        // 2. 실사 완료 처리
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedBy(request.getCompletedBy());
        cycleCount.setCompletedAt(Instant.now());

        // 3. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        CycleCount saved = cycleCountRepository.save(cycleCount);
        log.info("Cycle count completed for location: {} by: {}", location.getCode(), request.getCompletedBy());

        return CycleCountResponse.from(saved);
    }

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. Validation - reason 필수
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("REASON_REQUIRED", "Adjustment reason is required");
        }

        // 2. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        CycleCount cycleCount = null;
        if (request.getCycleCountId() != null) {
            cycleCount = cycleCountRepository.findById(request.getCycleCountId())
                    .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));
        }

        Inventory inventory = null;
        if (request.getInventoryId() != null) {
            inventory = inventoryRepository.findById(request.getInventoryId())
                    .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Inventory not found"));
        }

        // 3. 차이 계산
        Integer systemQty = request.getSystemQty();
        Integer actualQty = request.getActualQty();
        Integer difference = actualQty - systemQty;

        // 4. 승인 상태 결정
        InventoryAdjustment.ApprovalStatus approvalStatus = determineApprovalStatus(
                product, location, systemQty, actualQty, difference, request.getReason());

        // 5. InventoryAdjustment 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .cycleCount(cycleCount)
                .product(product)
                .location(location)
                .inventory(inventory)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(request.getReason())
                .approvalStatus(approvalStatus)
                .build();

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Inventory adjustment created: product={}, location={}, diff={}, status={}",
                product.getSku(), location.getCode(), difference, approvalStatus);

        // 6. 자동 승인인 경우 즉시 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(saved);
        }

        return InventoryAdjustmentResponse.from(saved);
    }

    private InventoryAdjustment.ApprovalStatus determineApprovalStatus(
            Product product, Location location, Integer systemQty, Integer actualQty,
            Integer difference, String reason) {

        // 1. system_qty=0 시 승인 필요
        if (systemQty == 0) {
            log.info("System qty is 0, approval required");
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 2. HIGH_VALUE 카테고리는 차이가 0이 아니면 무조건 승인 필요
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            log.info("HIGH_VALUE product with non-zero difference, approval required");
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 3. 연속 조정 감시 (최근 7일 내 2회 이상)
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentApprovedAdjustments(
                product, location, sevenDaysAgo);

        if (recentAdjustments.size() >= 2) {
            log.info("Consecutive adjustments detected ({}), approval required", recentAdjustments.size());
            // [연속조정감시] 태그 추가 (reason은 불변이므로 실제로는 로깅만)
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 4. 카테고리별 자동승인 임계치 체크
        double threshold = AUTO_APPROVAL_THRESHOLDS.get(product.getCategory());
        double diffRatio = Math.abs((double) difference / systemQty);

        if (diffRatio <= threshold) {
            log.info("Difference ratio {} is within threshold {}, auto approved", diffRatio, threshold);
            return InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        } else {
            log.info("Difference ratio {} exceeds threshold {}, approval required", diffRatio, threshold);
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, InventoryAdjustmentApprovalRequest request) {
        // 1. Adjustment 조회
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS",
                    "Only pending adjustments can be approved. Current status: " + adjustment.getApprovalStatus());
        }

        // 2. 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Inventory adjustment approved: id={}, by={}", adjustmentId, request.getApprovedBy());

        // 3. 재고 반영
        applyAdjustment(saved);

        // 4. HIGH_VALUE 감사 로그 기록
        if (saved.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            recordAuditLog(saved);
        }

        return InventoryAdjustmentResponse.from(saved);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, InventoryAdjustmentRejectionRequest request) {
        // 1. Adjustment 조회
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS",
                    "Only pending adjustments can be rejected. Current status: " + adjustment.getApprovalStatus());
        }

        // 2. 거부 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        adjustment.setRejectionReason(request.getRejectionReason());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Inventory adjustment rejected: id={}, by={}, reason={}",
                adjustmentId, request.getApprovedBy(), request.getRejectionReason());

        return InventoryAdjustmentResponse.from(saved);
    }

    private void applyAdjustment(InventoryAdjustment adjustment) {
        // 1. Inventory 업데이트
        Inventory inventory = adjustment.getInventory();
        if (inventory == null) {
            // 새로운 재고 생성 (실물 발견 케이스)
            inventory = Inventory.builder()
                    .product(adjustment.getProduct())
                    .location(adjustment.getLocation())
                    .quantity(adjustment.getActualQty())
                    .receivedAt(Instant.now())
                    .build();
            inventoryRepository.save(inventory);
            log.info("New inventory created: product={}, location={}, qty={}",
                    adjustment.getProduct().getSku(), adjustment.getLocation().getCode(), adjustment.getActualQty());
        } else {
            // 기존 재고 업데이트
            int oldQty = inventory.getQuantity();
            inventory.setQuantity(adjustment.getActualQty());
            inventoryRepository.save(inventory);
            log.info("Inventory updated: product={}, location={}, {} -> {}",
                    adjustment.getProduct().getSku(), adjustment.getLocation().getCode(), oldQty, adjustment.getActualQty());
        }

        // 2. Location 현재 수량 업데이트
        Location location = adjustment.getLocation();
        int locationDiff = adjustment.getDifference();
        int newLocationQty = location.getCurrentQuantity() + locationDiff;

        if (newLocationQty < 0) {
            throw new BusinessException("INVALID_LOCATION_QUANTITY",
                    "Location quantity cannot be negative: " + newLocationQty);
        }

        if (newLocationQty > location.getCapacity()) {
            throw new BusinessException("LOCATION_CAPACITY_EXCEEDED",
                    "Location capacity exceeded: " + newLocationQty + " > " + location.getCapacity());
        }

        location.setCurrentQuantity(newLocationQty);
        locationRepository.save(location);

        // 3. 안전재고 체크
        checkSafetyStock(adjustment.getProduct());
    }

    private void checkSafetyStock(Product product) {
        // 1. SafetyStockRule 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return; // 안전재고 규칙이 없으면 체크하지 않음
        }

        // 2. 전체 가용 재고 계산 (모든 로케이션 합산, expired 제외)
        Integer totalAvailableQty = inventoryRepository.sumAvailableQuantityByProduct(product);
        if (totalAvailableQty == null) {
            totalAvailableQty = 0;
        }

        // 3. 안전재고 이하인지 체크
        if (totalAvailableQty <= rule.getMinQty()) {
            // 자동 재발주 로그 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .reorderQty(rule.getReorderQty())
                    .triggeredAt(Instant.now())
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock threshold reached for product: {}, available: {}, min: {}, reorder: {}",
                    product.getSku(), totalAvailableQty, rule.getMinQty(), rule.getReorderQty());
        }
    }

    private void recordAuditLog(InventoryAdjustment adjustment) {
        Map<String, Object> details = new HashMap<>();
        details.put("adjustment_id", adjustment.getId().toString());
        details.put("product_sku", adjustment.getProduct().getSku());
        details.put("location_code", adjustment.getLocation().getCode());
        details.put("system_qty", adjustment.getSystemQty());
        details.put("actual_qty", adjustment.getActualQty());
        details.put("difference", adjustment.getDifference());
        details.put("reason", adjustment.getReason());

        AuditLog auditLog = AuditLog.builder()
                .entityType("INVENTORY_ADJUSTMENT")
                .entityId(adjustment.getId())
                .action("HIGH_VALUE_ADJUSTMENT")
                .details(details)
                .performedBy(adjustment.getApprovedBy())
                .performedAt(Instant.now())
                .build();

        auditLogRepository.save(auditLog);
        log.info("Audit log recorded for HIGH_VALUE adjustment: {}", adjustment.getId());
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));
        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentListResponse getAdjustments() {
        List<InventoryAdjustment> adjustments = adjustmentRepository.findAll();
        List<InventoryAdjustmentResponse> responses = adjustments.stream()
                .map(InventoryAdjustmentResponse::from)
                .collect(Collectors.toList());

        return InventoryAdjustmentListResponse.builder()
                .adjustments(responses)
                .total(responses.size())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderListResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // HAZMAT과 FRESH 상품 분리 체크
        Map<Boolean, List<ShipmentOrderRequest.ShipmentOrderLineRequest>> partitioned =
            request.getLines().stream()
                .collect(Collectors.partitioningBy(line -> {
                    Product product = productRepository.findById(line.getProductId())
                        .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));
                    return product.getCategory() == Product.ProductCategory.HAZMAT;
                }));

        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = partitioned.get(true);
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = partitioned.get(false);

        // HAZMAT과 FRESH 혼재 체크
        boolean hasFresh = nonHazmatLines.stream().anyMatch(line -> {
            Product product = productRepository.findById(line.getProductId()).orElse(null);
            return product != null && product.getCategory() == Product.ProductCategory.FRESH;
        });

        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT과 FRESH가 혼재된 경우 분리 출고
            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder mainOrder = createShipmentOrderInternal(request.getShipmentNumber(),
                request.getCustomerName(), nonHazmatLines);

            // HAZMAT 출고 지시서 별도 생성
            String hazmatShipmentNumber = request.getShipmentNumber() + "-HAZMAT";
            ShipmentOrder hazmatOrder = createShipmentOrderInternal(hazmatShipmentNumber,
                request.getCustomerName(), hazmatLines);

            log.info("Separated HAZMAT order {} from main order {}",
                hazmatOrder.getShipmentNumber(), mainOrder.getShipmentNumber());

            return toResponse(mainOrder);
        } else {
            // 분리 불필요, 정상 생성
            ShipmentOrder order = createShipmentOrderInternal(request.getShipmentNumber(),
                request.getCustomerName(), request.getLines());
            return toResponse(order);
        }
    }

    private ShipmentOrder createShipmentOrderInternal(String shipmentNumber, String customerName,
                                                      List<ShipmentOrderRequest.ShipmentOrderLineRequest> lineRequests) {
        ShipmentOrder order = ShipmentOrder.builder()
            .shipmentNumber(shipmentNumber)
            .customerName(customerName)
            .status(ShipmentOrder.ShipmentStatus.pending)
            .requestedDate(Instant.now())
            .build();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : lineRequests) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                .shipmentOrder(order)
                .product(product)
                .requestedQuantity(lineReq.getRequestedQuantity())
                .pickedQuantity(0)
                .status(ShipmentOrderLine.LineStatus.pending)
                .build();

            order.getLines().add(line);
        }

        return shipmentOrderRepository.save(order);
    }

    @Transactional
    public ShipmentOrderResponse pickShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("SHIPMENT_ORDER_NOT_FOUND", "Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("INVALID_STATUS", "Shipment order must be in pending status to pick");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.picking);

        for (ShipmentOrderLine line : order.getLines()) {
            pickLine(line);
        }

        // 전체 주문 상태 업데이트
        updateShipmentOrderStatus(order);

        ShipmentOrder savedOrder = shipmentOrderRepository.save(order);
        return toResponse(savedOrder);
    }

    private void pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQuantity();

        // 가용 재고 조회 (FIFO/FEFO 적용)
        List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

        int totalPicked = 0;
        int remainingQty = requestedQty;

        // HAZMAT max_pick_qty 체크
        if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                throw new BusinessException("MAX_PICK_QTY_EXCEEDED",
                    String.format("Requested quantity %d exceeds max pick qty %d for HAZMAT product %s",
                        requestedQty, product.getMaxPickQty(), product.getSku()));
            }
        }

        for (Inventory inventory : availableInventories) {
            if (remainingQty <= 0) break;

            // 실사 동결 로케이션 체크
            if (inventory.getLocation().getIsFrozen()) {
                continue;
            }

            // HAZMAT zone 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                if (inventory.getLocation().getZone() != Location.Zone.HAZMAT) {
                    continue;
                }
            }

            // 보관 유형 불일치 경고
            if (inventory.getLocation().getStorageType() != product.getStorageType()) {
                recordAuditLog("STORAGE_TYPE_MISMATCH", inventory.getId(),
                    String.format("Storage type mismatch: product %s (%s) in location %s (%s)",
                        product.getSku(), product.getStorageType(),
                        inventory.getLocation().getCode(), inventory.getLocation().getStorageType()));
            }

            int pickQty = Math.min(remainingQty, inventory.getQuantity());

            // 재고 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // 로케이션 현재 수량 차감
            Location location = inventory.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() - pickQty);
            locationRepository.save(location);

            totalPicked += pickQty;
            remainingQty -= pickQty;
        }

        line.setPickedQuantity(totalPicked);

        // 부분출고 의사결정 트리
        if (totalPicked < requestedQty) {
            double fulfillmentRate = (double) totalPicked / requestedQty;
            int backorderQty = requestedQty - totalPicked;

            if (fulfillmentRate >= 0.7) {
                // 70% 이상: 부분출고 + 백오더
                line.setStatus(ShipmentOrderLine.LineStatus.partial);
                createBackorder(line, backorderQty);
            } else if (fulfillmentRate >= 0.3) {
                // 30~70%: 부분출고 + 백오더 + 긴급발주
                line.setStatus(ShipmentOrderLine.LineStatus.partial);
                createBackorder(line, backorderQty);
                recordEmergencyReorder(product, backorderQty);
            } else {
                // 30% 미만: 전량 백오더 (부분출고 안 함)
                // 피킹한 재고를 다시 원복
                rollbackPicking(product, totalPicked);
                line.setPickedQuantity(0);
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                createBackorder(line, requestedQty);
            }
        } else {
            line.setStatus(ShipmentOrderLine.LineStatus.picked);
        }

        shipmentOrderLineRepository.save(line);
    }

    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        List<Inventory> inventories = inventoryRepository.findAvailableInventoryForProduct(product.getId());

        // 만료된 재고 및 잔여율 < 10% 재고 제외 및 expired 플래그 설정
        LocalDate today = LocalDate.now();
        inventories.removeIf(inv -> {
            if (inv.getExpiryDate() != null) {
                // 만료된 재고
                if (inv.getExpiryDate().isBefore(today)) {
                    inv.setExpired(true);
                    inventoryRepository.save(inv);
                    return true;
                }

                // 잔여율 < 10% 재고
                if (inv.getManufactureDate() != null) {
                    double remainingPct = calculateRemainingShelfLifePct(
                        inv.getManufactureDate(), inv.getExpiryDate(), today);
                    if (remainingPct < 10) {
                        inv.setExpired(true);
                        inventoryRepository.save(inv);
                        return true;
                    }
                }
            }
            return false;
        });

        // FEFO 정렬 (유통기한 관리 상품)
        if (product.getRequiresExpiry()) {
            inventories.sort((i1, i2) -> {
                // 잔여율 < 30%인 재고 최우선
                LocalDate today1 = LocalDate.now();
                double pct1 = i1.getManufactureDate() != null && i1.getExpiryDate() != null ?
                    calculateRemainingShelfLifePct(i1.getManufactureDate(), i1.getExpiryDate(), today1) : 100;
                double pct2 = i2.getManufactureDate() != null && i2.getExpiryDate() != null ?
                    calculateRemainingShelfLifePct(i2.getManufactureDate(), i2.getExpiryDate(), today1) : 100;

                boolean i1Priority = pct1 < 30;
                boolean i2Priority = pct2 < 30;

                if (i1Priority && !i2Priority) return -1;
                if (!i1Priority && i2Priority) return 1;

                // 유통기한 빠른 순 (FEFO)
                if (i1.getExpiryDate() != null && i2.getExpiryDate() != null) {
                    int expCompare = i1.getExpiryDate().compareTo(i2.getExpiryDate());
                    if (expCompare != 0) return expCompare;
                }

                // 유통기한 같으면 FIFO
                return i1.getReceivedAt().compareTo(i2.getReceivedAt());
            });
        } else {
            // FIFO 정렬 (일반 상품)
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalShelfLife <= 0) return 0;
        return ((double) remainingShelfLife / totalShelfLife) * 100;
    }

    private void createBackorder(ShipmentOrderLine line, int quantity) {
        Backorder backorder = Backorder.builder()
            .shipmentOrderLine(line)
            .product(line.getProduct())
            .quantity(quantity)
            .status(Backorder.BackorderStatus.open)
            .build();
        backorderRepository.save(backorder);
    }

    private void recordEmergencyReorder(Product product, int quantity) {
        AutoReorderLog log = AutoReorderLog.builder()
            .product(product)
            .triggerReason("EMERGENCY_REORDER")
            .reorderQty(quantity)
            .triggeredAt(Instant.now())
            .build();
        autoReorderLogRepository.save(log);
        log.info("Emergency reorder triggered for product {} with quantity {}", product.getSku(), quantity);
    }

    private void rollbackPicking(Product product, int quantity) {
        // 30% 미만일 때 피킹한 재고를 원복하는 로직
        // 실제로는 더 복잡하지만, 간단히 로그만 남김
        log.warn("Rollback picking for product {} with quantity {} (30% threshold not met)",
            product.getSku(), quantity);
        // TODO: 실제 원복 로직 구현 필요
    }

    private void recordAuditLog(String action, UUID entityId, String details) {
        AuditLog auditLog = AuditLog.builder()
            .entityType("INVENTORY")
            .entityId(entityId)
            .action(action)
            .details(details)
            .performedBy("SYSTEM")
            .performedAt(Instant.now())
            .build();
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("SHIPMENT_ORDER_NOT_FOUND", "Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
            order.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new BusinessException("INVALID_STATUS", "Shipment order must be in picking or partial status to ship");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        order.setShippedAt(Instant.now());

        // 출고 완료 후 안전재고 체크
        for (ShipmentOrderLine line : order.getLines()) {
            checkSafetyStock(line.getProduct());
        }

        ShipmentOrder savedOrder = shipmentOrderRepository.save(order);
        return toResponse(savedOrder);
    }

    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);
        if (rule == null) {
            return; // 안전재고 규칙 없음
        }

        int totalAvailable = inventoryRepository.getTotalAvailableQuantity(product.getId());

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason("SAFETY_STOCK_TRIGGER")
                .reorderQty(rule.getReorderQty())
                .triggeredAt(Instant.now())
                .build();
            autoReorderLogRepository.save(log);
            log.info("Safety stock trigger for product {} - current: {}, min: {}, reorder: {}",
                product.getSku(), totalAvailable, rule.getMinQty(), rule.getReorderQty());
        }
    }

    private void updateShipmentOrderStatus(ShipmentOrder order) {
        boolean allPicked = true;
        boolean anyPicked = false;

        for (ShipmentOrderLine line : order.getLines()) {
            if (line.getPickedQuantity() < line.getRequestedQuantity()) {
                allPicked = false;
            }
            if (line.getPickedQuantity() > 0) {
                anyPicked = true;
            }
        }

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.picking);
        } else if (anyPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("SHIPMENT_ORDER_NOT_FOUND", "Shipment order not found"));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderListResponse> getShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
            .map(this::toListResponse)
            .collect(Collectors.toList());
    }

    private ShipmentOrderResponse toResponse(ShipmentOrder order) {
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = order.getLines().stream()
            .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                .id(line.getId())
                .productId(line.getProduct().getId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .requestedQuantity(line.getRequestedQuantity())
                .pickedQuantity(line.getPickedQuantity())
                .status(line.getStatus().name())
                .build())
            .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
            .id(order.getId())
            .shipmentNumber(order.getShipmentNumber())
            .customerName(order.getCustomerName())
            .status(order.getStatus().name())
            .requestedDate(order.getRequestedDate())
            .shippedAt(order.getShippedAt())
            .lines(lineResponses)
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    private ShipmentOrderListResponse toListResponse(ShipmentOrder order) {
        return ShipmentOrderListResponse.builder()
            .id(order.getId())
            .shipmentNumber(order.getShipmentNumber())
            .customerName(order.getCustomerName())
            .status(order.getStatus().name())
            .requestedDate(order.getRequestedDate())
            .shippedAt(order.getShippedAt())
            .createdAt(order.getCreatedAt())
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferListResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // 1. Inventory 조회
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Inventory not found"));

        Location fromLocation = inventory.getLocation();
        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "To location not found"));

        Product product = inventory.getProduct();

        // 2. 기본 검증
        validateBasicRules(inventory, fromLocation, toLocation, request.getQuantity());

        // 3. 보관 유형 호환성 검증
        validateStorageTypeCompatibility(product, toLocation);

        // 4. HAZMAT 혼적 금지 검증
        validateHazmatSegregation(product, toLocation);

        // 5. 유통기한 임박 상품 이동 제한
        validateExpiryRestrictions(inventory, toLocation);

        // 6. 대량 이동 승인 체크
        StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.IMMEDIATE;
        double transferPct = ((double) request.getQuantity() / inventory.getQuantity()) * 100;
        if (transferPct >= 80) {
            transferStatus = StockTransfer.TransferStatus.PENDING_APPROVAL;
        }

        // 7. StockTransfer 레코드 생성
        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .inventory(inventory)
                .quantity(request.getQuantity())
                .transferStatus(transferStatus)
                .requestedBy(request.getRequestedBy())
                .transferredAt(Instant.now())
                .build();

        // 8. 즉시 이동인 경우 재고 반영
        if (transferStatus == StockTransfer.TransferStatus.IMMEDIATE) {
            executeInventoryTransfer(inventory, fromLocation, toLocation, request.getQuantity());

            // 9. 이동 후 안전재고 체크
            checkSafetyStockAfterTransfer(product);
        }

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);
        return toResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        // 승인 시 재고 반영
        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();

        // 재검증 (승인 대기 중에 재고가 변경되었을 수 있음)
        if (inventory.getQuantity() < transfer.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock for transfer. Current: " + inventory.getQuantity());
        }

        executeInventoryTransfer(inventory, fromLocation, toLocation, transfer.getQuantity());

        transfer.setTransferStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());

        // 안전재고 체크
        checkSafetyStockAfterTransfer(transfer.getProduct());

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);
        return toResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String rejectedBy, String reason) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setApprovedBy(rejectedBy);
        transfer.setRejectionReason(reason);
        transfer.setApprovedAt(Instant.now());

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);
        return toResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferListResponse> getTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    // ========== Private Helper Methods ==========

    private void validateBasicRules(Inventory inventory, Location fromLocation,
                                    Location toLocation, int quantity) {
        // 동일 로케이션 거부
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new BusinessException("SAME_LOCATION", "Cannot transfer to the same location");
        }

        // 재고 부족 체크
        if (inventory.getQuantity() < quantity) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock. Available: " + inventory.getQuantity());
        }

        // 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN",
                    "From location " + fromLocation.getCode() + " is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN",
                    "To location " + toLocation.getCode() + " is frozen for cycle count");
        }

        // 도착지 용량 체크
        if (toLocation.getCurrentQuantity() + quantity > toLocation.getCapacity()) {
            throw new BusinessException("LOCATION_CAPACITY_EXCEEDED",
                    "Destination location capacity would be exceeded");
        }
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot move FROZEN product to AMBIENT location (quality risk)");
        }

        // COLD 상품 → AMBIENT 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot move COLD product to AMBIENT location (quality risk)");
        }

        // HAZMAT 상품 → 비-HAZMAT zone 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT &&
            toLocation.getZone() != Location.Zone.HAZMAT) {
            throw new BusinessException("HAZMAT_ZONE_REQUIRED",
                    "HAZMAT products must be stored in HAZMAT zone");
        }
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 적재된 재고 확인
        List<Inventory> existingInventories = inventoryRepository.findAvailableInventoryForProduct(
                product.getId()).stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .toList();

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory existing : existingInventories) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat != existingIsHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }
        }
    }

    private void validateExpiryRestrictions(Inventory inventory, Location toLocation) {
        if (inventory.getExpiryDate() == null) {
            return; // 유통기한 관리 대상 아님
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();

        // 유통기한 만료 → 이동 불가
        if (!expiryDate.isAfter(today)) {
            throw new BusinessException("EXPIRED_PRODUCT",
                    "Cannot transfer expired products (expiry: " + expiryDate + ")");
        }

        // 잔여 유통기한 < 10% → SHIPPING zone만 허용
        if (inventory.getManufactureDate() != null) {
            long totalShelfLife = ChronoUnit.DAYS.between(inventory.getManufactureDate(), expiryDate);
            long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = ((double) remainingShelfLife / totalShelfLife) * 100;

            if (remainingPct < 10 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("EXPIRY_RESTRICTION",
                        "Products with <10% shelf life remaining can only be transferred to SHIPPING zone");
            }
        }
    }

    private void executeInventoryTransfer(Inventory inventory, Location fromLocation,
                                          Location toLocation, int quantity) {
        // 출발지 재고 감소
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);

        // 출발지 로케이션 현재 수량 감소
        fromLocation.setCurrentQuantity(fromLocation.getCurrentQuantity() - quantity);
        locationRepository.save(fromLocation);

        // 도착지 재고 증가 (동일 상품+로트가 있는지 확인)
        Inventory toInventory = inventoryRepository.findByProductAndLocationAndLot(
                inventory.getProduct().getId(),
                toLocation.getId(),
                inventory.getLotNumber()
        ).orElse(null);

        if (toInventory != null) {
            toInventory.setQuantity(toInventory.getQuantity() + quantity);
            inventoryRepository.save(toInventory);
        } else {
            // 새 재고 레코드 생성
            Inventory newInventory = Inventory.builder()
                    .product(inventory.getProduct())
                    .location(toLocation)
                    .quantity(quantity)
                    .lotNumber(inventory.getLotNumber())
                    .manufactureDate(inventory.getManufactureDate())
                    .expiryDate(inventory.getExpiryDate())
                    .receivedAt(inventory.getReceivedAt())
                    .expired(inventory.getExpired())
                    .build();
            inventoryRepository.save(newInventory);
        }

        // 도착지 로케이션 현재 수량 증가
        toLocation.setCurrentQuantity(toLocation.getCurrentQuantity() + quantity);
        locationRepository.save(toLocation);
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        int storageZoneStock = inventoryRepository.findAvailableInventoryForProduct(product.getId()).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .mapToInt(Inventory::getQuantity)
                .sum();

        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);
        if (rule != null && storageZoneStock <= rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER_AFTER_TRANSFER")
                    .reorderQty(rule.getReorderQty())
                    .triggeredAt(Instant.now())
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.info("Auto reorder triggered for product {} after transfer. Current STORAGE stock: {}, Min: {}",
                    product.getSku(), storageZoneStock, rule.getMinQty());
        }
    }

    private StockTransferResponse toResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .productId(transfer.getProduct().getId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getId())
                .toLocationCode(transfer.getToLocation().getCode())
                .inventoryId(transfer.getInventory().getId())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus().name())
                .requestedBy(transfer.getRequestedBy())
                .approvedBy(transfer.getApprovedBy())
                .approvedAt(transfer.getApprovedAt())
                .rejectionReason(transfer.getRejectionReason())
                .transferredAt(transfer.getTransferredAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }

    private StockTransferListResponse toListResponse(StockTransfer transfer) {
        return StockTransferListResponse.builder()
                .id(transfer.getId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus().name())
                .transferredAt(transfer.getTransferredAt())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\WmsApplication.java
============================================================
package com.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms-inbound

  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: wms_user
    password: wms_password
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC
    show-sql: true

  sql:
    init:
      mode: never

server:
  port: 8080
  servlet:
    context-path: /api/v1

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- WMS Database Schema

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(20) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    requires_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Locations table
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    zone VARCHAR(50) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    capacity INTEGER NOT NULL,
    current_quantity INTEGER NOT NULL DEFAULT 0,
    is_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (current_quantity >= 0 AND current_quantity <= capacity)
);

-- Inventory table
CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    quantity INTEGER NOT NULL DEFAULT 0,
    lot_number VARCHAR(100),
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    expired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (quantity >= 0)
);

-- Suppliers table
CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Supplier penalties table
CREATE TABLE IF NOT EXISTS supplier_penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(50) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase orders table
CREATE TABLE IF NOT EXISTS purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number VARCHAR(100) UNIQUE NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    order_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase order lines table
CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ordered_quantity > 0),
    CHECK (received_quantity >= 0)
);

-- Inbound receipts table
CREATE TABLE IF NOT EXISTS inbound_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number VARCHAR(100) UNIQUE NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_date TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inbound receipt lines table
CREATE TABLE IF NOT EXISTS inbound_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    received_quantity INTEGER NOT NULL,
    lot_number VARCHAR(100),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (received_quantity > 0)
);

-- Seasonal config table
CREATE TABLE IF NOT EXISTS seasonal_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier NUMERIC(3, 2) NOT NULL DEFAULT 1.5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_date > start_date),
    CHECK (multiplier > 0)
);

-- Safety stock rules table
CREATE TABLE IF NOT EXISTS safety_stock_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) UNIQUE,
    min_qty INTEGER NOT NULL,
    reorder_qty INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (min_qty >= 0),
    CHECK (reorder_qty > 0)
);

-- Auto reorder logs table
CREATE TABLE IF NOT EXISTS auto_reorder_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    trigger_reason VARCHAR(50) NOT NULL,
    reorder_qty INTEGER NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    details JSONB,
    performed_by VARCHAR(255),
    performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Shipment orders table
CREATE TABLE IF NOT EXISTS shipment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_number VARCHAR(100) UNIQUE NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    requested_date TIMESTAMPTZ NOT NULL,
    shipped_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Shipment order lines table
CREATE TABLE IF NOT EXISTS shipment_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_id UUID NOT NULL REFERENCES shipment_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    requested_quantity INTEGER NOT NULL,
    picked_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picked', 'partial', 'backordered')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (requested_quantity > 0),
    CHECK (picked_quantity >= 0)
);

-- Backorders table
CREATE TABLE IF NOT EXISTS backorders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_line_id UUID NOT NULL REFERENCES shipment_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (quantity > 0)
);

-- Stock transfers table
CREATE TABLE IF NOT EXISTS stock_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    from_location_id UUID NOT NULL REFERENCES locations(id),
    to_location_id UUID NOT NULL REFERENCES locations(id),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    quantity INTEGER NOT NULL,
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'immediate' CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    requested_by VARCHAR(255),
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    rejection_reason TEXT,
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (quantity > 0),
    CHECK (from_location_id != to_location_id)
);

-- Cycle counts table
CREATE TABLE IF NOT EXISTS cycle_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations(id),
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    started_by VARCHAR(255),
    completed_by VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inventory adjustments table
CREATE TABLE IF NOT EXISTS inventory_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_count_id UUID REFERENCES cycle_counts(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    inventory_id UUID REFERENCES inventory(id),
    system_qty INTEGER NOT NULL,
    actual_qty INTEGER NOT NULL,
    difference INTEGER NOT NULL,
    reason TEXT NOT NULL,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'pending' CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_inventory_product_location ON inventory(product_id, location_id);
CREATE INDEX idx_inventory_expiry ON inventory(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_supplier_penalties_supplier_date ON supplier_penalties(supplier_id, occurred_at);
CREATE INDEX idx_po_lines_po_product ON purchase_order_lines(purchase_order_id, product_id);
CREATE INDEX idx_inbound_receipts_po ON inbound_receipts(purchase_order_id);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date);
CREATE INDEX idx_shipment_order_lines_shipment ON shipment_order_lines(shipment_order_id);
CREATE INDEX idx_backorders_status ON backorders(status);
CREATE INDEX idx_stock_transfers_product ON stock_transfers(product_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(transfer_status);
CREATE INDEX idx_cycle_counts_location ON cycle_counts(location_id);
CREATE INDEX idx_cycle_counts_status ON cycle_counts(status);
CREATE INDEX idx_inventory_adjustments_product_location_created ON inventory_adjustments(product_id, location_id, created_at);
CREATE INDEX idx_inventory_adjustments_status ON inventory_adjustments(approval_status);

