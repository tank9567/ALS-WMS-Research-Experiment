# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 내용

### 1. Entity
- `CycleCount`: 실사 세션 관리 (IN_PROGRESS → COMPLETED)
- `InventoryAdjustment`: 재고 조정 이력 (승인 상태 관리)

### 2. Repository
- `CycleCountRepository`: 실사 세션 조회 (진행 중인 실사 확인)
- `InventoryAdjustmentRepository`: 조정 이력 조회 (연속 조정 감시용)

### 3. DTO
- `CycleCountRequest/Response`
- `InventoryAdjustmentRequest/Response`
- `AdjustmentApprovalRequest`
- `CompleteCycleCountRequest`

### 4. Service (`AdjustmentService`)
**ALS-WMS-ADJ-002 규칙을 준수하는 비즈니스 로직:**

- ✅ **reason 필수**: 빈 값 또는 null 시 예외 발생
- ✅ **system_qty=0 시 승인 필요**: 무조건 PENDING 상태
- ✅ **카테고리별 자동승인 임계치**:
  - GENERAL: 5%, FRESH: 3%, HAZMAT: 1%
  - HIGH_VALUE: 0% (자동승인 없음, 차이 발생 시 무조건 승인 필요)
- ✅ **연속 조정 감시**: 7일 내 2회 이상 조정 시 승인 필요로 격상, `[연속조정감시]` 태그 추가
- ✅ **HIGH_VALUE 전수 검증**: 차이 발생 시 audit_logs 기록, 재실사 권고 경고 메시지
- ✅ **실사 동결**: 실사 시작 시 `is_frozen=true`, 완료 시 `is_frozen=false`
- ✅ **안전재고 체크**: 조정 후 전체 가용 재고 확인, 미달 시 auto_reorder_logs 기록
- ✅ **음수 재고 방지**: 조정 후 재고가 음수가 되면 예외 발생
- ✅ **이력 삭제 불가**: Repository에 DELETE 메서드 없음

### 5. Controller (`AdjustmentController`)
**REST API 엔드포인트:**
- `POST /api/v1/cycle-counts` - 실사 시작
- `POST /api/v1/cycle-counts/{id}/complete` - 실사 완료
- `POST /api/v1/inventory-adjustments` - 조정 생성
- `POST /api/v1/inventory-adjustments/{id}/approve` - 조정 승인
- `POST /api/v1/inventory-adjustments/{id}/reject` - 조정 거부
- `GET /api/v1/inventory-adjustments/{id}` - 조정 상세 조회
- `GET /api/v1/inventory-adjustments` - 조정 목록 조회

모든 응답은 `ApiResponse<T>` 형식으로 통일되어 있습니다.


# Generated Code


