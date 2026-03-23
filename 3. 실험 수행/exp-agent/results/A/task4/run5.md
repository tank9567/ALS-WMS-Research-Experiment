# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 완료 내역

### 1. DB 스키마 (schema.sql)
- `cycle_counts` 테이블 추가
- `inventory_adjustments` 테이블 추가
- 관련 인덱스 추가

### 2. Entity 클래스
- `CycleCount.java`: 실사 세션 엔티티
- `InventoryAdjustment.java`: 재고 조정 엔티티

### 3. Repository
- `CycleCountRepository.java`
- `InventoryAdjustmentRepository.java`
- `InventoryRepository.java`에 `findByProductAndLocation()` 메서드 추가

### 4. DTO
- `CycleCountRequest.java` / `CycleCountResponse.java`
- `InventoryAdjustmentRequest.java` / `InventoryAdjustmentResponse.java`

### 5. Service (비즈니스 로직)
- **CycleCountService**: 
  - 실사 시작 시 로케이션 동결 (is_frozen=true)
  - 실사 완료 시 동결 해제
  
- **InventoryAdjustmentService**:
  - 카테고리별 자동승인 임계치 적용 (GENERAL 5%, FRESH 3%, HAZMAT 1%, HIGH_VALUE 2%)
  - system_qty=0 시 승인 필요
  - HIGH_VALUE 상품은 차이가 있으면 무조건 승인 필요
  - 연속 조정 감시 (7일 내 2회 이상 → 승인 격상, [연속조정감시] 태그 자동 추가)
  - HIGH_VALUE 조정 시 audit_logs 기록
  - 조정 승인/거부 처리
  - 조정 반영 후 안전재고 체크 → 자동 재발주

### 6. Controller (REST API)
- **CycleCountController**:
  - `POST /api/v1/cycle-counts` - 실사 시작
  - `POST /api/v1/cycle-counts/{id}/complete` - 실사 완료
  - `GET /api/v1/cycle-counts/{id}` - 실사 조회
  
- **InventoryAdjustmentController**:
  - `POST /api/v1/inventory-adjustments` - 조정 생성
  - `POST /api/v1/inventory-adjustments/{id}/approve` - 승인
  - `POST /api/v1/inventory-adjustments/{id}/reject` - 거부
  - `GET /api/v1/inventory-adjustments/{id}` - 상세 조회
  - `GET /api/v1/inventory-adjustments` - 목록 조회 (필터링 지원)