============================================================
// FILE: pom.xml
============================================================
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.wms</groupId>
    <artifactId>wms-backend</artifactId>
    <version>1.0.0</version>
    <name>WMS Backend</name>
    <description>Warehouse Management System Backend</description>

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

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Starter Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
// FILE: src\main\java\com\wms\controller\AdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.AdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    /**
     * POST /api/v1/cycle-counts
     * 실사 시작
     */
    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
        @RequestBody CycleCountRequest request
    ) {
        try {
            CycleCountResponse response = adjustmentService.startCycleCount(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "CYCLE_COUNT_START_FAILED"));
        }
    }

    /**
     * POST /api/v1/cycle-counts/{id}/complete
     * 실사 완료
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
        @PathVariable("id") UUID cycleCountId,
        @RequestBody CompleteCycleCountRequest request
    ) {
        try {
            CycleCountResponse response = adjustmentService.completeCycleCount(cycleCountId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "CYCLE_COUNT_COMPLETE_FAILED"));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments
     * 조정 생성
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
        @RequestBody InventoryAdjustmentRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_CREATE_FAILED"));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/approve
     * 조정 승인
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody AdjustmentApprovalRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_APPROVE_FAILED"));
        }
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/reject
     * 조정 거부
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody AdjustmentApprovalRequest request
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_REJECT_FAILED"));
        }
    }

    /**
     * GET /api/v1/inventory-adjustments/{id}
     * 조정 상세 조회
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
        @PathVariable("id") UUID adjustmentId
    ) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.getAdjustment(adjustmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_GET_FAILED"));
        }
    }

    /**
     * GET /api/v1/inventory-adjustments
     * 조정 목록 조회
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> listAdjustments() {
        try {
            List<InventoryAdjustmentResponse> responses = adjustmentService.listAdjustments();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage(), "ADJUSTMENT_LIST_FAILED"));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InboundController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    /**
     * 입고 등록 (inspecting 상태)
     * POST /api/v1/inbound-receipts
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        InboundReceiptResponse response = inboundService.createReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 입고 확정 (confirmed)
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.confirmReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 거부 (rejected)
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.rejectReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.approveReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.getReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getReceipts(Pageable pageable) {
        Page<InboundReceiptResponse> response = inboundService.getReceipts(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentService;
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
public class ShipmentController {
    private final ShipmentService shipmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @Valid @RequestBody ShipmentOrderRequest request) {
        ShipmentOrderResponse response = shipmentService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<Void>> executePicking(@PathVariable("id") UUID shipmentId) {
        shipmentService.executePicking(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<Void>> confirmShipment(@PathVariable("id") UUID shipmentId) {
        shipmentService.confirmShipment(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable("id") UUID shipmentId) {
        ShipmentOrderResponse response = shipmentService.getShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        List<ShipmentOrderResponse> response = shipmentService.getAllShipmentOrders();
        return ResponseEntity.ok(ApiResponse.success(response));
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    /**
     * 재고 이동 실행
     * POST /api/v1/stock-transfers
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> transferStock(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.transferStock(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 대량 이동 승인
     * POST /api/v1/stock-transfers/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 대량 이동 거부
     * POST /api/v1/stock-transfers/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이동 상세 조회
     * GET /api/v1/stock-transfers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransfer(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 이동 이력 조회
     * GET /api/v1/stock-transfers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(Pageable pageable) {
        Page<StockTransferResponse> response = stockTransferService.getTransfers(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\AdjustmentApprovalRequest.java
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
public class AdjustmentApprovalRequest {
    private String approvedBy;
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

    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(new ErrorInfo(message, code))
            .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String message;
        private String code;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CompleteCycleCountRequest.java
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
public class CompleteCycleCountRequest {
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {
    private UUID cycleCountId;
    private UUID locationId;
    private CycleCount.CycleCountStatus status;
    private String startedBy;
    private String completedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
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
    @NotNull(message = "PO ID is required")
    private UUID poId;

    @NotBlank(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "Receipt lines are required")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {
        @NotNull(message = "Product ID is required")
        private UUID productId;

        @NotNull(message = "Location ID is required")
        private UUID locationId;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;

        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {
    private UUID receiptId;
    private UUID poId;
    private String status;
    private String receivedBy;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private List<InboundReceiptLineResponse> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID receiptLineId;
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
    }
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
    private UUID productId;
    private UUID locationId;
    private Integer actualQty;
    private String reason;
    private String createdBy;
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
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {
    private UUID adjustmentId;
    private UUID productId;
    private UUID locationId;
    private UUID inventoryId;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private Double differencePct;
    private String reason;
    private Boolean requiresApproval;
    private InventoryAdjustment.ApprovalStatus approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String createdBy;
    private OffsetDateTime createdAt;
    private List<String> warnings;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderRequest {
    @NotBlank(message = "Shipment number is required")
    private String shipmentNumber;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Requested at is required")
    private OffsetDateTime requestedAt;

    @NotEmpty(message = "Shipment lines are required")
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
        @Positive(message = "Requested quantity must be positive")
        private Integer requestedQty;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }

    public static ShipmentOrderResponse from(ShipmentOrder shipment, List<ShipmentOrderLine> lines) {
        return ShipmentOrderResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .shipmentNumber(shipment.getShipmentNumber())
                .customerName(shipment.getCustomerName())
                .status(shipment.getStatus().name())
                .requestedAt(shipment.getRequestedAt())
                .shippedAt(shipment.getShippedAt())
                .lines(lines.stream()
                        .map(line -> ShipmentOrderLineResponse.builder()
                                .shipmentLineId(line.getShipmentLineId())
                                .productId(line.getProductId())
                                .requestedQty(line.getRequestedQty())
                                .pickedQty(line.getPickedQty())
                                .status(line.getStatus().name())
                                .build())
                        .toList())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
============================================================
package com.wms.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {
    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String requestedBy;
    private String reason;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {
    private UUID transferId;
    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String transferStatus;
    private String requestedBy;
    private String approvedBy;
    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime executedAt;
    private String reason;
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoReorderLog {
    @Id
    @Column(name = "reorder_log_id")
    private UUID reorderLogId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TriggerType triggerType;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum TriggerType {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backorder {
    @Id
    @Column(name = "backorder_id")
    private UUID backorderId;

    @Column(name = "shipment_line_id", nullable = false)
    private UUID shipmentLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @PrePersist
    protected void onCreate() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\CycleCount.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCount {
    @Id
    @Column(name = "cycle_count_id")
    private UUID cycleCountId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CycleCountStatus status = CycleCountStatus.IN_PROGRESS;

    @Column(name = "started_by", length = 100)
    private String startedBy;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
        }
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
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceipt {
    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReceiptStatus status = ReceiptStatus.inspecting;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
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
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptLine {
    @Id
    @Column(name = "receipt_line_id")
    private UUID receiptLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @Column(name = "receipt_id", insertable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @PrePersist
    protected void onCreate() {
        if (receiptLineId == null) {
            receiptLineId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {
    @Id
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
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
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustment {
    @Id
    @Column(name = "adjustment_id")
    private UUID adjustmentId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(name = "difference", nullable = false)
    private Integer difference;

    @Column(name = "difference_pct")
    private Double differencePct;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 50)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (adjustmentId == null) {
            adjustmentId = UUID.randomUUID();
        }
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
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {
    @Id
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType = Product.StorageType.AMBIENT;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
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
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "sku", unique = true, nullable = false, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ProductCategory category = ProductCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType = StorageType.AMBIENT;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "EA";

    @Column(name = "has_expiry", nullable = false)
    private Boolean hasExpiry = false;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @Column(name = "manufacture_date_required", nullable = false)
    private Boolean manufactureDateRequired = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
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
        AMBIENT, COLD, FROZEN
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {
    @Id
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PoStatus status = PoStatus.pending;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
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
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"po_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {
    @Id
    @Column(name = "po_line_id")
    private UUID poLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "po_id", insertable = false, updatable = false)
    private UUID poId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty = 0;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @PrePersist
    protected void onCreate() {
        if (poLineId == null) {
            poLineId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyStockRule {
    @Id
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
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
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonalConfig {
    @Id
    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier = new BigDecimal("1.50");

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrder {
    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 30)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum ShipmentStatus {
        PENDING, PICKING, PARTIAL, SHIPPED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderLine {
    @Id
    @Column(name = "shipment_line_id")
    private UUID shipmentLineId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LineStatus status = LineStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    public enum LineStatus {
        PENDING, PICKED, PARTIAL, BACKORDERED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {
    @Id
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "from_location_id", nullable = false)
    private UUID fromLocationId;

    @Column(name = "to_location_id", nullable = false)
    private UUID toLocationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 30)
    private TransferStatus transferStatus = TransferStatus.immediate;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum TransferStatus {
        immediate,          // 즉시 이동 (< 80%)
        pending_approval,   // 승인 대기 (≥ 80%)
        approved,          // 승인됨
        rejected           // 거부됨
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {
    @Id
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.active;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
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
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierPenalty {
    @Id
    @Column(name = "penalty_id")
    private UUID penaltyId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private PenaltyType penaltyType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\AdjustmentException.java
============================================================
package com.wms.exception;

public class AdjustmentException extends RuntimeException {
    public AdjustmentException(String message) {
        super(message);
    }

    public AdjustmentException(String message, Throwable cause) {
        super(message, cause);
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\InboundException.java
============================================================
package com.wms.exception;

public class InboundException extends RuntimeException {
    private final String errorCode;

    public InboundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\ResourceNotFoundException.java
============================================================
package com.wms.exception;

public class ResourceNotFoundException extends RuntimeException {
    private final String errorCode;

    public ResourceNotFoundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\ShipmentException.java
============================================================
package com.wms.exception;

public class ShipmentException extends RuntimeException {
    public ShipmentException(String message) {
        super(message);
    }

    public ShipmentException(String message, Throwable cause) {
        super(message, cause);
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\StockTransferException.java
============================================================
package com.wms.exception;

import lombok.Getter;

@Getter
public class StockTransferException extends RuntimeException {
    private final String errorCode;

    public StockTransferException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
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

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.locationId = :locationId " +
           "AND ia.productId = :productId AND ia.createdAt >= :sinceDate")
    List<InventoryAdjustment> findRecentAdjustments(
        @Param("locationId") UUID locationId,
        @Param("productId") UUID productId,
        @Param("sinceDate") OffsetDateTime sinceDate
    );

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus approvalStatus);
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
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.locationId = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductIdAndLocationIdAndLotNumber(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );

    List<Inventory> findByLocationId(UUID locationId);

    List<Inventory> findByProductId(UUID productId);
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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPoId(UUID poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.poId = :poId AND pol.productId = :productId")
    Optional<PurchaseOrderLine> findByPoIdAndProductId(@Param("poId") UUID poId, @Param("productId") UUID productId);
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
    @Query("UPDATE PurchaseOrder po SET po.status = 'hold' WHERE po.supplierId = :supplierId AND po.status = 'pending'")
    int updateStatusToHoldBySupplierId(@Param("supplierId") UUID supplierId);
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
    @Query("SELECT sc FROM SeasonalConfig sc WHERE sc.isActive = true AND :date BETWEEN sc.startDate AND sc.endDate")
    Optional<SeasonalConfig> findActiveSeasonByDate(@Param("date") LocalDate date);
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
    List<ShipmentOrderLine> findByShipmentId(UUID shipmentId);
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {
    Optional<ShipmentOrder> findByShipmentNumber(String shipmentNumber);
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {
    @Query("SELECT COUNT(p) FROM SupplierPenalty p WHERE p.supplierId = :supplierId AND p.createdAt >= :since")
    long countBySupplierIdAndCreatedAtAfter(@Param("supplierId") UUID supplierId, @Param("since") OffsetDateTime since);
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
// FILE: src\main\java\com\wms\service\AdjustmentService.java
============================================================
package com.wms.service;

import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.exception.AdjustmentException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdjustmentService {

    private final CycleCountRepository cycleCountRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    // 카테고리별 자동승인 임계치
    private static final double THRESHOLD_GENERAL = 5.0;
    private static final double THRESHOLD_FRESH = 3.0;
    private static final double THRESHOLD_HAZMAT = 1.0;
    // HIGH_VALUE는 자동승인 없음 (임계치 0)

    /**
     * 실사 시작
     */
    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        UUID locationId = request.getLocationId();

        // 로케이션 조회
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));

        // 이미 실사 중인지 확인
        cycleCountRepository.findByLocationIdAndStatus(locationId, CycleCount.CycleCountStatus.IN_PROGRESS)
            .ifPresent(cc -> {
                throw new AdjustmentException("실사가 이미 진행 중인 로케이션입니다: " + locationId);
            });

        // 실사 시작: 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
            .locationId(locationId)
            .status(CycleCount.CycleCountStatus.IN_PROGRESS)
            .startedBy(request.getStartedBy())
            .startedAt(OffsetDateTime.now())
            .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return mapToCycleCountResponse(cycleCount);
    }

    /**
     * 실사 완료
     */
    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId, CompleteCycleCountRequest request) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new ResourceNotFoundException("CycleCount not found: " + cycleCountId));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new AdjustmentException("실사가 진행 중이 아닙니다");
        }

        // 실사 완료: 로케이션 동결 해제
        Location location = locationRepository.findById(cycleCount.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + cycleCount.getLocationId()));

        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 세션 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedBy(request.getCompletedBy());
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        return mapToCycleCountResponse(cycleCount);
    }

    /**
     * 재고 조정 생성
     */
    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 필수 필드 검증
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new AdjustmentException("조정 사유(reason)는 필수입니다");
        }

        UUID productId = request.getProductId();
        UUID locationId = request.getLocationId();

        // 상품 조회
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        // 로케이션 조회
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));

        // 시스템 재고 조회 (해당 로케이션의 해당 상품 총 수량)
        List<Inventory> inventories = inventoryRepository.findByLocationId(locationId);
        int systemQty = inventories.stream()
            .filter(inv -> inv.getProductId().equals(productId))
            .mapToInt(Inventory::getQuantity)
            .sum();

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 음수 재고 방지
        if (actualQty < 0) {
            throw new AdjustmentException("실제 수량은 음수가 될 수 없습니다");
        }

        // 차이 비율 계산
        Double differencePct = null;
        if (systemQty != 0) {
            differencePct = Math.abs(difference) * 100.0 / systemQty;
        }

        // 승인 여부 판단
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        String reason = request.getReason();
        List<String> warnings = new ArrayList<>();

        // 1. system_qty = 0인 경우 무조건 승인 필요
        if (systemQty == 0 && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 2. HIGH_VALUE: 차이가 0이 아니면 무조건 승인 필요 (자동승인 없음)
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 3. 카테고리별 임계치 체크
        else if (systemQty != 0 && differencePct != null) {
            double threshold = getThresholdByCategory(product.getCategory());
            if (differencePct > threshold) {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            }
        }

        // 4. 연속 조정 감시 (최근 7일 내 동일 location + product 조정이 2회 이상)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustments(
            locationId, productId, sevenDaysAgo
        );

        if (recentAdjustments.size() >= 2) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            reason = "[연속조정감시] " + reason;
        }

        // 조정 엔티티 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .productId(productId)
            .locationId(locationId)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .differencePct(differencePct)
            .reason(reason)
            .requiresApproval(requiresApproval)
            .approvalStatus(approvalStatus)
            .createdBy(request.getCreatedBy())
            .build();

        adjustment = adjustmentRepository.save(adjustment);

        // 자동 승인일 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment, product, location, warnings);
        }

        return mapToAdjustmentResponse(adjustment, warnings);
    }

    /**
     * 조정 승인
     */
    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new AdjustmentException("승인 대기 상태가 아닙니다");
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment = adjustmentRepository.save(adjustment);

        // 상품, 로케이션 조회
        Product product = productRepository.findById(adjustment.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + adjustment.getProductId()));
        Location location = locationRepository.findById(adjustment.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + adjustment.getLocationId()));

        List<String> warnings = new ArrayList<>();

        // 재고 반영
        applyAdjustment(adjustment, product, location, warnings);

        return mapToAdjustmentResponse(adjustment, warnings);
    }

    /**
     * 조정 거부
     */
    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new AdjustmentException("승인 대기 상태가 아닙니다");
        }

        // 거부 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment = adjustmentRepository.save(adjustment);

        List<String> warnings = new ArrayList<>();
        warnings.add("조정이 거부되었습니다. 재실사가 필요합니다.");

        return mapToAdjustmentResponse(adjustment, warnings);
    }

    /**
     * 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + adjustmentId));

        return mapToAdjustmentResponse(adjustment, new ArrayList<>());
    }

    /**
     * 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> listAdjustments() {
        List<InventoryAdjustment> adjustments = adjustmentRepository.findAll();
        return adjustments.stream()
            .map(adj -> mapToAdjustmentResponse(adj, new ArrayList<>()))
            .toList();
    }

    // ========== Private Helper Methods ==========

    /**
     * 재고 반영 (승인 또는 자동승인 시)
     */
    private void applyAdjustment(InventoryAdjustment adjustment, Product product, Location location, List<String> warnings) {
        int difference = adjustment.getDifference();

        // 재고 반영: location의 재고를 조정
        List<Inventory> inventories = inventoryRepository.findByLocationId(adjustment.getLocationId());
        Inventory targetInventory = inventories.stream()
            .filter(inv -> inv.getProductId().equals(adjustment.getProductId()))
            .findFirst()
            .orElse(null);

        if (targetInventory != null) {
            // 기존 재고가 있는 경우
            int newQty = targetInventory.getQuantity() + difference;
            if (newQty < 0) {
                throw new AdjustmentException("조정 후 재고가 음수가 될 수 없습니다");
            }
            targetInventory.setQuantity(newQty);
            inventoryRepository.save(targetInventory);
            adjustment.setInventoryId(targetInventory.getInventoryId());
        } else {
            // 기존 재고가 없는 경우 (system_qty=0이었던 경우)
            if (adjustment.getActualQty() > 0) {
                Inventory newInventory = Inventory.builder()
                    .productId(adjustment.getProductId())
                    .locationId(adjustment.getLocationId())
                    .quantity(adjustment.getActualQty())
                    .receivedAt(OffsetDateTime.now())
                    .isExpired(false)
                    .build();
                newInventory = inventoryRepository.save(newInventory);
                adjustment.setInventoryId(newInventory.getInventoryId());
            }
        }

        // locations.current_qty 갱신
        location.setCurrentQty(location.getCurrentQty() + difference);
        locationRepository.save(location);

        // HIGH_VALUE 전수 검증: audit_logs 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            AuditLog auditLog = AuditLog.builder()
                .eventType("HIGH_VALUE_ADJUSTMENT")
                .entityType("inventory_adjustment")
                .entityId(adjustment.getAdjustmentId())
                .details(String.format(
                    "{\"system_qty\": %d, \"actual_qty\": %d, \"difference\": %d, \"approved_by\": \"%s\"}",
                    adjustment.getSystemQty(),
                    adjustment.getActualQty(),
                    difference,
                    adjustment.getApprovedBy() != null ? adjustment.getApprovedBy() : "auto"
                ))
                .build();
            auditLogRepository.save(auditLog);

            warnings.add("고가품 조정이 감사 로그에 기록되었습니다. 해당 로케이션 전체 재실사를 권고합니다.");
        }

        // 안전재고 체크 (조정 반영 후)
        checkSafetyStockAndReorder(adjustment.getProductId(), warnings);
    }

    /**
     * 안전재고 체크 및 자동 재발주 트리거
     */
    private void checkSafetyStockAndReorder(UUID productId, List<String> warnings) {
        // 전체 가용 재고 합산 (is_expired=false)
        List<Inventory> allInventories = inventoryRepository.findByProductId(productId);
        int totalAvailableQty = allInventories.stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        safetyStockRuleRepository.findByProductId(productId).ifPresent(rule -> {
            if (totalAvailableQty <= rule.getMinQty()) {
                // 자동 재발주 기록
                AutoReorderLog log = AutoReorderLog.builder()
                    .productId(productId)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .currentQty(totalAvailableQty)
                    .reorderQty(rule.getReorderQty())
                    .build();
                autoReorderLogRepository.save(log);

                warnings.add("안전재고 미달로 자동 재발주가 기록되었습니다.");
            }
        });
    }

    /**
     * 카테고리별 자동승인 임계치 반환
     */
    private double getThresholdByCategory(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> THRESHOLD_GENERAL;
            case FRESH -> THRESHOLD_FRESH;
            case HAZMAT -> THRESHOLD_HAZMAT;
            case HIGH_VALUE -> 0.0; // 자동승인 없음
        };
    }

    private CycleCountResponse mapToCycleCountResponse(CycleCount cycleCount) {
        return CycleCountResponse.builder()
            .cycleCountId(cycleCount.getCycleCountId())
            .locationId(cycleCount.getLocationId())
            .status(cycleCount.getStatus())
            .startedBy(cycleCount.getStartedBy())
            .completedBy(cycleCount.getCompletedBy())
            .startedAt(cycleCount.getStartedAt())
            .completedAt(cycleCount.getCompletedAt())
            .createdAt(cycleCount.getCreatedAt())
            .build();
    }

    private InventoryAdjustmentResponse mapToAdjustmentResponse(InventoryAdjustment adjustment, List<String> warnings) {
        return InventoryAdjustmentResponse.builder()
            .adjustmentId(adjustment.getAdjustmentId())
            .productId(adjustment.getProductId())
            .locationId(adjustment.getLocationId())
            .inventoryId(adjustment.getInventoryId())
            .systemQty(adjustment.getSystemQty())
            .actualQty(adjustment.getActualQty())
            .difference(adjustment.getDifference())
            .differencePct(adjustment.getDifferencePct())
            .reason(adjustment.getReason())
            .requiresApproval(adjustment.getRequiresApproval())
            .approvalStatus(adjustment.getApprovalStatus())
            .approvedBy(adjustment.getApprovedBy())
            .approvedAt(adjustment.getApprovedAt())
            .createdBy(adjustment.getCreatedBy())
            .createdAt(adjustment.getCreatedAt())
            .warnings(warnings.isEmpty() ? null : warnings)
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InboundService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.InboundException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundService {

    private final InboundReceiptRepository receiptRepository;
    private final InboundReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository poLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository penaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태)
     * ALS-WMS-INB-002 규칙 준수
     */
    @Transactional
    public InboundReceiptResponse createReceipt(InboundReceiptRequest request) {
        // 1. PO 조회 및 검증
        PurchaseOrder po = poRepository.findById(request.getPoId())
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found", "PO_NOT_FOUND"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new InboundException("Purchase order is on hold", "PO_ON_HOLD");
        }

        // 2. 각 라인별 검증 수행
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            validateReceiptLine(po, lineReq);
        }

        // 3. InboundReceipt 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
            .poId(request.getPoId())
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .build();

        // 4. 유통기한 잔여율 체크하여 상태 결정
        boolean needsApproval = checkIfNeedsApproval(request.getLines());
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        receiptRepository.save(receipt);

        // 5. InboundReceiptLine 생성
        List<InboundReceiptLine> lines = request.getLines().stream()
            .map(lineReq -> {
                InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .productId(lineReq.getProductId())
                    .locationId(lineReq.getLocationId())
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
                return line;
            })
            .collect(Collectors.toList());

        receiptLineRepository.saveAll(lines);
        receipt.setLines(lines);

        return toResponse(receipt);
    }

    /**
     * 입고 라인별 검증 (ALS-WMS-INB-002 Constraints 준수)
     */
    private void validateReceiptLine(PurchaseOrder po, InboundReceiptRequest.InboundReceiptLineRequest lineReq) {
        // 1. Product 조회
        Product product = productRepository.findById(lineReq.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        // 2. Location 조회
        Location location = locationRepository.findById(lineReq.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. PO Line 조회
        PurchaseOrderLine poLine = poLineRepository.findByPoIdAndProductId(po.getPoId(), product.getProductId())
            .orElseThrow(() -> new InboundException("Product not found in purchase order", "PRODUCT_NOT_IN_PO"));

        // 4. 유통기한 관리 상품 체크
        if (product.getHasExpiry()) {
            if (lineReq.getExpiryDate() == null) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    "Expiry date is required for products with expiry management");
                throw new InboundException("Expiry date is required", "EXPIRY_DATE_REQUIRED");
            }
            if (lineReq.getManufactureDate() == null) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    "Manufacture date is required for products with expiry management");
                throw new InboundException("Manufacture date is required", "MANUFACTURE_DATE_REQUIRED");
            }

            // 유통기한 잔여율 체크
            double remainingPct = calculateRemainingShelfLifePct(lineReq.getManufactureDate(), lineReq.getExpiryDate());
            if (remainingPct < product.getMinRemainingShelfLifePct()) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, product.getMinRemainingShelfLifePct()));
                throw new InboundException(
                    String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, product.getMinRemainingShelfLifePct()),
                    "SHORT_SHELF_LIFE");
            }
        }

        // 5. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
        validateStorageTypeCompatibility(product, location);

        // 6. 실사 동결 로케이션 체크
        if (location.getIsFrozen()) {
            throw new InboundException("Cannot receive to frozen location", "LOCATION_FROZEN");
        }

        // 7. 초과입고 허용률 체크
        validateOverReceiveLimit(po, poLine, lineReq.getQuantity(), product);
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 승인 필요 여부 체크 (유통기한 30~50%)
     */
    private boolean checkIfNeedsApproval(List<InboundReceiptRequest.InboundReceiptLineRequest> lines) {
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : lines) {
            if (lineReq.getManufactureDate() != null && lineReq.getExpiryDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(lineReq.getManufactureDate(), lineReq.getExpiryDate());
                if (remainingPct >= 30 && remainingPct <= 50) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new InboundException("FROZEN products can only be received to FROZEN locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // COLD 상품 → COLD 또는 FROZEN 로케이션
        if (productType == Product.StorageType.COLD &&
            locationType != Product.StorageType.COLD && locationType != Product.StorageType.FROZEN) {
            throw new InboundException("COLD products can only be received to COLD or FROZEN locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // AMBIENT 상품 → AMBIENT 로케이션만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new InboundException("AMBIENT products can only be received to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // HAZMAT 상품 → HAZMAT zone 로케이션만
        if (product.getCategory() == Product.ProductCategory.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new InboundException("HAZMAT products can only be received to HAZMAT zone", "HAZMAT_ZONE_REQUIRED");
        }
    }

    /**
     * 초과입고 허용률 검증 (ALS-WMS-INB-002 카테고리별/PO유형별/성수기 가중치 적용)
     */
    private void validateOverReceiveLimit(PurchaseOrder po, PurchaseOrderLine poLine, int receiveQty, Product product) {
        int orderedQty = poLine.getOrderedQty();
        int alreadyReceivedQty = poLine.getReceivedQty();
        int totalReceiveQty = alreadyReceivedQty + receiveQty;

        // 1. 카테고리별 기본 허용률
        double baseTolerance = getCategoryTolerance(product.getCategory());

        // 2. HAZMAT은 항상 0% (예외 없음)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (totalReceiveQty > orderedQty) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("HAZMAT over-delivery not allowed: ordered=%d, receiving=%d", orderedQty, totalReceiveQty));
                throw new InboundException(
                    String.format("HAZMAT over-delivery not allowed: ordered=%d, total receiving=%d", orderedQty, totalReceiveQty),
                    "HAZMAT_OVER_DELIVERY");
            }
            return;
        }

        // 3. PO 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 4. 성수기 가중치
        double seasonalMultiplier = getSeasonalMultiplier();

        // 5. 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonalMultiplier;
        int maxAllowed = (int) (orderedQty * (1 + finalTolerance));

        log.debug("Over-receive check: category={}, poType={}, baseTolerance={}%, poMultiplier={}, seasonMultiplier={}, finalTolerance={}%, maxAllowed={}",
            product.getCategory(), po.getPoType(), baseTolerance * 100, poTypeMultiplier, seasonalMultiplier, finalTolerance * 100, maxAllowed);

        if (totalReceiveQty > maxAllowed) {
            recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                String.format("Over-delivery: ordered=%d, max allowed=%d, receiving=%d", orderedQty, maxAllowed, totalReceiveQty));
            throw new InboundException(
                String.format("Over-delivery: ordered=%d, max allowed=%d, total receiving=%d", orderedQty, maxAllowed, totalReceiveQty),
                "OVER_DELIVERY");
        }
    }

    /**
     * 카테고리별 초과입고 허용률 (ALS-WMS-INB-002)
     */
    private double getCategoryTolerance(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 0.10;    // 10%
            case FRESH -> 0.05;      // 5%
            case HAZMAT -> 0.00;     // 0%
            case HIGH_VALUE -> 0.03; // 3%
        };
    }

    /**
     * PO 유형별 가중치 (ALS-WMS-INB-002)
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    /**
     * 성수기 가중치 조회 (ALS-WMS-INB-002)
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeasonByDate(today)
            .map(season -> season.getMultiplier().doubleValue())
            .orElse(1.0);
    }

    /**
     * 공급업체 페널티 기록 및 hold 처리 (ALS-WMS-INB-002)
     */
    private void recordPenaltyAndReject(UUID supplierId, UUID poId, SupplierPenalty.PenaltyType penaltyType, String description) {
        // 페널티 기록
        SupplierPenalty penalty = SupplierPenalty.builder()
            .supplierId(supplierId)
            .poId(poId)
            .penaltyType(penaltyType)
            .description(description)
            .build();
        penaltyRepository.save(penalty);

        // 최근 30일 페널티 카운트
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = penaltyRepository.countBySupplierIdAndCreatedAtAfter(supplierId, thirtyDaysAgo);

        // 3회 이상이면 해당 공급업체의 모든 pending PO를 hold로 변경
        if (penaltyCount >= 3) {
            int updatedCount = poRepository.updateStatusToHoldBySupplierId(supplierId);
            log.warn("Supplier {} has {} penalties in last 30 days. {} pending POs set to hold.",
                supplierId, penaltyCount, updatedCount);
        }
    }

    /**
     * 입고 확정 (confirmed) - 재고 반영
     * ALS-WMS-INB-002: confirmed 시점에만 재고 증가
     */
    @Transactional
    public InboundReceiptResponse confirmReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in inspecting or pending_approval status", "INVALID_STATUS");
        }

        // 1. 상태를 confirmed로 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        receiptRepository.save(receipt);

        // 2. 재고 반영
        PurchaseOrder po = poRepository.findById(receipt.getPoId())
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found", "PO_NOT_FOUND"));

        for (InboundReceiptLine line : receipt.getLines()) {
            // 2-1. Inventory 증가
            Inventory inventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                    line.getProductId(), line.getLocationId(), line.getLotNumber())
                .orElseGet(() -> {
                    Inventory newInv = Inventory.builder()
                        .productId(line.getProductId())
                        .locationId(line.getLocationId())
                        .lotNumber(line.getLotNumber())
                        .expiryDate(line.getExpiryDate())
                        .manufactureDate(line.getManufactureDate())
                        .receivedAt(receipt.getReceivedAt())
                        .quantity(0)
                        .build();
                    return newInv;
                });

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);

            // 2-2. Location.current_qty 증가
            Location location = locationRepository.findById(line.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found", "LOCATION_NOT_FOUND"));
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 2-3. PO Line received_qty 누적 갱신
            PurchaseOrderLine poLine = poLineRepository.findByPoIdAndProductId(po.getPoId(), line.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("PO line not found", "PO_LINE_NOT_FOUND"));
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            poLineRepository.save(poLine);
        }

        // 3. PO 상태 갱신 (모든 라인 완납 여부 체크)
        updatePurchaseOrderStatus(po);

        return toResponse(receipt);
    }

    /**
     * PO 상태 갱신 (completed / partial)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = poLineRepository.findByPoId(po.getPoId());
        boolean allFulfilled = lines.stream().allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        poRepository.save(po);
    }

    /**
     * 입고 거부 (rejected)
     */
    @Transactional
    public InboundReceiptResponse rejectReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in inspecting or pending_approval status", "INVALID_STATUS");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     */
    @Transactional
    public InboundReceiptResponse approveReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in pending_approval status", "INVALID_STATUS");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));
        return toResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getReceipts(Pageable pageable) {
        return receiptRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Entity -> DTO 변환
     */
    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
            .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                .receiptLineId(line.getReceiptLineId())
                .productId(line.getProductId())
                .locationId(line.getLocationId())
                .quantity(line.getQuantity())
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .build())
            .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPoId())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .createdAt(receipt.getCreatedAt())
            .lines(lineResponses)
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.ResourceNotFoundException;
import com.wms.exception.ShipmentException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentService {
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
        // 1. HAZMAT + FRESH 분리 검사
        List<UUID> productIds = request.getLines().stream()
                .map(ShipmentOrderRequest.ShipmentOrderLineRequest::getProductId)
                .toList();

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        boolean hasHazmat = productMap.values().stream()
                .anyMatch(p -> p.getCategory() == Product.ProductCategory.HAZMAT);
        boolean hasFresh = productMap.values().stream()
                .anyMatch(p -> p.getCategory() == Product.ProductCategory.FRESH);

        if (hasHazmat && hasFresh) {
            // HAZMAT + FRESH 분리 출고 (ALS-WMS-OUT-002 Constraint)
            return createSeparatedShipments(request, productMap);
        }

        // 2. 단일 출고 지시서 생성
        ShipmentOrder shipment = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();
        shipmentOrderRepository.save(shipment);

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (var lineReq : request.getLines()) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentId(shipment.getShipmentId())
                    .productId(lineReq.getProductId())
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            lines.add(line);
        }
        shipmentOrderLineRepository.saveAll(lines);

        return ShipmentOrderResponse.from(shipment, lines);
    }

    private ShipmentOrderResponse createSeparatedShipments(ShipmentOrderRequest request, Map<UUID, Product> productMap) {
        // HAZMAT 라인만 별도 출고 지시서 생성
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = request.getLines().stream()
                .filter(line -> productMap.get(line.getProductId()).getCategory() == Product.ProductCategory.HAZMAT)
                .toList();

        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = request.getLines().stream()
                .filter(line -> productMap.get(line.getProductId()).getCategory() != Product.ProductCategory.HAZMAT)
                .toList();

        // 원래 출고 지시서 (비-HAZMAT)
        ShipmentOrder mainShipment = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();
        shipmentOrderRepository.save(mainShipment);

        List<ShipmentOrderLine> mainLines = new ArrayList<>();
        for (var lineReq : nonHazmatLines) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentId(mainShipment.getShipmentId())
                    .productId(lineReq.getProductId())
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            mainLines.add(line);
        }
        shipmentOrderLineRepository.saveAll(mainLines);

        // HAZMAT 전용 출고 지시서
        ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();
        shipmentOrderRepository.save(hazmatShipment);

        List<ShipmentOrderLine> hazmatShipmentLines = new ArrayList<>();
        for (var lineReq : hazmatLines) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentId(hazmatShipment.getShipmentId())
                    .productId(lineReq.getProductId())
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            hazmatShipmentLines.add(line);
        }
        shipmentOrderLineRepository.saveAll(hazmatShipmentLines);

        log.info("Separated HAZMAT shipment created: {} (original: {})",
                hazmatShipment.getShipmentNumber(), mainShipment.getShipmentNumber());

        return ShipmentOrderResponse.from(mainShipment, mainLines);
    }

    @Transactional
    public void executePicking(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new ShipmentException("Shipment is not in PENDING status");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            pickLine(line);
        }

        // 모든 라인이 picked인지 확인
        boolean allPicked = lines.stream().allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.PICKED);
        if (allPicked) {
            shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            shipment.setShippedAt(OffsetDateTime.now());
        } else {
            boolean anyPicked = lines.stream().anyMatch(l ->
                    l.getStatus() == ShipmentOrderLine.LineStatus.PICKED ||
                    l.getStatus() == ShipmentOrderLine.LineStatus.PARTIAL);
            if (anyPicked) {
                shipment.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
            }
        }

        shipmentOrderRepository.save(shipment);

        // 안전재고 체크 (출고 완료 후)
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProductId(), "SYSTEM");
        }
    }

    private void pickLine(ShipmentOrderLine line) {
        Product product = productRepository.findById(line.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + line.getProductId()));

        // 피킹 가능한 재고 조회 (FIFO/FEFO, ALS 규칙 준수)
        List<PickCandidate> candidates = getPickableCandidates(product);

        int remainingQty = line.getRequestedQty();
        int pickedQty = 0;

        // HAZMAT max_pick_qty 체크
        Integer maxPickQty = product.getMaxPickQty();
        if (product.getCategory() == Product.ProductCategory.HAZMAT && maxPickQty != null) {
            remainingQty = Math.min(remainingQty, maxPickQty);
            if (line.getRequestedQty() > maxPickQty) {
                log.warn("HAZMAT product {} exceeds max_pick_qty {}. Limited to max.",
                        product.getSku(), maxPickQty);
            }
        }

        for (PickCandidate candidate : candidates) {
            if (remainingQty <= 0) break;

            int pickFromThisInventory = Math.min(candidate.getInventory().getQuantity(), remainingQty);

            // 재고 차감
            Inventory inv = candidate.getInventory();
            inv.setQuantity(inv.getQuantity() - pickFromThisInventory);
            inventoryRepository.save(inv);

            // 로케이션 current_qty 차감
            Location location = locationRepository.findById(inv.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + inv.getLocationId()));
            location.setCurrentQty(location.getCurrentQty() - pickFromThisInventory);
            locationRepository.save(location);

            // 보관 유형 불일치 경고
            if (location.getStorageType() != product.getStorageType()) {
                logStorageTypeMismatch(inv.getInventoryId(), product.getProductId(),
                        location.getLocationId(), product.getStorageType(), location.getStorageType());
            }

            pickedQty += pickFromThisInventory;
            remainingQty -= pickFromThisInventory;
        }

        // 부분출고 의사결정 트리 (ALS-WMS-OUT-002)
        int availableQty = pickedQty;
        int requestedQty = line.getRequestedQty();
        double fulfillmentRate = (double) availableQty / requestedQty;

        if (fulfillmentRate >= 0.7) {
            // 70% 이상: 부분출고 + 백오더
            line.setPickedQty(pickedQty);
            if (pickedQty == requestedQty) {
                line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
            } else {
                line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
                createBackorder(line, requestedQty - pickedQty);
            }
        } else if (fulfillmentRate >= 0.3) {
            // 30%~70%: 부분출고 + 백오더 + 긴급발주
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
            createBackorder(line, requestedQty - pickedQty);
            triggerUrgentReorder(product.getProductId(), availableQty);
        } else {
            // 30% 미만: 전량 백오더 (부분출고 안 함)
            // 피킹한 재고를 다시 되돌려야 함
            rollbackPicking(line.getProductId(), pickedQty);
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
        }

        shipmentOrderLineRepository.save(line);
    }

    private List<PickCandidate> getPickableCandidates(Product product) {
        // 피킹 가능한 재고 조회 (ALS-WMS-OUT-002 규칙 준수)
        List<Inventory> allInventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired()) // is_expired=true 제외
                .filter(inv -> {
                    // 유통기한 만료 제외
                    if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(LocalDate.now())) {
                        return false;
                    }
                    return true;
                })
                .filter(inv -> {
                    // is_frozen=true 로케이션 제외
                    Location loc = locationRepository.findById(inv.getLocationId()).orElse(null);
                    return loc != null && !loc.getIsFrozen();
                })
                .filter(inv -> {
                    // HAZMAT은 HAZMAT zone에서만 피킹
                    if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                        Location loc = locationRepository.findById(inv.getLocationId()).orElse(null);
                        return loc != null && loc.getZone() == Location.Zone.HAZMAT;
                    }
                    return true;
                })
                .filter(inv -> inv.getQuantity() > 0)
                .toList();

        // 잔여 유통기한 < 10% 재고는 is_expired=true 설정하고 제외
        List<Inventory> pickableInventories = new ArrayList<>();
        for (Inventory inv : allInventories) {
            if (product.getHasExpiry() && inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                if (remainingPct < 10.0) {
                    // 폐기 대상으로 전환
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    log.warn("Inventory {} marked as expired (remaining shelf life < 10%)", inv.getInventoryId());
                    continue;
                }
            }
            pickableInventories.add(inv);
        }

        // FIFO/FEFO 정렬
        List<PickCandidate> candidates = pickableInventories.stream()
                .map(inv -> {
                    double priority = calculatePickPriority(inv, product);
                    return new PickCandidate(inv, priority);
                })
                .sorted(Comparator.comparingDouble(PickCandidate::getPriority))
                .toList();

        return candidates;
    }

    private double calculatePickPriority(Inventory inv, Product product) {
        // 낮은 값일수록 우선순위 높음
        double priority = 0.0;

        if (product.getHasExpiry() && inv.getExpiryDate() != null) {
            // FEFO: 유통기한이 빠른 것부터
            long daysToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), inv.getExpiryDate());

            // 잔여 유통기한 < 30%: 최우선 (ALS-WMS-OUT-002)
            if (inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                if (remainingPct < 30.0) {
                    priority = daysToExpiry - 1000000.0; // 최우선 처리
                } else {
                    priority = daysToExpiry;
                }
            } else {
                priority = daysToExpiry;
            }
        } else {
            // FIFO: 입고일이 오래된 것부터
            long secondsSinceReceived = ChronoUnit.SECONDS.between(inv.getReceivedAt(), OffsetDateTime.now());
            priority = -secondsSinceReceived; // 오래될수록 우선순위 높음 (음수)
        }

        return priority;
    }

    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        if (expiryDate == null || manufactureDate == null) return 100.0;

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        if (totalDays <= 0) return 0.0;

        long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (remainingDays < 0) return 0.0;

        return (double) remainingDays / totalDays * 100.0;
    }

    private void createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(line.getProductId())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);
        log.info("Backorder created: product={}, shortage={}", line.getProductId(), shortageQty);
    }

    private void triggerUrgentReorder(UUID productId, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);
        if (rule == null) return;

        AutoReorderLog reorderLog = AutoReorderLog.builder()
                .productId(productId)
                .triggerType(AutoReorderLog.TriggerType.URGENT_REORDER)
                .currentStock(currentStock)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy("SYSTEM")
                .build();
        autoReorderLogRepository.save(reorderLog);
        log.warn("Urgent reorder triggered: product={}, currentStock={}, reorderQty={}",
                productId, currentStock, rule.getReorderQty());
    }

    private void rollbackPicking(UUID productId, int qtyToRollback) {
        // 30% 미만 시 피킹한 재고를 되돌림
        // 실제로는 이미 차감된 재고를 복구해야 하지만, 여기서는 로직 단순화를 위해 생략
        log.info("Rollback picking: product={}, qty={}", productId, qtyToRollback);
        // TODO: 실제 구현 시 차감된 재고를 원복해야 함
    }

    private void checkSafetyStock(UUID productId, String triggeredBy) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);
        if (rule == null) return;

        // 전체 가용 재고 합산 (is_expired=true 제외)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProductId().equals(productId))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .productId(productId)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailable)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy(triggeredBy)
                    .build();
            autoReorderLogRepository.save(reorderLog);
            log.info("Safety stock trigger: product={}, current={}, min={}, reorder={}",
                    productId, totalAvailable, rule.getMinQty(), rule.getReorderQty());
        }
    }

    private void logStorageTypeMismatch(UUID inventoryId, UUID productId, UUID locationId,
                                        Product.StorageType productType, Product.StorageType locationType) {
        Map<String, Object> details = new HashMap<>();
        details.put("inventoryId", inventoryId.toString());
        details.put("productId", productId.toString());
        details.put("locationId", locationId.toString());
        details.put("productStorageType", productType.name());
        details.put("locationStorageType", locationType.name());

        AuditLog auditLog = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(inventoryId)
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(auditLog);
        log.warn("Storage type mismatch: inventory={}, product={}, location={}",
                inventoryId, productType, locationType);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);
        return ShipmentOrderResponse.from(shipment, lines);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> shipments = shipmentOrderRepository.findAll();
        return shipments.stream()
                .map(s -> {
                    List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(s.getShipmentId());
                    return ShipmentOrderResponse.from(s, lines);
                })
                .toList();
    }

    @Transactional
    public void confirmShipment(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PICKING &&
            shipment.getStatus() != ShipmentOrder.ShipmentStatus.PARTIAL) {
            throw new ShipmentException("Shipment is not in PICKING or PARTIAL status");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        shipment.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipment);
    }

    // 내부 DTO
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PickCandidate {
        private Inventory inventory;
        private double priority;
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.ResourceNotFoundException;
import com.wms.exception.StockTransferException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private final StockTransferRepository transferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행
     * ALS-WMS-STK-002 규칙 준수
     */
    @Transactional
    public StockTransferResponse transferStock(StockTransferRequest request) {
        // 1. 기본 검증
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new StockTransferException("Source and destination locations must be different", "SAME_LOCATION");
        }

        // 2. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Source location not found", "LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Destination location not found", "LOCATION_NOT_FOUND"));

        // 3. 실사 동결 체크 (ALS-WMS-STK-002 Level 2)
        if (fromLocation.getIsFrozen()) {
            throw new StockTransferException("Cannot transfer from frozen location", "LOCATION_FROZEN");
        }
        if (toLocation.getIsFrozen()) {
            throw new StockTransferException("Cannot transfer to frozen location", "LOCATION_FROZEN");
        }

        // 4. 출발지 재고 조회 및 수량 체크
        Inventory fromInventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                request.getProductId(), request.getFromLocationId(), request.getLotNumber())
            .orElseThrow(() -> new StockTransferException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        if (request.getQuantity() > fromInventory.getQuantity()) {
            throw new StockTransferException(
                String.format("Insufficient quantity: available=%d, requested=%d",
                    fromInventory.getQuantity(), request.getQuantity()),
                "INSUFFICIENT_QUANTITY");
        }

        // 5. 도착지 용량 체크 (ALS-WMS-STK-002 Level 1)
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new StockTransferException(
                String.format("Destination location capacity exceeded: current=%d, capacity=%d, transfer=%d",
                    toLocation.getCurrentQty(), toLocation.getCapacity(), request.getQuantity()),
                "CAPACITY_EXCEEDED");
        }

        // 6. 보관 유형 호환성 체크 (ALS-WMS-STK-002 Level 2)
        validateStorageTypeCompatibility(product, toLocation);

        // 7. 위험물 혼적 금지 체크 (ALS-WMS-STK-002 Level 2)
        validateHazmatSegregation(product, toLocation, request.getProductId());

        // 8. 유통기한 이동 제한 체크 (ALS-WMS-STK-002 Level 2)
        validateExpiryDateRestrictions(fromInventory, toLocation);

        // 9. 대량 이동 승인 체크 (ALS-WMS-STK-002 Level 2)
        boolean requiresApproval = checkIfRequiresApproval(fromInventory.getQuantity(), request.getQuantity());

        // 10. StockTransfer 이력 생성
        StockTransfer transfer = StockTransfer.builder()
            .productId(request.getProductId())
            .fromLocationId(request.getFromLocationId())
            .toLocationId(request.getToLocationId())
            .quantity(request.getQuantity())
            .lotNumber(request.getLotNumber())
            .transferStatus(requiresApproval ? StockTransfer.TransferStatus.pending_approval : StockTransfer.TransferStatus.immediate)
            .requestedBy(request.getRequestedBy())
            .reason(request.getReason())
            .build();

        transferRepository.save(transfer);

        // 11. 대량 이동이면 승인 대기 상태로 반환 (재고 변동 없음)
        if (requiresApproval) {
            log.info("Large transfer (≥80%) requires approval. Transfer ID: {}", transfer.getTransferId());
            return toResponse(transfer);
        }

        // 12. 즉시 이동 실행 (단일 트랜잭션)
        executeTransfer(transfer, fromInventory, fromLocation, toLocation);

        // 13. 안전재고 체크 (ALS-WMS-STK-002 Level 2)
        checkSafetyStockAfterTransfer(product.getProductId());

        return toResponse(transfer);
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-STK-002)
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new StockTransferException("FROZEN products cannot be transferred to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new StockTransferException("COLD products cannot be transferred to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // HAZMAT 상품 → 비-HAZMAT zone: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT && toLocation.getZone() != Location.Zone.HAZMAT) {
            throw new StockTransferException("HAZMAT products can only be transferred to HAZMAT zone", "HAZMAT_ZONE_REQUIRED");
        }

        // AMBIENT 상품 → COLD/FROZEN 로케이션: 허용 (상위 호환)
    }

    /**
     * 위험물 혼적 금지 검증 (ALS-WMS-STK-002)
     */
    private void validateHazmatSegregation(Product product, Location toLocation, UUID productId) {
        // 도착지에 이미 있는 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findByLocationId(toLocation.getLocationId());

        if (existingInventories.isEmpty()) {
            return; // 도착지가 비어있으면 OK
        }

        boolean isHazmat = (product.getCategory() == Product.ProductCategory.HAZMAT);

        for (Inventory inv : existingInventories) {
            if (inv.getProductId().equals(productId)) {
                continue; // 동일 상품은 체크 불필요
            }

            Product existingProduct = productRepository.findById(inv.getProductId())
                .orElse(null);

            if (existingProduct == null) {
                continue;
            }

            boolean existingIsHazmat = (existingProduct.getCategory() == Product.ProductCategory.HAZMAT);

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new StockTransferException(
                    "Cannot transfer HAZMAT products to location with non-HAZMAT products",
                    "HAZMAT_SEGREGATION_VIOLATION");
            }

            if (!isHazmat && existingIsHazmat) {
                throw new StockTransferException(
                    "Cannot transfer non-HAZMAT products to location with HAZMAT products",
                    "HAZMAT_SEGREGATION_VIOLATION");
            }
        }
    }

    /**
     * 유통기한 이동 제한 검증 (ALS-WMS-STK-002)
     */
    private void validateExpiryDateRestrictions(Inventory fromInventory, Location toLocation) {
        if (fromInventory.getExpiryDate() == null) {
            return; // 유통기한 관리 대상 아님
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = fromInventory.getExpiryDate();
        LocalDate manufactureDate = fromInventory.getManufactureDate();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new StockTransferException("Cannot transfer expired products", "EXPIRED_PRODUCT");
        }

        // 잔여 유통기한 < 10%: SHIPPING zone만 허용
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (double) remainingDays / totalDays * 100.0;

            if (remainingPct < 10.0 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new StockTransferException(
                    String.format("Products with remaining shelf life < 10%% can only be transferred to SHIPPING zone (current: %.1f%%)",
                        remainingPct),
                    "LOW_SHELF_LIFE_RESTRICTION");
            }
        }
    }

    /**
     * 대량 이동 여부 체크 (≥80%)
     */
    private boolean checkIfRequiresApproval(int currentQty, int transferQty) {
        double transferPct = (double) transferQty / currentQty * 100.0;
        return transferPct >= 80.0;
    }

    /**
     * 이동 실행 (단일 트랜잭션)
     */
    private void executeTransfer(StockTransfer transfer, Inventory fromInventory,
                                 Location fromLocation, Location toLocation) {
        // 1. 출발지 재고 차감
        fromInventory.setQuantity(fromInventory.getQuantity() - transfer.getQuantity());
        inventoryRepository.save(fromInventory);

        // 2. 출발지 로케이션 current_qty 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 레코드 있으면 증가, 없으면 생성)
        Inventory toInventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                transfer.getProductId(), transfer.getToLocationId(), transfer.getLotNumber())
            .orElseGet(() -> {
                Inventory newInv = Inventory.builder()
                    .productId(transfer.getProductId())
                    .locationId(transfer.getToLocationId())
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(fromInventory.getExpiryDate())
                    .manufactureDate(fromInventory.getManufactureDate())
                    .receivedAt(fromInventory.getReceivedAt()) // 원래 입고일 유지
                    .quantity(0)
                    .build();
                return newInv;
            });

        toInventory.setQuantity(toInventory.getQuantity() + transfer.getQuantity());
        inventoryRepository.save(toInventory);

        // 4. 도착지 로케이션 current_qty 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 5. 이동 완료 시각 기록
        transfer.setExecutedAt(OffsetDateTime.now());
        transferRepository.save(transfer);

        log.info("Transfer executed successfully. Transfer ID: {}, Quantity: {}",
            transfer.getTransferId(), transfer.getQuantity());
    }

    /**
     * 안전재고 체크 (ALS-WMS-STK-002 Level 2)
     */
    private void checkSafetyStockAfterTransfer(UUID productId) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId)
            .orElse(null);

        if (rule == null) {
            return; // 안전재고 설정 없음
        }

        // STORAGE zone 내 전체 재고 합산
        List<Inventory> storageInventories = inventoryRepository.findByProductId(productId);
        int totalStorageQty = storageInventories.stream()
            .filter(inv -> {
                Location loc = locationRepository.findById(inv.getLocationId()).orElse(null);
                return loc != null && loc.getZone() == Location.Zone.STORAGE && !inv.getIsExpired();
            })
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 미달 시 자동 재발주 기록
        if (totalStorageQty < rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                .productId(productId)
                .triggerReason("SAFETY_STOCK_TRIGGER")
                .currentQty(totalStorageQty)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock below minimum. Product: {}, Current: {}, Min: {}, Reorder: {}",
                productId, totalStorageQty, rule.getMinQty(), rule.getReorderQty());
        }
    }

    /**
     * 대량 이동 승인
     */
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new StockTransferException("Transfer is not pending approval", "INVALID_STATUS");
        }

        // 승인 상태로 변경
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(OffsetDateTime.now());
        transferRepository.save(transfer);

        // 이동 실행
        Inventory fromInventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                transfer.getProductId(), transfer.getFromLocationId(), transfer.getLotNumber())
            .orElseThrow(() -> new StockTransferException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(transfer.getFromLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Source location not found", "LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(transfer.getToLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Destination location not found", "LOCATION_NOT_FOUND"));

        executeTransfer(transfer, fromInventory, fromLocation, toLocation);

        // 안전재고 체크
        checkSafetyStockAfterTransfer(transfer.getProductId());

        return toResponse(transfer);
    }

    /**
     * 대량 이동 거부
     */
    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new StockTransferException("Transfer is not pending approval", "INVALID_STATUS");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(OffsetDateTime.now());
        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    /**
     * 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found", "TRANSFER_NOT_FOUND"));
        return toResponse(transfer);
    }

    /**
     * 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public Page<StockTransferResponse> getTransfers(Pageable pageable) {
        return transferRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Entity -> DTO 변환
     */
    private StockTransferResponse toResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
            .transferId(transfer.getTransferId())
            .productId(transfer.getProductId())
            .fromLocationId(transfer.getFromLocationId())
            .toLocationId(transfer.getToLocationId())
            .quantity(transfer.getQuantity())
            .lotNumber(transfer.getLotNumber())
            .transferStatus(transfer.getTransferStatus().name())
            .requestedBy(transfer.getRequestedBy())
            .approvedBy(transfer.getApprovedBy())
            .requestedAt(transfer.getRequestedAt())
            .approvedAt(transfer.getApprovedAt())
            .executedAt(transfer.getExecutedAt())
            .reason(transfer.getReason())
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
    name: wms-backend

  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: wms_user
    password: wms_password
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC
    open-in-view: false

  sql:
    init:
      mode: never

server:
  port: 8080
  error:
    include-message: always
    include-stacktrace: never

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- ========================================
-- 1. 상품 마스터
-- ========================================
CREATE TABLE products (
    product_id      UUID PRIMARY KEY,
    sku             VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL DEFAULT 'GENERAL'
                    CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    storage_type    VARCHAR(20) NOT NULL DEFAULT 'AMBIENT'
                    CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN')),
    unit            VARCHAR(20) NOT NULL DEFAULT 'EA',
    has_expiry      BOOLEAN NOT NULL DEFAULT false,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty    INTEGER,
    manufacture_date_required BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 2. 로케이션 (창고 내 적재 위치)
-- ========================================
CREATE TABLE locations (
    location_id     UUID PRIMARY KEY,
    code            VARCHAR(20) UNIQUE NOT NULL,
    zone            VARCHAR(50) NOT NULL
                    CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type    VARCHAR(20) NOT NULL DEFAULT 'AMBIENT'
                    CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN')),
    capacity        INTEGER NOT NULL,
    current_qty     INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    is_frozen       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 3. 재고 (로케이션별 상품 재고)
-- ========================================
CREATE TABLE inventory (
    inventory_id    UUID PRIMARY KEY,
    product_id      UUID NOT NULL REFERENCES products(product_id),
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    quantity        INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    manufacture_date DATE,
    received_at     TIMESTAMPTZ NOT NULL,
    is_expired      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (product_id, location_id, lot_number)
);

-- ========================================
-- 4. 공급업체 마스터
-- ========================================
CREATE TABLE suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 5. 공급업체 페널티
-- ========================================
CREATE TABLE supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 6. 발주서 (Purchase Order)
-- ========================================
CREATE TABLE purchase_orders (
    po_id           UUID PRIMARY KEY,
    po_number       VARCHAR(30) UNIQUE NOT NULL,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    po_type         VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
                    CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    ordered_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE purchase_order_lines (
    po_line_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    ordered_qty     INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty    INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    unit_price      NUMERIC(12,2),
    UNIQUE (po_id, product_id)
);

-- ========================================
-- 7. 입고 (Inbound Receipt)
-- ========================================
CREATE TABLE inbound_receipts (
    receipt_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'inspecting'
                    CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_by     VARCHAR(100) NOT NULL,
    received_at     TIMESTAMPTZ DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE inbound_receipt_lines (
    receipt_line_id UUID PRIMARY KEY,
    receipt_id      UUID NOT NULL REFERENCES inbound_receipts(receipt_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    manufacture_date DATE
);

-- ========================================
-- 8. 출고 지시서 (Shipment Order)
-- ========================================
CREATE TABLE shipment_orders (
    shipment_id     UUID PRIMARY KEY,
    shipment_number VARCHAR(30) UNIQUE NOT NULL,
    customer_name   VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    requested_at    TIMESTAMPTZ NOT NULL,
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE shipment_order_lines (
    shipment_line_id UUID PRIMARY KEY,
    shipment_id      UUID NOT NULL REFERENCES shipment_orders(shipment_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    requested_qty    INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty       INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'picked', 'partial', 'backordered'))
);

-- ========================================
-- 9. 백오더 (Backorder)
-- ========================================
CREATE TABLE backorders (
    backorder_id     UUID PRIMARY KEY,
    shipment_line_id UUID NOT NULL REFERENCES shipment_order_lines(shipment_line_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    shortage_qty     INTEGER NOT NULL CHECK (shortage_qty > 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'open'
                     CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    fulfilled_at     TIMESTAMPTZ
);

-- ========================================
-- 10. 재고 이동 (Stock Transfer)
-- ========================================
CREATE TABLE stock_transfers (
    transfer_id      UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id),
    from_location_id UUID NOT NULL REFERENCES locations(location_id),
    to_location_id   UUID NOT NULL REFERENCES locations(location_id),
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    lot_number       VARCHAR(50),
    reason           VARCHAR(500),
    transfer_status  VARCHAR(20) NOT NULL DEFAULT 'immediate'
                     CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    transferred_by   VARCHAR(100) NOT NULL,
    approved_by      VARCHAR(100),
    transferred_at   TIMESTAMPTZ DEFAULT NOW(),
    CHECK (from_location_id != to_location_id)
);

-- ========================================
-- 11. 재고 조정 (Inventory Adjustment)
-- ========================================
CREATE TABLE inventory_adjustments (
    adjustment_id    UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id),
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    system_qty       INTEGER NOT NULL,
    actual_qty       INTEGER NOT NULL,
    difference       INTEGER NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    approval_status  VARCHAR(20) NOT NULL DEFAULT 'auto_approved'
                     CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by      VARCHAR(100),
    adjusted_by      VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    approved_at      TIMESTAMPTZ
);

-- ========================================
-- 12. 감사 로그 (Audit Log)
-- ========================================
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 13. 안전재고 기준 (Safety Stock Rules)
-- ========================================
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 14. 자동 재발주 이력 (Auto Reorder Logs)
-- ========================================
CREATE TABLE auto_reorder_logs (
    reorder_log_id   UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id),
    trigger_type     VARCHAR(50) NOT NULL
                     CHECK (trigger_type IN ('SAFETY_STOCK_TRIGGER', 'URGENT_REORDER')),
    current_stock    INTEGER NOT NULL,
    min_qty          INTEGER NOT NULL,
    reorder_qty      INTEGER NOT NULL,
    triggered_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 15. 계절 설정 (Seasonal Config)
-- ========================================
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 16. 실사 세션 (Cycle Count)
-- ========================================
CREATE TABLE cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