모든 요구사항이 구현되었으며, 기존 코드와 일관성을 유지하면서 Spring Boot 표준 구조를 따랐습니다.


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
    <artifactId>warehouse-management-system</artifactId>
    <version>1.0.0</version>
    <name>WMS</name>
    <description>Warehouse Management System</description>

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

        <!-- Validation -->
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

        <!-- Test -->
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
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.service.CycleCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final CycleCountService cycleCountService;

    @PostMapping
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        CycleCountResponse response = cycleCountService.startCycleCount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(@PathVariable UUID id) {
        CycleCountResponse response = cycleCountService.completeCycleCount(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CycleCountResponse>> getCycleCount(@PathVariable UUID id) {
        CycleCountResponse response = cycleCountService.getCycleCount(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "Rejected by user");
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceiptById(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceiptById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InventoryAdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.InventoryAdjustment;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createAdjustment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        String approvedBy = payload.getOrDefault("approvedBy", "ADMIN");
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveAdjustment(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        String rejectedBy = payload.getOrDefault("rejectedBy", "ADMIN");
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectAdjustment(id, rejectedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(@PathVariable UUID id) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustments(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) InventoryAdjustment.ApprovalStatus approvalStatus) {
        List<InventoryAdjustmentResponse> responses = inventoryAdjustmentService.getAdjustments(
                productId, locationId, approvalStatus);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentOrderService;
import jakarta.validation.Valid;
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
            @Valid @RequestBody ShipmentOrderRequest request) {
        ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipmentOrder(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentOrderService.pickShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipmentOrder(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentOrderService.shipShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrderById(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentOrderService.getShipmentOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        List<ShipmentOrderResponse> responses = shipmentOrderService.getAllShipmentOrders();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.executeTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransferById(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransferById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        List<StockTransferResponse> response = stockTransferService.getAllTransfers();
        return ResponseEntity.ok(ApiResponse.success(response));
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
    private ErrorInfo error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorInfo.builder()
                        .message(message)
                        .build())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorInfo.builder()
                        .message(message)
                        .details(details)
                        .build())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorInfo {
        private String message;
        private String details;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Location ID is required")
    private UUID locationId;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {

    private UUID id;
    private UUID locationId;
    private String locationCode;
    private CycleCount.CycleCountStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .updatedAt(cycleCount.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Purchase order ID is required")
    private UUID purchaseOrderId;

    @NotNull(message = "Received date is required")
    private LocalDate receivedDate;

    @NotEmpty(message = "Receipt lines cannot be empty")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "Purchase order line ID is required")
        private UUID purchaseOrderLineId;

        @NotNull(message = "Location ID is required")
        private UUID locationId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.InboundReceipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {
    private UUID id;
    private String receiptNumber;
    private UUID purchaseOrderId;
    private String purchaseOrderNumber;
    private String status;
    private LocalDate receivedDate;
    private String rejectionReason;
    private List<InboundReceiptLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID id;
        private UUID purchaseOrderLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private Integer quantity;
        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }

    public static InboundReceiptResponse from(InboundReceipt receipt, List<InboundReceiptLineResponse> lines) {
        return InboundReceiptResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .purchaseOrderId(receipt.getPurchaseOrder().getId())
                .purchaseOrderNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedDate(receipt.getReceivedDate())
                .rejectionReason(receipt.getRejectionReason())
                .lines(lines)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Location ID is required")
    private UUID locationId;

    @NotNull(message = "Actual quantity is required")
    private Integer actualQty;

    @NotBlank(message = "Reason is required")
    private String reason;

    private UUID cycleCountId;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private InventoryAdjustment.ApprovalStatus approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private UUID cycleCountId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .productId(adjustment.getProduct().getId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getId())
                .locationCode(adjustment.getLocation().getCode())
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .difference(adjustment.getDifference())
                .reason(adjustment.getReason())
                .approvalStatus(adjustment.getApprovalStatus())
                .approvedBy(adjustment.getApprovedBy())
                .approvedAt(adjustment.getApprovedAt())
                .cycleCountId(adjustment.getCycleCount() != null ? adjustment.getCycleCount().getId() : null)
                .createdAt(adjustment.getCreatedAt())
                .updatedAt(adjustment.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class ShipmentOrderRequest {

    @NotNull(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    @NotEmpty(message = "Order lines cannot be empty")
    @Valid
    private List<ShipmentOrderLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineRequest {

        @NotNull(message = "Product ID is required")
        private UUID productId;

        @NotNull(message = "Requested quantity is required")
        private Integer requestedQuantity;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {

    private UUID id;
    private String orderNumber;
    private String customerName;
    private ShipmentOrder.ShipmentStatus status;
    private LocalDate orderDate;
    private LocalDate shipDate;
    private List<ShipmentOrderLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

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
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
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
public class StockTransferRequest {

    private UUID inventoryId;
    private UUID toLocationId;
    private Integer quantity;
    private String requestedBy;
    private String reason;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.StockTransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {

    private UUID id;
    private UUID inventoryId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private StockTransfer.TransferStatus transferStatus;
    private String requestedBy;
    private String approvedBy;
    private String reason;
    private OffsetDateTime transferredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
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

import java.time.OffsetDateTime;
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

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
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

import java.time.OffsetDateTime;
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

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "reorder_quantity", nullable = false)
    private Integer reorderQuantity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
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

import java.time.OffsetDateTime;
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

    @Column(name = "backordered_quantity", nullable = false)
    private Integer backorderedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BackorderStatus status = BackorderStatus.open;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private LocalDate receivedDate;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
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

import java.time.OffsetDateTime;
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
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(nullable = false)
    private Integer difference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_count_id")
    private CycleCount cycleCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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

    @Column(name = "requires_expiry_management", nullable = false)
    private Boolean requiresExpiryManagement = false;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    private LocalDate orderDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
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

import java.time.OffsetDateTime;
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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    @Column(name = "order_number", unique = true, nullable = false, length = 100)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status = ShipmentStatus.pending;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "ship_date")
    private LocalDate shipDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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
    @Column(nullable = false, length = 30)
    private LineStatus status = LineStatus.pending;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 30)
    private TransferStatus transferStatus;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "transferred_at")
    private OffsetDateTime transferredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum TransferStatus {
        immediate,
        pending_approval,
        approved,
        rejected
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

import java.time.OffsetDateTime;
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

    @Column
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

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

import java.time.OffsetDateTime;
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

    @Column
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
    }

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
    private final String details;

    public BusinessException(String message) {
        super(message);
        this.details = null;
    }

    public BusinessException(String message, String details) {
        super(message);
        this.details = details;
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\GlobalExceptionHandler.java
============================================================
package com.wms.exception;

import com.wms.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        ApiResponse<Void> response = ApiResponse.error("Internal server error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\ResourceNotFoundException.java
============================================================
package com.wms.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    Optional<CycleCount> findByLocationIdAndStatus(UUID locationId, CycleCount.CycleCountStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {

    @Query("SELECT irl FROM InboundReceiptLine irl WHERE irl.inboundReceipt.id = :receiptId")
    List<InboundReceiptLine> findByReceiptId(@Param("receiptId") UUID receiptId);
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryAdjustmentRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT COUNT(ia) FROM InventoryAdjustment ia " +
           "WHERE ia.product.id = :productId AND ia.location.id = :locationId " +
           "AND ia.createdAt >= :since")
    long countRecentAdjustments(@Param("productId") UUID productId,
                                @Param("locationId") UUID locationId,
                                @Param("since") OffsetDateTime since);

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus approvalStatus);

    @Query("SELECT ia FROM InventoryAdjustment ia " +
           "WHERE (:productId IS NULL OR ia.product.id = :productId) " +
           "AND (:locationId IS NULL OR ia.location.id = :locationId) " +
           "AND (:approvalStatus IS NULL OR ia.approvalStatus = :approvalStatus)")
    List<InventoryAdjustment> findByFilters(@Param("productId") UUID productId,
                                           @Param("locationId") UUID locationId,
                                           @Param("approvalStatus") InventoryAdjustment.ApprovalStatus approvalStatus);
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

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId " +
           "AND i.lotNumber = :lotNumber AND i.expiryDate = :expiryDate")
    Optional<Inventory> findByProductAndLocationAndLotAndExpiry(
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("lotNumber") String lotNumber,
            @Param("expiryDate") java.time.LocalDate expiryDate);

    List<Inventory> findByProductId(UUID productId);

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId")
    List<Inventory> findByProductAndLocation(@Param("productId") UUID productId,
                                             @Param("locationId") UUID locationId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product.id = :productId AND i.quantity > 0")
    Integer getTotalAvailableQuantity(@Param("productId") UUID productId);
}


============================================================
// FILE: src\main\java\com\wms\repository\LocationRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\ProductRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.id = :purchaseOrderId")
    List<PurchaseOrderLine> findByPurchaseOrderId(@Param("purchaseOrderId") UUID purchaseOrderId);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Modifying
    @Query("UPDATE PurchaseOrder p SET p.status = 'hold' WHERE p.supplier.id = :supplierId AND p.status = 'pending'")
    void holdPendingOrdersBySupplierId(@Param("supplierId") UUID supplierId);
}


============================================================
// FILE: src\main\java\com\wms\repository\SafetyStockRuleRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.SafetyStockRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, UUID> {
    Optional<SafetyStockRule> findByProductId(UUID productId);
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
    Optional<SeasonalConfig> findByDate(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentOrderLineRepository extends JpaRepository<ShipmentOrderLine, UUID> {
    List<ShipmentOrderLine> findByShipmentOrderId(UUID shipmentOrderId);
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

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    List<StockTransfer> findByInventoryProductId(UUID productId);

    List<StockTransfer> findByFromLocationId(UUID locationId);

    List<StockTransfer> findByToLocationId(UUID locationId);

    List<StockTransfer> findByTransferStatus(StockTransfer.TransferStatus status);
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(p) FROM SupplierPenalty p WHERE p.supplier.id = :supplierId AND p.occurredAt >= :since")
    long countBySupplierId(@Param("supplierId") UUID supplierId, @Param("since") OffsetDateTime since);
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
// FILE: src\main\java\com\wms\service\CycleCountService.java
============================================================
package com.wms.service;

import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.CycleCountRepository;
import com.wms.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. 로케이션 조회
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        // 2. 이미 진행 중인 실사가 있는지 체크
        cycleCountRepository.findByLocationIdAndStatus(location.getId(), CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new BusinessException("Cycle count already in progress for this location");
                });

        // 3. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 4. 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedAt(OffsetDateTime.now())
                .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        // 1. 실사 세션 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new BusinessException("Cycle count is not in progress");
        }

        // 2. 실사 완료 처리
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());

        // 3. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    @Transactional(readOnly = true)
    public CycleCountResponse getCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));

        return CycleCountResponse.from(cycleCount);
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InboundReceiptService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new BusinessException("Purchase order is on hold");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .receiptNumber(generateReceiptNumber())
                .purchaseOrder(po)
                .receivedDate(request.getReceivedDate())
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .build();
        receipt = inboundReceiptRepository.save(receipt);

        List<InboundReceiptLineResponse> lineResponses = new ArrayList<>();
        boolean needsApproval = false;
        String rejectionReason = null;

        // 3. 각 라인 검증
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order line not found"));

            Product product = poLine.getProduct();
            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

            // 3.1 실사 동결 체크
            if (location.getIsFrozen()) {
                rejectionReason = "Location is frozen for cycle count";
                break;
            }

            // 3.2 보관유형 호환성 체크
            if (!isStorageTypeCompatible(product, location)) {
                rejectionReason = "Storage type incompatible: product=" + product.getStorageType() +
                                 ", location=" + location.getStorageType();
                break;
            }

            // 3.3 HAZMAT zone 체크
            if (product.getStorageType() == Product.StorageType.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
                rejectionReason = "HAZMAT product must be stored in HAZMAT zone";
                break;
            }

            // 3.4 유통기한 필수 체크
            if (product.getRequiresExpiryManagement()) {
                if (lineReq.getExpiryDate() == null) {
                    rejectionReason = "Expiry date is required for this product";
                    break;
                }
                if (lineReq.getManufactureDate() == null) {
                    rejectionReason = "Manufacture date is required for this product";
                    break;
                }
            }

            // 3.5 초과입고 허용률 체크
            int totalReceived = poLine.getReceivedQuantity() + lineReq.getQuantity();
            double allowedRate = calculateAllowedOverReceiveRate(product.getCategory(), po.getPoType(), request.getReceivedDate());
            int maxAllowed = (int) (poLine.getOrderedQuantity() * (1 + allowedRate / 100.0));

            if (totalReceived > maxAllowed) {
                rejectionReason = String.format("Over delivery: ordered=%d, max_allowed=%d, total_received=%d",
                        poLine.getOrderedQuantity(), maxAllowed, totalReceived);

                // 공급업체 페널티
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, rejectionReason);
                break;
            }

            // 3.6 유통기한 잔여율 체크
            if (product.getRequiresExpiryManagement() && lineReq.getExpiryDate() != null && lineReq.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        request.getReceivedDate()
                );

                Integer minPct = product.getMinRemainingShelfLifePct() != null ?
                                product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minPct) {
                    rejectionReason = String.format("Shelf life too short: remaining=%.1f%%, required>=%d%%",
                            remainingPct, minPct);

                    // 공급업체 페널티
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE, rejectionReason);
                    break;
                } else if (remainingPct >= minPct && remainingPct <= 50) {
                    needsApproval = true;
                }
            }

            // 3.7 용량 체크
            if (location.getCurrentQuantity() + lineReq.getQuantity() > location.getCapacity()) {
                rejectionReason = "Location capacity exceeded";
                break;
            }

            // 라인 저장
            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .manufactureDate(lineReq.getManufactureDate())
                    .expiryDate(lineReq.getExpiryDate())
                    .build();
            line = inboundReceiptLineRepository.save(line);

            lineResponses.add(InboundReceiptLineResponse.builder()
                    .id(line.getId())
                    .purchaseOrderLineId(poLine.getId())
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .locationId(location.getId())
                    .locationCode(location.getCode())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .manufactureDate(line.getManufactureDate())
                    .expiryDate(line.getExpiryDate())
                    .build());
        }

        // 4. 최종 상태 결정
        if (rejectionReason != null) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
            receipt.setRejectionReason(rejectionReason);
        } else if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        receipt = inboundReceiptRepository.save(receipt);

        // 5. 공급업체 페널티 체크 (3회 이상이면 PO hold)
        if (rejectionReason != null) {
            checkAndHoldSupplierOrders(po.getSupplier());
        }

        return InboundReceiptResponse.from(receipt, lineResponses);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("Cannot confirm receipt in status: " + receipt.getStatus());
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);

        // 재고 반영
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Optional<Inventory> existingInv = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber(),
                    line.getExpiryDate()
            );

            if (existingInv.isPresent()) {
                Inventory inv = existingInv.get();
                inv.setQuantity(inv.getQuantity() + line.getQuantity());
                inventoryRepository.save(inv);
            } else {
                Inventory inv = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .lotNumber(line.getLotNumber())
                        .quantity(line.getQuantity())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(OffsetDateTime.now())
                        .build();
                inventoryRepository.save(inv);
            }

            // 로케이션 용량 증가
            Location location = line.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + line.getQuantity());
            locationRepository.save(location);

            // PO Line 수량 갱신
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getId());

        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt = inboundReceiptRepository.save(receipt);

        return getInboundReceiptById(receiptId);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("Cannot reject receipt in status: " + receipt.getStatus());
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receipt.setRejectionReason(reason);
        receipt = inboundReceiptRepository.save(receipt);

        return getInboundReceiptById(receiptId);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("Receipt is not pending approval");
        }

        // pending_approval → inspecting으로 변경 후 확정 가능 상태로
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receipt = inboundReceiptRepository.save(receipt);

        // 자동으로 확정 처리
        return confirmInboundReceipt(receiptId);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceiptById(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound receipt not found"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);

        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .purchaseOrderLineId(line.getPurchaseOrderLine().getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .quantity(line.getQuantity())
                        .lotNumber(line.getLotNumber())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.from(receipt, lineResponses);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(receipt -> getInboundReceiptById(receipt.getId()))
                .collect(Collectors.toList());
    }

    // === Helper methods ===

    private String generateReceiptNumber() {
        return "IR-" + System.currentTimeMillis();
    }

    private boolean isStorageTypeCompatible(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT은 HAZMAT끼리만
        if (productType == Product.StorageType.HAZMAT) {
            return locationType == Product.StorageType.HAZMAT;
        }

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN) {
            return locationType == Product.StorageType.FROZEN;
        }

        // COLD 상품 → COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD) {
            return locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
        }

        // AMBIENT 상품 → AMBIENT만
        if (productType == Product.StorageType.AMBIENT) {
            return locationType == Product.StorageType.AMBIENT;
        }

        return false;
    }

    private double calculateAllowedOverReceiveRate(
            Product.ProductCategory category,
            PurchaseOrder.PoType poType,
            LocalDate receivedDate) {

        // 기본 허용률
        double baseRate;
        switch (category) {
            case GENERAL:
                baseRate = 10.0;
                break;
            case FRESH:
                baseRate = 5.0;
                break;
            case HAZMAT:
                return 0.0; // HAZMAT은 항상 0%
            case HIGH_VALUE:
                baseRate = 3.0;
                break;
            default:
                baseRate = 10.0;
        }

        // PO 유형별 가중치
        double poMultiplier = 1.0;
        switch (poType) {
            case NORMAL:
                poMultiplier = 1.0;
                break;
            case URGENT:
                poMultiplier = 2.0;
                break;
            case IMPORT:
                poMultiplier = 1.5;
                break;
        }

        // 성수기 가중치
        double seasonalMultiplier = 1.0;
        Optional<SeasonalConfig> seasonal = seasonalConfigRepository.findByDate(receivedDate);
        if (seasonal.isPresent()) {
            seasonalMultiplier = seasonal.get().getMultiplier().doubleValue();
        }

        // HAZMAT은 어떤 경우에도 0%
        if (category == Product.ProductCategory.HAZMAT) {
            return 0.0;
        }

        return baseRate * poMultiplier * seasonalMultiplier;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .reason(reason)
                .occurredAt(OffsetDateTime.now())
                .build();
        supplierPenaltyRepository.save(penalty);
    }

    private void checkAndHoldSupplierOrders(Supplier supplier) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // PO를 hold로 변경
            purchaseOrderRepository.holdPendingOrdersBySupplierId(supplier.getId());

            // Supplier 상태도 hold로
            supplier.setStatus(Supplier.SupplierStatus.hold);
            supplierRepository.save(supplier);
        }
    }

    private void updatePurchaseOrderStatus(UUID purchaseOrderId) {
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrderId);

        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : lines) {
            if (line.getReceivedQuantity() > 0) {
                anyReceived = true;
            }
            if (line.getReceivedQuantity() < line.getOrderedQuantity()) {
                allCompleted = false;
            }
        }

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InventoryAdjustmentService.java
============================================================
package com.wms.service;

import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final CycleCountRepository cycleCountRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        CycleCount cycleCount = null;
        if (request.getCycleCountId() != null) {
            cycleCount = cycleCountRepository.findById(request.getCycleCountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));
        }

        // 2. 시스템 재고 수량 조회
        int systemQty = inventoryRepository.findByProductAndLocation(product.getId(), location.getId())
                .stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 차이 계산
        int difference = request.getActualQty() - systemQty;

        // 4. 사유 체크
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("Reason is required");
        }

        String reason = request.getReason();

        // 5. 연속 조정 감시 (최근 7일 내 2회 이상)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        long recentAdjustments = inventoryAdjustmentRepository.countRecentAdjustments(
                product.getId(), location.getId(), sevenDaysAgo);

        if (recentAdjustments >= 2) {
            reason = "[연속조정감시] " + reason;
        }

        // 6. 승인 상태 결정
        InventoryAdjustment.ApprovalStatus approvalStatus;

        if (systemQty == 0 && request.getActualQty() > 0) {
            // 시스템 재고 0인데 실물 발견 → 무조건 승인 필요
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        } else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            // HIGH_VALUE 상품은 차이가 있으면 무조건 승인 필요
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        } else if (recentAdjustments >= 2) {
            // 연속 조정 감지 → 승인 필요
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        } else {
            // 카테고리별 임계치 체크
            double threshold = getAutoApprovalThreshold(product.getCategory());
            double diffPercent = systemQty == 0 ? 0 : Math.abs((double) difference / systemQty) * 100;

            if (diffPercent <= threshold) {
                approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
            } else {
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            }
        }

        // 7. 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .systemQty(systemQty)
                .actualQty(request.getActualQty())
                .difference(difference)
                .reason(reason)
                .approvalStatus(approvalStatus)
                .cycleCount(cycleCount)
                .build();

        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            adjustment.setApprovedBy("SYSTEM");
            adjustment.setApprovedAt(OffsetDateTime.now());
            // 자동 승인 시 즉시 재고 반영
            applyAdjustment(adjustment);
        }

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // 8. HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT")
                    .entityType("InventoryAdjustment")
                    .entityId(adjustment.getId())
                    .description("HIGH_VALUE product adjustment: " + product.getSku() +
                            ", Location: " + location.getCode() +
                            ", Difference: " + difference +
                            ", Reason: " + reason)
                    .performedBy(approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED ? "SYSTEM" : "PENDING")
                    .build();
            auditLogRepository.save(auditLog);
        }

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        // 재고 반영
        applyAdjustment(adjustment);

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // HIGH_VALUE 감사 로그 업데이트
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT_APPROVED")
                    .entityType("InventoryAdjustment")
                    .entityId(adjustment.getId())
                    .description("HIGH_VALUE adjustment approved by " + approvedBy)
                    .performedBy(approvedBy)
                    .build();
            auditLogRepository.save(auditLog);
        }

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, String rejectedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(rejectedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment not found"));

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustments(UUID productId, UUID locationId,
                                                           InventoryAdjustment.ApprovalStatus approvalStatus) {
        List<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findByFilters(
                productId, locationId, approvalStatus);

        return adjustments.stream()
                .map(InventoryAdjustmentResponse::from)
                .collect(Collectors.toList());
    }

    private double getAutoApprovalThreshold(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 5.0;
            case FRESH:
                return 3.0;
            case HAZMAT:
                return 1.0;
            case HIGH_VALUE:
                return 2.0;
            default:
                return 5.0;
        }
    }

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        int difference = adjustment.getDifference();

        if (difference != 0) {
            // 재고 조정 반영
            List<Inventory> inventories = inventoryRepository.findByProductAndLocation(
                    product.getId(), location.getId());

            if (inventories.isEmpty() && difference > 0) {
                // 새 재고 생성
                Inventory newInventory = Inventory.builder()
                        .product(product)
                        .location(location)
                        .quantity(difference)
                        .receivedAt(OffsetDateTime.now())
                        .build();
                inventoryRepository.save(newInventory);

                // 로케이션 수량 업데이트
                location.setCurrentQuantity(location.getCurrentQuantity() + difference);

            } else if (!inventories.isEmpty()) {
                // 기존 재고 조정
                Inventory inventory = inventories.get(0);
                int newQty = inventory.getQuantity() + difference;

                if (newQty < 0) {
                    throw new BusinessException("Resulting inventory quantity cannot be negative");
                }

                inventory.setQuantity(newQty);
                inventoryRepository.save(inventory);

                // 로케이션 수량 업데이트
                location.setCurrentQuantity(location.getCurrentQuantity() + difference);
            }

            // 안전재고 체크
            checkSafetyStock(product);
        }
    }

    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule != null) {
            // 전체 가용 재고 계산
            int totalAvailableQty = inventoryRepository.findByProductId(product.getId())
                    .stream()
                    .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(java.time.LocalDate.now()))
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            if (totalAvailableQty <= rule.getMinQty()) {
                // 자동 재발주 요청 생성
                AutoReorderLog reorderLog = AutoReorderLog.builder()
                        .product(product)
                        .triggerReason("SAFETY_STOCK_TRIGGER")
                        .currentQty(totalAvailableQty)
                        .minQty(rule.getMinQty())
                        .reorderQty(rule.getReorderQty())
                        .build();
                autoReorderLogRepository.save(reorderLog);
            }
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
        // 1. 출고 지시서 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .orderNumber(generateOrderNumber())
                .customerName(request.getCustomerName())
                .orderDate(request.getOrderDate())
                .status(ShipmentOrder.ShipmentStatus.pending)
                .build();
        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // 2. HAZMAT/FRESH 분리 출고 처리
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> normalLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                normalLines.add(lineReq);
            }
        }

        // 3. HAZMAT 상품이 FRESH와 함께 있으면 분리
        boolean hasFresh = false;
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : normalLines) {
            Product product = productRepository.findById(lineReq.getProductId()).get();
            if (product.getCategory() == Product.ProductCategory.FRESH) {
                hasFresh = true;
                break;
            }
        }

        ShipmentOrder finalShipmentOrder = shipmentOrder;
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = new ArrayList<>();

        // 4. 정상 라인 처리 (비-HAZMAT)
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : normalLines) {
            Product product = productRepository.findById(lineReq.getProductId()).get();

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(finalShipmentOrder)
                    .product(product)
                    .requestedQuantity(lineReq.getRequestedQuantity())
                    .pickedQuantity(0)
                    .status(ShipmentOrderLine.LineStatus.pending)
                    .build();
            line = shipmentOrderLineRepository.save(line);

            lineResponses.add(ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                    .id(line.getId())
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .requestedQuantity(line.getRequestedQuantity())
                    .pickedQuantity(line.getPickedQuantity())
                    .status(line.getStatus().name())
                    .build());
        }

        // 5. HAZMAT 라인이 있고 FRESH와 함께 있으면 별도 출고 지시서 생성
        if (!hazmatLines.isEmpty() && hasFresh) {
            ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                    .orderNumber(generateOrderNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .orderDate(request.getOrderDate())
                    .status(ShipmentOrder.ShipmentStatus.pending)
                    .build();
            hazmatShipment = shipmentOrderRepository.save(hazmatShipment);

            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : hazmatLines) {
                Product product = productRepository.findById(lineReq.getProductId()).get();

                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(hazmatShipment)
                        .product(product)
                        .requestedQuantity(lineReq.getRequestedQuantity())
                        .pickedQuantity(0)
                        .status(ShipmentOrderLine.LineStatus.pending)
                        .build();
                shipmentOrderLineRepository.save(line);
            }
        } else if (!hazmatLines.isEmpty()) {
            // FRESH가 없으면 HAZMAT도 동일 출고 지시서에 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : hazmatLines) {
                Product product = productRepository.findById(lineReq.getProductId()).get();

                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(finalShipmentOrder)
                        .product(product)
                        .requestedQuantity(lineReq.getRequestedQuantity())
                        .pickedQuantity(0)
                        .status(ShipmentOrderLine.LineStatus.pending)
                        .build();
                line = shipmentOrderLineRepository.save(line);

                lineResponses.add(ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                        .id(line.getId())
                        .productId(product.getId())
                        .productSku(product.getSku())
                        .productName(product.getName())
                        .requestedQuantity(line.getRequestedQuantity())
                        .pickedQuantity(line.getPickedQuantity())
                        .status(line.getStatus().name())
                        .build());
            }
        }

        return ShipmentOrderResponse.builder()
                .id(finalShipmentOrder.getId())
                .orderNumber(finalShipmentOrder.getOrderNumber())
                .customerName(finalShipmentOrder.getCustomerName())
                .status(finalShipmentOrder.getStatus())
                .orderDate(finalShipmentOrder.getOrderDate())
                .shipDate(finalShipmentOrder.getShipDate())
                .lines(lineResponses)
                .createdAt(finalShipmentOrder.getCreatedAt())
                .updatedAt(finalShipmentOrder.getUpdatedAt())
                .build();
    }

    @Transactional
    public ShipmentOrderResponse pickShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("Cannot pick shipment in status: " + shipmentOrder.getStatus());
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);
        shipmentOrderRepository.save(shipmentOrder);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);

        for (ShipmentOrderLine line : lines) {
            Product product = line.getProduct();

            // FIFO/FEFO 피킹
            List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

            int remainingQty = line.getRequestedQuantity();
            int totalPicked = 0;

            // HAZMAT 1회 출고 최대 수량 체크
            Integer maxPickQty = product.getMaxPickQty();
            if (product.getCategory() == Product.ProductCategory.HAZMAT && maxPickQty != null) {
                if (remainingQty > maxPickQty) {
                    throw new BusinessException(
                            String.format("HAZMAT product %s exceeds max pick quantity: requested=%d, max=%d",
                                    product.getSku(), remainingQty, maxPickQty));
                }
            }

            for (Inventory inv : availableInventories) {
                if (remainingQty <= 0) break;

                // 실사 동결 로케이션 체크
                if (inv.getLocation().getIsFrozen()) {
                    continue;
                }

                // HAZMAT zone 체크
                if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                    if (inv.getLocation().getZone() != Location.Zone.HAZMAT) {
                        continue;
                    }
                }

                int pickQty = Math.min(inv.getQuantity(), remainingQty);

                // 재고 차감
                inv.setQuantity(inv.getQuantity() - pickQty);
                inventoryRepository.save(inv);

                // 로케이션 용량 차감
                Location location = inv.getLocation();
                location.setCurrentQuantity(location.getCurrentQuantity() - pickQty);
                locationRepository.save(location);

                // 보관 유형 불일치 경고
                if (product.getStorageType() != location.getStorageType()) {
                    logStorageTypeMismatch(product, location);
                }

                totalPicked += pickQty;
                remainingQty -= pickQty;
            }

            line.setPickedQuantity(totalPicked);

            // 부분출고 의사결정 트리
            if (totalPicked == 0) {
                // 가용 재고 = 0: 전량 백오더
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                createBackorder(line, line.getRequestedQuantity());
            } else if (totalPicked < line.getRequestedQuantity()) {
                int backorderQty = line.getRequestedQuantity() - totalPicked;
                double fulfillmentRate = (totalPicked * 100.0) / line.getRequestedQuantity();

                if (fulfillmentRate >= 70) {
                    // 가용 재고 ≥ 70%: 부분출고 + 백오더
                    line.setStatus(ShipmentOrderLine.LineStatus.partial);
                    createBackorder(line, backorderQty);
                } else if (fulfillmentRate >= 30) {
                    // 가용 재고 30% ~ 70%: 부분출고 + 백오더 + 긴급발주
                    line.setStatus(ShipmentOrderLine.LineStatus.partial);
                    createBackorder(line, backorderQty);
                    createUrgentReorder(product, "PARTIAL_SHIPMENT_30_70");
                } else {
                    // 가용 재고 < 30%: 전량 백오더 (부분출고 안 함)
                    // 피킹한 것을 다시 되돌림
                    rollbackPicking(line, totalPicked, availableInventories);
                    line.setPickedQuantity(0);
                    line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                    createBackorder(line, line.getRequestedQuantity());
                }
            } else {
                // 전량 피킹 완료
                line.setStatus(ShipmentOrderLine.LineStatus.picked);
            }

            shipmentOrderLineRepository.save(line);
        }

        // 출고 지시서 상태 갱신
        updateShipmentOrderStatus(shipmentOrderId);

        return getShipmentOrderById(shipmentOrderId);
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
            shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new BusinessException("Cannot ship order in status: " + shipmentOrder.getStatus());
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        shipmentOrder.setShipDate(LocalDate.now());
        shipmentOrderRepository.save(shipmentOrder);

        // 출고 완료 후 안전재고 체크
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProduct());
        }

        return getShipmentOrderById(shipmentOrderId);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrderById(UUID shipmentOrderId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);

        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
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
                .id(shipmentOrder.getId())
                .orderNumber(shipmentOrder.getOrderNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus())
                .orderDate(shipmentOrder.getOrderDate())
                .shipDate(shipmentOrder.getShipDate())
                .lines(lineResponses)
                .createdAt(shipmentOrder.getCreatedAt())
                .updatedAt(shipmentOrder.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> shipmentOrders = shipmentOrderRepository.findAll();
        return shipmentOrders.stream()
                .map(order -> getShipmentOrderById(order.getId()))
                .collect(Collectors.toList());
    }

    // === Helper methods ===

    private String generateOrderNumber() {
        return "SO-" + System.currentTimeMillis();
    }

    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        List<Inventory> allInventories = inventoryRepository.findByProductId(product.getId());

        LocalDate today = LocalDate.now();

        // 필터링: 만료된 재고 제외, 잔여율 <10% 제외
        List<Inventory> validInventories = allInventories.stream()
                .filter(inv -> {
                    // 수량이 0이면 제외
                    if (inv.getQuantity() <= 0) return false;

                    // 유통기한 관리 상품인 경우
                    if (product.getRequiresExpiryManagement() && inv.getExpiryDate() != null) {
                        // 만료된 재고 제외
                        if (inv.getExpiryDate().isBefore(today)) {
                            return false;
                        }

                        // 잔여율 계산
                        if (inv.getManufactureDate() != null) {
                            double remainingPct = calculateRemainingShelfLifePct(
                                    inv.getManufactureDate(),
                                    inv.getExpiryDate(),
                                    today
                            );

                            // 잔여율 <10%: 출고 불가 (폐기 대상)
                            if (remainingPct < 10.0) {
                                markAsExpired(inv);
                                return false;
                            }
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        // FEFO + 잔여율 기반 정렬
        validInventories.sort((inv1, inv2) -> {
            // 유통기한 관리 상품
            if (product.getRequiresExpiryManagement()) {
                if (inv1.getExpiryDate() != null && inv2.getExpiryDate() != null) {
                    // 잔여율 <30% 재고 최우선
                    double pct1 = calculateRemainingShelfLifePct(
                            inv1.getManufactureDate(),
                            inv1.getExpiryDate(),
                            today
                    );
                    double pct2 = calculateRemainingShelfLifePct(
                            inv2.getManufactureDate(),
                            inv2.getExpiryDate(),
                            today
                    );

                    boolean urgent1 = pct1 < 30.0;
                    boolean urgent2 = pct2 < 30.0;

                    if (urgent1 && !urgent2) return -1;
                    if (!urgent1 && urgent2) return 1;

                    // FEFO: 유통기한 빠른 순
                    int expiryCompare = inv1.getExpiryDate().compareTo(inv2.getExpiryDate());
                    if (expiryCompare != 0) return expiryCompare;
                }
            }

            // FIFO: 입고일 빠른 순
            return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
        });

        return validInventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) return 100.0;

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) return 0.0;

        return (remainingDays * 100.0) / totalDays;
    }

    private void markAsExpired(Inventory inv) {
        // 만료 재고로 표시 (실제로는 별도 컬럼이 필요하지만, 여기서는 수량을 0으로 설정)
        // 실무에서는 is_expired 컬럼을 추가하는 것이 좋음
        inv.setQuantity(0);
        inventoryRepository.save(inv);
    }

    private void createBackorder(ShipmentOrderLine line, int backorderQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .backorderedQuantity(backorderQty)
                .status(Backorder.BackorderStatus.open)
                .build();
        backorderRepository.save(backorder);
    }

    private void createUrgentReorder(Product product, String reason) {
        int currentStock = inventoryRepository.getTotalAvailableQuantity(product.getId());

        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        int reorderQty = ruleOpt.map(SafetyStockRule::getReorderQty).orElse(100);

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason(reason)
                .currentStock(currentStock)
                .reorderQuantity(reorderQty)
                .build();
        autoReorderLogRepository.save(log);
    }

    private void checkSafetyStock(Product product) {
        int totalStock = inventoryRepository.getTotalAvailableQuantity(product.getId());

        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());

        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();

            if (totalStock <= rule.getMinQty()) {
                AutoReorderLog log = AutoReorderLog.builder()
                        .product(product)
                        .triggerReason("SAFETY_STOCK_TRIGGER")
                        .currentStock(totalStock)
                        .reorderQuantity(rule.getReorderQty())
                        .build();
                autoReorderLogRepository.save(log);
            }
        }
    }

    private void logStorageTypeMismatch(Product product, Location location) {
        AuditLog log = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(product.getId())
                .description(String.format("Product %s (storage_type=%s) picked from location %s (storage_type=%s)",
                        product.getSku(), product.getStorageType(), location.getCode(), location.getStorageType()))
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(log);
    }

    private void rollbackPicking(ShipmentOrderLine line, int pickedQty, List<Inventory> inventories) {
        // 피킹한 수량을 다시 재고에 되돌림
        int remaining = pickedQty;

        for (Inventory inv : inventories) {
            if (remaining <= 0) break;

            int restoreQty = Math.min(remaining, pickedQty);
            inv.setQuantity(inv.getQuantity() + restoreQty);
            inventoryRepository.save(inv);

            Location location = inv.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + restoreQty);
            locationRepository.save(location);

            remaining -= restoreQty;
        }
    }

    private void updateShipmentOrderStatus(UUID shipmentOrderId) {
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipmentOrderId);

        boolean allPicked = true;
        boolean anyPicked = false;
        boolean anyBackordered = false;

        for (ShipmentOrderLine line : lines) {
            if (line.getStatus() == ShipmentOrderLine.LineStatus.picked ||
                line.getStatus() == ShipmentOrderLine.LineStatus.partial) {
                anyPicked = true;
            }

            if (line.getStatus() == ShipmentOrderLine.LineStatus.backordered ||
                line.getStatus() == ShipmentOrderLine.LineStatus.partial) {
                allPicked = false;
            }

            if (line.getStatus() == ShipmentOrderLine.LineStatus.backordered) {
                anyBackordered = true;
            }
        }

        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment order not found"));

        if (allPicked && anyPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);
        } else if (anyPicked || anyBackordered) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }

        shipmentOrderRepository.save(shipmentOrder);
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        Location fromLocation = inventory.getLocation();
        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found"));

        Product product = inventory.getProduct();

        // 2. 기본 검증
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new BusinessException("Cannot transfer to the same location");
        }

        if (request.getQuantity() <= 0) {
            throw new BusinessException("Transfer quantity must be positive");
        }

        if (inventory.getQuantity() < request.getQuantity()) {
            throw new BusinessException("Insufficient inventory quantity");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("Source location is frozen for cycle count");
        }

        if (toLocation.getIsFrozen()) {
            throw new BusinessException("Destination location is frozen for cycle count");
        }

        // 4. 용량 체크
        if (toLocation.getCurrentQuantity() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("Destination location capacity exceeded");
        }

        // 5. 보관 유형 호환성 체크
        if (!isStorageTypeCompatible(product, toLocation)) {
            throw new BusinessException("Storage type incompatible: product=" + product.getStorageType() +
                    ", location=" + toLocation.getStorageType());
        }

        // 6. HAZMAT 혼적 금지
        if (!isHazmatMixingAllowed(product, toLocation)) {
            throw new BusinessException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
        }

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getRequiresExpiryManagement() && inventory.getExpiryDate() != null) {
            validateExpiryDateForTransfer(inventory, toLocation);
        }

        // 8. 대량 이동 승인 체크
        boolean requiresApproval = false;
        double transferPercentage = (double) request.getQuantity() / inventory.getQuantity() * 100;
        if (transferPercentage >= 80) {
            requiresApproval = true;
        }

        StockTransfer.TransferStatus status = requiresApproval
                ? StockTransfer.TransferStatus.pending_approval
                : StockTransfer.TransferStatus.immediate;

        // 9. StockTransfer 생성
        StockTransfer transfer = StockTransfer.builder()
                .inventory(inventory)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .transferStatus(status)
                .requestedBy(request.getRequestedBy())
                .reason(request.getReason())
                .build();

        // 10. 즉시 이동인 경우 재고 변경
        if (status == StockTransfer.TransferStatus.immediate) {
            executeInventoryTransfer(inventory, fromLocation, toLocation, request.getQuantity());
            transfer.setTransferredAt(OffsetDateTime.now());
        }

        transfer = stockTransferRepository.save(transfer);

        // 11. 안전재고 체크 (STORAGE zone 대상)
        if (status == StockTransfer.TransferStatus.immediate) {
            checkSafetyStockAfterTransfer(product);
        }

        return convertToResponse(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("Transfer is not pending approval");
        }

        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();

        // 재검증 (승인 대기 중에 상태가 변경되었을 수 있음)
        if (inventory.getQuantity() < transfer.getQuantity()) {
            throw new BusinessException("Insufficient inventory quantity");
        }

        if (toLocation.getCurrentQuantity() + transfer.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("Destination location capacity exceeded");
        }

        // 재고 이동 실행
        executeInventoryTransfer(inventory, fromLocation, toLocation, transfer.getQuantity());

        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setTransferredAt(OffsetDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        // 안전재고 체크
        checkSafetyStockAfterTransfer(inventory.getProduct());

        return convertToResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        transfer = stockTransferRepository.save(transfer);

        return convertToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransferById(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found"));
        return convertToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 비즈니스 로직 헬퍼 메서드

    private void executeInventoryTransfer(Inventory inventory, Location fromLocation,
                                          Location toLocation, Integer quantity) {
        // 출발지 재고 감소
        inventory.setQuantity(inventory.getQuantity() - quantity);
        fromLocation.setCurrentQuantity(fromLocation.getCurrentQuantity() - quantity);
        inventoryRepository.save(inventory);
        locationRepository.save(fromLocation);

        // 도착지 재고 증가 (동일한 상품+로트+유통기한 조합이 있으면 합산, 없으면 신규 생성)
        Inventory toInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                inventory.getProduct().getId(),
                toLocation.getId(),
                inventory.getLotNumber(),
                inventory.getExpiryDate()
        ).orElse(null);

        if (toInventory != null) {
            // 기존 재고에 합산
            toInventory.setQuantity(toInventory.getQuantity() + quantity);
            inventoryRepository.save(toInventory);
        } else {
            // 신규 재고 생성
            Inventory newInventory = Inventory.builder()
                    .product(inventory.getProduct())
                    .location(toLocation)
                    .lotNumber(inventory.getLotNumber())
                    .quantity(quantity)
                    .manufactureDate(inventory.getManufactureDate())
                    .expiryDate(inventory.getExpiryDate())
                    .receivedAt(inventory.getReceivedAt())
                    .build();
            inventoryRepository.save(newInventory);
        }

        toLocation.setCurrentQuantity(toLocation.getCurrentQuantity() + quantity);
        locationRepository.save(toLocation);
    }

    private boolean isStorageTypeCompatible(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT은 zone으로 체크 (별도 처리)
        if (productType == Product.StorageType.HAZMAT) {
            return location.getZone() == Location.Zone.HAZMAT;
        }

        // FROZEN → FROZEN만 허용
        if (productType == Product.StorageType.FROZEN) {
            return locationType == Product.StorageType.FROZEN;
        }

        // COLD → COLD, FROZEN 허용
        if (productType == Product.StorageType.COLD) {
            return locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
        }

        // AMBIENT → AMBIENT만 허용 (하위 호환 불가, 상위 호환만 가능)
        if (productType == Product.StorageType.AMBIENT) {
            return locationType == Product.StorageType.AMBIENT;
        }

        return true;
    }

    private boolean isHazmatMixingAllowed(Product product, Location toLocation) {
        List<Inventory> existingInventories = inventoryRepository.findByProductId(product.getId());

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        // 도착지에 이미 있는 재고 확인
        List<Inventory> toLocationInventories = existingInventories.stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .collect(Collectors.toList());

        for (Inventory inv : toLocationInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT;
            if (isHazmat != existingIsHazmat) {
                return false; // HAZMAT과 비-HAZMAT 혼적 금지
            }
        }

        return true;
    }

    private void validateExpiryDateForTransfer(Inventory inventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();
        LocalDate manufactureDate = inventory.getManufactureDate();

        // 유통기한 만료
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("Cannot transfer expired products");
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

            // 잔여 유통기한 < 10%: SHIPPING zone만 허용
            if (remainingPct < 10) {
                if (toLocation.getZone() != Location.Zone.SHIPPING) {
                    throw new BusinessException("Products with <10% remaining shelf life can only be transferred to SHIPPING zone");
                }
            }
        }
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);
        if (rule == null) {
            return; // 안전재고 규칙이 없으면 체크 안 함
        }

        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventories = inventoryRepository.findByProductId(product.getId()).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .collect(Collectors.toList());

        int totalStorageQuantity = storageInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalStorageQuantity <= rule.getMinQty()) {
            // 자동 재발주 요청 생성
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .currentQty(totalStorageQuantity)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .reason("SAFETY_STOCK_TRIGGER")
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    private StockTransferResponse convertToResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .inventoryId(transfer.getInventory().getId())
                .productSku(transfer.getInventory().getProduct().getSku())
                .productName(transfer.getInventory().getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getId())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus())
                .requestedBy(transfer.getRequestedBy())
                .approvedBy(transfer.getApprovedBy())
                .reason(transfer.getReason())
                .transferredAt(transfer.getTransferredAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
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
    name: wms
  datasource:
    url: jdbc:postgresql://localhost:5432/wms
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

server:
  port: 8080


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- Drop tables if exist
DROP TABLE IF EXISTS inbound_receipt_lines CASCADE;
DROP TABLE IF EXISTS inbound_receipts CASCADE;
DROP TABLE IF EXISTS purchase_order_lines CASCADE;
DROP TABLE IF EXISTS purchase_orders CASCADE;
DROP TABLE IF EXISTS supplier_penalties CASCADE;
DROP TABLE IF EXISTS suppliers CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS seasonal_config CASCADE;

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(20) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    requires_expiry_management BOOLEAN NOT NULL DEFAULT false,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    shelf_life_days INTEGER,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Locations table
CREATE TABLE locations (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    zone VARCHAR(50) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    capacity INTEGER NOT NULL,
    current_quantity INTEGER NOT NULL DEFAULT 0,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    is_frozen BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_current_quantity_positive CHECK (current_quantity >= 0),
    CONSTRAINT chk_capacity_positive CHECK (capacity > 0),
    CONSTRAINT chk_current_quantity_lte_capacity CHECK (current_quantity <= capacity)
);

-- Inventory table
CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    quantity INTEGER NOT NULL DEFAULT 0,
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quantity_positive CHECK (quantity >= 0),
    UNIQUE (product_id, location_id, lot_number, expiry_date)
);

-- Suppliers table
CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Supplier penalties table
CREATE TABLE supplier_penalties (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(50) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase orders table
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    po_number VARCHAR(100) UNIQUE NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    order_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase order lines table
CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ordered_quantity_positive CHECK (ordered_quantity > 0),
    CONSTRAINT chk_received_quantity_nonnegative CHECK (received_quantity >= 0)
);

-- Inbound receipts table
CREATE TABLE inbound_receipts (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(100) UNIQUE NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_date DATE NOT NULL,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inbound receipt lines table
CREATE TABLE inbound_receipt_lines (
    id UUID PRIMARY KEY,
    inbound_receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    quantity INTEGER NOT NULL,
    lot_number VARCHAR(100),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
);

-- Seasonal config table
CREATE TABLE seasonal_config (
    id UUID PRIMARY KEY,
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(3, 2) NOT NULL DEFAULT 1.5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_end_date_after_start_date CHECK (end_date >= start_date)
);

-- Cycle counts table
CREATE TABLE cycle_counts (
    id UUID PRIMARY KEY,
    location_id UUID NOT NULL REFERENCES locations(id),
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inventory adjustments table
CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    system_qty INTEGER NOT NULL,
    actual_qty INTEGER NOT NULL,
    difference INTEGER NOT NULL,
    reason TEXT NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ,
    cycle_count_id UUID REFERENCES cycle_counts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_locations_code ON locations(code);
CREATE INDEX idx_locations_zone ON locations(zone);
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_location_id ON inventory(location_id);
CREATE INDEX idx_inventory_expiry_date ON inventory(expiry_date);
CREATE INDEX idx_suppliers_status ON suppliers(status);
CREATE INDEX idx_supplier_penalties_supplier_id ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_occurred_at ON supplier_penalties(occurred_at);
CREATE INDEX idx_purchase_orders_supplier_id ON purchase_orders(supplier_id);
CREATE INDEX idx_purchase_orders_status ON purchase_orders(status);
CREATE INDEX idx_purchase_order_lines_purchase_order_id ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_purchase_order_lines_product_id ON purchase_order_lines(product_id);
CREATE INDEX idx_inbound_receipts_purchase_order_id ON inbound_receipts(purchase_order_id);
CREATE INDEX idx_inbound_receipts_status ON inbound_receipts(status);
CREATE INDEX idx_inbound_receipt_lines_inbound_receipt_id ON inbound_receipt_lines(inbound_receipt_id);
CREATE INDEX idx_inbound_receipt_lines_product_id ON inbound_receipt_lines(product_id);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date);
CREATE INDEX idx_cycle_counts_location_id ON cycle_counts(location_id);
CREATE INDEX idx_cycle_counts_status ON cycle_counts(status);
CREATE INDEX idx_inventory_adjustments_product_id ON inventory_adjustments(product_id);
CREATE INDEX idx_inventory_adjustments_location_id ON inventory_adjustments(location_id);
CREATE INDEX idx_inventory_adjustments_approval_status ON inventory_adjustments(approval_status);
CREATE INDEX idx_inventory_adjustments_created_at ON inventory_adjustments(created_at);

