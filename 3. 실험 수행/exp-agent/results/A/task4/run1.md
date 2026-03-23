# CLI Output

재고 실사 및 조정 기능 구현을 완료했습니다. 

구현된 내용:

**1. DB 스키마** (schema.sql:219-254)
- `cycle_counts` 테이블: 실사 세션 관리
- `inventory_adjustments` 테이블: 재고 조정 이력

**2. Entity 클래스**
- `CycleCount`: 실사 엔티티
- `InventoryAdjustment`: 재고 조정 엔티티

**3. Repository**
- `CycleCountRepository`: 실사 데이터 접근
- `InventoryAdjustmentRepository`: 조정 데이터 접근 (최근 7일 조정 조회 포함)

**4. DTO**
- `CycleCountRequest`, `CycleCountCompleteRequest`, `CycleCountResponse`
- `InventoryAdjustmentRequest`, `InventoryAdjustmentResponse`

**5. Service** (InventoryAdjustmentService)
- 실사 시작: 로케이션 동결 처리
- 실사 완료: 차이 발생 시 자동 조정 생성, 동결 해제
- 재고 조정 생성: 카테고리별 자동승인 임계치 적용 (GENERAL 5%, FRESH 3%, HAZMAT 1%, HIGH_VALUE 2%)
- 연속 조정 감시: 7일 내 2회 이상 시 승인 격상
- HIGH_VALUE 전수 검증: 차이가 0이 아니면 무조건 승인 필요, 감사 로그 기록
- 조정 승인/거부
- 안전재고 체크 및 자동 재발주

**6. Controller** (InventoryAdjustmentController)
- POST /api/v1/cycle-counts (실사 시작)
- POST /api/v1/cycle-counts/{id}/complete (실사 완료)
- POST /api/v1/inventory-adjustments (조정 생성)
- POST /api/v1/inventory-adjustments/{id}/approve (승인)
- POST /api/v1/inventory-adjustments/{id}/reject (거부)
- GET /api/v1/inventory-adjustments/{id} (상세 조회)
- GET /api/v1/inventory-adjustments (목록 조회)

모든 비즈니스 규칙이 요구사항에 따라 구현되었습니다.


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

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts
     * 입고 등록 (검수 시작)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request
    ) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm
     * 입고 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject
     * 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam String reason
    ) {
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve
     * 유통기한 경고 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inbound-receipts/{id}
     * 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inbound-receipts
     * 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getInboundReceipts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InboundReceiptResponse> response = inboundReceiptService.getInboundReceipts(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InventoryAdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    /**
     * POST /api/v1/cycle-counts - 실사 시작
     */
    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @RequestBody CycleCountRequest request
    ) {
        CycleCountResponse response = inventoryAdjustmentService.startCycleCount(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/cycle-counts/{id}/complete - 실사 완료
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable UUID id,
            @RequestBody CycleCountCompleteRequest request
    ) {
        CycleCountResponse response = inventoryAdjustmentService.completeCycleCount(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inventory-adjustments - 재고 조정 생성
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createAdjustment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/approve - 재고 조정 승인
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestParam String approvedBy
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveAdjustment(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inventory-adjustments/{id}/reject - 재고 조정 거부
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestParam String approvedBy
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectAdjustment(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inventory-adjustments/{id} - 재고 조정 상세 조회
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable UUID id
    ) {
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getAdjustment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inventory-adjustments - 재고 조정 목록 조회
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<Page<InventoryAdjustmentResponse>>> getAdjustments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InventoryAdjustmentResponse> response = inventoryAdjustmentService.getAdjustments(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    /**
     * POST /api/v1/shipment-orders
     * 출고 지시서 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request
    ) {
        ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/shipment-orders/{id}/pick
     * 피킹 실행
     */
    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipment(
            @PathVariable UUID id
    ) {
        ShipmentOrderResponse response = shipmentOrderService.pickShipment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/shipment-orders/{id}/ship
     * 출고 확정
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipOrder(
            @PathVariable UUID id
    ) {
        ShipmentOrderResponse response = shipmentOrderService.shipOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/shipment-orders/{id}
     * 출고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable UUID id
    ) {
        ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/shipment-orders
     * 출고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ShipmentOrderResponse>>> getShipmentOrders(
            Pageable pageable
    ) {
        Page<ShipmentOrderResponse> response = shipmentOrderService.getShipmentOrders(pageable);
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
     * POST /api/v1/stock-transfers
     * 재고 이동 실행
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @RequestBody StockTransferRequest request
    ) {
        StockTransferResponse response = stockTransferService.executeTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/stock-transfers/{id}/approve
     * 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy
    ) {
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/stock-transfers/{id}/reject
     * 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy
    ) {
        StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/stock-transfers/{id}
     * 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
            @PathVariable UUID id
    ) {
        StockTransferResponse response = stockTransferService.getTransfer(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/stock-transfers
     * 이동 이력 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<StockTransferResponse> response = stockTransferService.getTransfers(pageable);
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
    private ErrorDetails error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .message(message)
                        .code(code)
                        .build())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetails {
        private String message;
        private String code;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountCompleteRequest.java
============================================================
package com.wms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountCompleteRequest {
    private Integer countedQty;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountRequest.java
============================================================
package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountRequest {
    private UUID locationId;
    private UUID productId;
    private String lotNumber;
    private LocalDate expiryDate;
    private String countedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.CycleCount.CycleCountStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {
    private UUID id;
    private UUID locationId;
    private String locationCode;
    private UUID productId;
    private String productSku;
    private String productName;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer systemQty;
    private Integer countedQty;
    private CycleCountStatus status;
    private String countedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
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
public class InboundReceiptRequest {

    private String receiptNumber;
    private UUID purchaseOrderId;
    private OffsetDateTime receivedDate;
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {
        private UUID purchaseOrderLineId;
        private UUID productId;
        private UUID locationId;
        private String lotNumber;
        private Integer receivedQty;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.InboundReceipt.ReceiptStatus;
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

    private UUID id;
    private String receiptNumber;
    private UUID purchaseOrderId;
    private String poNumber;
    private ReceiptStatus status;
    private OffsetDateTime receivedDate;
    private OffsetDateTime confirmedAt;
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
        private String lotNumber;
        private Integer receivedQty;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRequest.java
============================================================
package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentRequest {
    private UUID productId;
    private UUID locationId;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer actualQty;
    private String reason;
    private String createdBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
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
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer systemQty;
    private Integer actualQty;
    private Integer differenceQty;
    private String reason;
    private ApprovalStatus approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

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

    private String shipmentNumber;
    private String customerName;
    private OffsetDateTime orderDate;
    private List<ShipmentOrderLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineRequest {
        private UUID productId;
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

    private UUID id;
    private String shipmentNumber;
    private String customerName;
    private ShipmentOrder.ShipmentStatus status;
    private OffsetDateTime orderDate;
    private OffsetDateTime shippedAt;
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
        private Integer requestedQty;
        private Integer pickedQty;
        private ShipmentOrderLine.LineStatus status;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
============================================================
package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
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
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer transferQty;
    private String reason;
    private String requestedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.StockTransfer;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
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
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer transferQty;
    private StockTransfer.TransferStatus transferStatus;
    private String reason;
    private String requestedBy;
    private String approvedBy;
    private OffsetDateTime transferredAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private TriggerReason triggerReason;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "triggered_at", nullable = false, updatable = false)
    private OffsetDateTime triggeredAt;

    public enum TriggerReason {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_line_id", nullable = false)
    private ShipmentOrderLine shipmentOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "backordered_qty", nullable = false)
    private Integer backorderedQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "counted_qty")
    private Integer countedQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleCountStatus status = CycleCountStatus.IN_PROGRESS;

    @Column(name = "counted_by", length = 255)
    private String countedBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 100)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceiptStatus status = ReceiptStatus.INSPECTING;

    @Column(name = "received_date", nullable = false)
    private OffsetDateTime receivedDate;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ReceiptStatus {
        INSPECTING, PENDING_APPROVAL, CONFIRMED, REJECTED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines", indexes = {
    @Index(name = "idx_inbound_lines_receipt", columnList = "inbound_receipt_id"),
    @Index(name = "idx_inbound_lines_po_line", columnList = "purchase_order_line_id")
})
@Getter
@Setter
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

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number", "expiry_date"})
})
@Getter
@Setter
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
    private Integer quantity;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\InventoryAdjustment.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
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

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(name = "difference_qty", nullable = false)
    private Integer differenceQty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty = 0;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
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

    @Column(name = "manages_expiry", nullable = false)
    private Boolean managesExpiry = false;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "po_number", nullable = false, unique = true, length = 100)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoStatus status = PoStatus.PENDING;

    @Column(name = "order_date", nullable = false)
    private OffsetDateTime orderDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        PENDING, PARTIAL, COMPLETED, CANCELLED, HOLD
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", indexes = {
    @Index(name = "idx_po_lines_po", columnList = "purchase_order_id"),
    @Index(name = "idx_po_lines_product", columnList = "product_id")
})
@Getter
@Setter
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

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config", indexes = {
    @Index(name = "idx_seasonal_dates", columnList = "start_date, end_date")
})
@Getter
@Setter
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
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 100)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "order_date", nullable = false)
    private OffsetDateTime orderDate;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status = LineStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
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

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "transfer_qty", nullable = false)
    private Integer transferQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 30)
    private TransferStatus transferStatus = TransferStatus.IMMEDIATE;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "transferred_at", nullable = false)
    private OffsetDateTime transferredAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum TransferStatus {
        IMMEDIATE, PENDING_APPROVAL, APPROVED, REJECTED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum SupplierStatus {
        ACTIVE, HOLD, INACTIVE
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties", indexes = {
    @Index(name = "idx_supplier_penalties_date", columnList = "supplier_id, penalized_at")
})
@Getter
@Setter
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

    @CreationTimestamp
    @Column(name = "penalized_at", nullable = false, updatable = false)
    private OffsetDateTime penalizedAt;

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

    private final String errorCode;

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
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
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + e.getMessage(), "INTERNAL_ERROR"));
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
import com.wms.entity.CycleCount.CycleCountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    List<CycleCount> findByLocationIdAndStatus(UUID locationId, CycleCountStatus status);

    @Query("SELECT cc FROM CycleCount cc WHERE cc.location.id = :locationId AND cc.status = :status")
    List<CycleCount> findInProgressByLocation(@Param("locationId") UUID locationId, @Param("status") CycleCountStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {

    List<InboundReceiptLine> findByInboundReceiptId(UUID inboundReceiptId);
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

    boolean existsByReceiptNumber(String receiptNumber);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryAdjustmentRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia " +
            "WHERE ia.product.id = :productId " +
            "AND ia.location.id = :locationId " +
            "AND ia.createdAt >= :startDate " +
            "AND (ia.approvalStatus = 'AUTO_APPROVED' OR ia.approvalStatus = 'APPROVED')")
    List<InventoryAdjustment> findRecentAdjustments(
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("startDate") OffsetDateTime startDate
    );

    List<InventoryAdjustment> findByApprovalStatus(ApprovalStatus approvalStatus);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
        @Param("expiryDate") LocalDate expiryDate
    );

    /**
     * 특정 로케이션의 모든 재고 조회
     */
    @Query("SELECT i FROM Inventory i WHERE i.location.id = :locationId")
    List<Inventory> findByLocationId(@Param("locationId") UUID locationId);

    /**
     * 특정 상품의 특정 zone 내 재고 조회
     */
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.zone = :zone")
    List<Inventory> findByProductIdAndZone(@Param("productId") UUID productId, @Param("zone") Location.Zone zone);

    /**
     * 특정 상품의 모든 재고 조회
     */
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    List<Inventory> findByProductId(@Param("productId") UUID productId);
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

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    List<PurchaseOrderLine> findByPurchaseOrderId(UUID purchaseOrderId);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);
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
    boolean existsByShipmentNumber(String shipmentNumber);
}


============================================================
// FILE: src\main\java\com\wms\repository\StockTransferRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    /**
     * 특정 상품의 재고 이동 이력 조회
     */
    Page<StockTransfer> findByProductId(UUID productId, Pageable pageable);

    /**
     * 특정 출발지 로케이션의 재고 이동 이력 조회
     */
    Page<StockTransfer> findByFromLocationId(UUID fromLocationId, Pageable pageable);

    /**
     * 특정 도착지 로케이션의 재고 이동 이력 조회
     */
    Page<StockTransfer> findByToLocationId(UUID toLocationId, Pageable pageable);

    /**
     * 특정 상태의 재고 이동 조회
     */
    Page<StockTransfer> findByTransferStatus(StockTransfer.TransferStatus transferStatus, Pageable pageable);

    /**
     * 특정 상품 + 출발지 로케이션 + 로트 + 유통기한에 해당하는 이동 이력 조회
     */
    @Query("SELECT st FROM StockTransfer st WHERE st.product.id = :productId " +
            "AND st.fromLocation.id = :fromLocationId " +
            "AND (:lotNumber IS NULL OR st.lotNumber = :lotNumber) " +
            "AND (:expiryDate IS NULL OR st.expiryDate = :expiryDate)")
    Page<StockTransfer> findByProductAndFromLocationAndLotAndExpiry(
            @Param("productId") UUID productId,
            @Param("fromLocationId") UUID fromLocationId,
            @Param("lotNumber") String lotNumber,
            @Param("expiryDate") java.time.LocalDate expiryDate,
            Pageable pageable
    );
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.id = :supplierId " +
           "AND CAST(sp.penalizedAt AS date) >= :since")
    long countBySupplierId(@Param("supplierId") UUID supplierId, @Param("since") LocalDate since);
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

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.entity.InboundReceipt.ReceiptStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.PurchaseOrder.PoStatus;
import com.wms.entity.PurchaseOrder.PoType;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (검수 시작)
     */
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. 입고번호 중복 체크
        if (inboundReceiptRepository.existsByReceiptNumber(request.getReceiptNumber())) {
            throw new BusinessException("Receipt number already exists", "DUPLICATE_RECEIPT_NUMBER");
        }

        // 2. PO 조회 및 검증
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new BusinessException("Purchase order not found", "PO_NOT_FOUND"));

        if (purchaseOrder.getStatus() == PoStatus.HOLD) {
            throw new BusinessException("Purchase order is on hold", "PO_ON_HOLD");
        }

        if (purchaseOrder.getStatus() == PoStatus.CANCELLED) {
            throw new BusinessException("Purchase order is cancelled", "PO_CANCELLED");
        }

        if (purchaseOrder.getStatus() == PoStatus.COMPLETED) {
            throw new BusinessException("Purchase order is already completed", "PO_COMPLETED");
        }

        // 3. PO Lines 조회
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrder.getId());

        // 4. InboundReceipt 생성
        InboundReceipt inboundReceipt = InboundReceipt.builder()
                .receiptNumber(request.getReceiptNumber())
                .purchaseOrder(purchaseOrder)
                .status(ReceiptStatus.INSPECTING)
                .receivedDate(request.getReceivedDate() != null ? request.getReceivedDate() : OffsetDateTime.now())
                .build();

        inboundReceipt = inboundReceiptRepository.save(inboundReceipt);

        // 5. 각 라인 검증 및 생성
        List<InboundReceiptLine> receiptLines = new ArrayList<>();
        boolean needsApproval = false;
        String rejectionReason = null;

        for (InboundReceiptRequest.InboundReceiptLineRequest lineRequest : request.getLines()) {
            // PO Line 조회
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineRequest.getPurchaseOrderLineId())
                    .orElseThrow(() -> new BusinessException("Purchase order line not found", "PO_LINE_NOT_FOUND"));

            if (!poLine.getPurchaseOrder().getId().equals(purchaseOrder.getId())) {
                throw new BusinessException("Purchase order line does not belong to the specified PO", "PO_LINE_MISMATCH");
            }

            // Product 조회
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            if (!product.getId().equals(poLine.getProduct().getId())) {
                throw new BusinessException("Product does not match PO line", "PRODUCT_MISMATCH");
            }

            // Location 조회
            Location location = locationRepository.findById(lineRequest.getLocationId())
                    .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

            // (a) 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("Location is frozen for cycle count", "LOCATION_FROZEN");
            }

            // (b) 초과입고 허용률 체크
            int receivedQty = lineRequest.getReceivedQty();
            int orderedQty = poLine.getOrderedQty();
            int alreadyReceivedQty = poLine.getReceivedQty();
            int totalReceivedQty = alreadyReceivedQty + receivedQty;

            double allowedOverReceiptPct = calculateAllowedOverReceiptPercentage(
                    product.getCategory(),
                    purchaseOrder.getPoType(),
                    LocalDate.now()
            );

            int maxAllowedQty = (int) Math.floor(orderedQty * (1.0 + allowedOverReceiptPct / 100.0));

            if (totalReceivedQty > maxAllowedQty) {
                // 초과입고 거부
                rejectionReason = String.format(
                        "Over-delivery: received %d, ordered %d, max allowed %d (%.2f%%)",
                        totalReceivedQty, orderedQty, maxAllowedQty, allowedOverReceiptPct
                );

                // 공급업체 페널티 기록
                recordSupplierPenalty(purchaseOrder.getSupplier(), "OVER_DELIVERY", rejectionReason);

                inboundReceipt.setStatus(ReceiptStatus.REJECTED);
                inboundReceipt.setRejectionReason(rejectionReason);
                inboundReceiptRepository.save(inboundReceipt);

                throw new BusinessException(rejectionReason, "OVER_DELIVERY");
            }

            // (c) 유통기한 관리 체크
            if (product.getManagesExpiry()) {
                if (lineRequest.getExpiryDate() == null) {
                    throw new BusinessException("Expiry date is required for expiry-managed product", "EXPIRY_DATE_REQUIRED");
                }

                if (lineRequest.getManufactureDate() == null) {
                    throw new BusinessException("Manufacture date is required for expiry-managed product", "MANUFACTURE_DATE_REQUIRED");
                }

                // 유통기한 잔여율 체크
                LocalDate today = LocalDate.now();
                LocalDate manufactureDate = lineRequest.getManufactureDate();
                LocalDate expiryDate = lineRequest.getExpiryDate();

                long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
                long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

                if (totalShelfLife <= 0) {
                    throw new BusinessException("Invalid shelf life: expiry date must be after manufacture date", "INVALID_SHELF_LIFE");
                }

                double remainingPct = (remainingShelfLife * 100.0) / totalShelfLife;
                int minRemainingPct = product.getMinRemainingShelfLifePct() != null ?
                        product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minRemainingPct) {
                    // 유통기한 부족 거부
                    rejectionReason = String.format(
                            "Insufficient shelf life: %.1f%% remaining (min required: %d%%)",
                            remainingPct, minRemainingPct
                    );

                    // 공급업체 페널티 기록
                    recordSupplierPenalty(purchaseOrder.getSupplier(), "SHORT_SHELF_LIFE", rejectionReason);

                    inboundReceipt.setStatus(ReceiptStatus.REJECTED);
                    inboundReceipt.setRejectionReason(rejectionReason);
                    inboundReceiptRepository.save(inboundReceipt);

                    throw new BusinessException(rejectionReason, "SHORT_SHELF_LIFE");
                } else if (remainingPct >= minRemainingPct && remainingPct < 50) {
                    // 30~50%: 승인 필요
                    needsApproval = true;
                }
            }

            // (d) 보관 유형 호환성 체크
            if (!isStorageTypeCompatible(product.getStorageType(), location.getStorageType(), location.getZone())) {
                throw new BusinessException(
                        String.format("Storage type incompatible: product %s, location %s",
                                product.getStorageType(), location.getStorageType()),
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }

            // (e) 로케이션 용량 체크
            int newQty = location.getCurrentQty() + receivedQty;
            if (newQty > location.getCapacity()) {
                throw new BusinessException(
                        String.format("Location capacity exceeded: current %d + received %d > capacity %d",
                                location.getCurrentQty(), receivedQty, location.getCapacity()),
                        "LOCATION_CAPACITY_EXCEEDED"
                );
            }

            // InboundReceiptLine 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                    .inboundReceipt(inboundReceipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .lotNumber(lineRequest.getLotNumber())
                    .receivedQty(receivedQty)
                    .manufactureDate(lineRequest.getManufactureDate())
                    .expiryDate(lineRequest.getExpiryDate())
                    .build();

            receiptLines.add(receiptLine);
        }

        // 승인 필요 여부에 따라 상태 변경
        if (needsApproval) {
            inboundReceipt.setStatus(ReceiptStatus.PENDING_APPROVAL);
        }

        inboundReceiptLineRepository.saveAll(receiptLines);
        inboundReceiptRepository.save(inboundReceipt);

        return buildResponse(inboundReceipt, receiptLines);
    }

    /**
     * 입고 확정
     */
    public InboundReceiptResponse confirmInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() != ReceiptStatus.INSPECTING) {
            throw new BusinessException(
                    "Can only confirm receipts in INSPECTING status",
                    "INVALID_STATUS"
            );
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        // 재고 반영 및 PO 업데이트
        processConfirmation(inboundReceipt, lines);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 거부
     */
    public InboundReceiptResponse rejectInboundReceipt(UUID id, String reason) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() == ReceiptStatus.CONFIRMED) {
            throw new BusinessException("Cannot reject confirmed receipt", "ALREADY_CONFIRMED");
        }

        if (inboundReceipt.getStatus() == ReceiptStatus.REJECTED) {
            throw new BusinessException("Receipt is already rejected", "ALREADY_REJECTED");
        }

        inboundReceipt.setStatus(ReceiptStatus.REJECTED);
        inboundReceipt.setRejectionReason(reason);
        inboundReceiptRepository.save(inboundReceipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 유통기한 경고 승인
     */
    public InboundReceiptResponse approveInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() != ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only approve receipts in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        // 재고 반영 및 PO 업데이트
        processConfirmation(inboundReceipt, lines);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getInboundReceipts(Pageable pageable) {
        Page<InboundReceipt> receipts = inboundReceiptRepository.findAll(pageable);

        return receipts.map(receipt -> {
            List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(receipt.getId());
            return buildResponse(receipt, lines);
        });
    }

    // ===== Private Helper Methods =====

    /**
     * 초과입고 허용률 계산
     */
    private double calculateAllowedOverReceiptPercentage(
            ProductCategory category,
            PoType poType,
            LocalDate date
    ) {
        // HAZMAT은 무조건 0%
        if (category == ProductCategory.HAZMAT) {
            return 0.0;
        }

        // 카테고리별 기본 허용률
        double basePct = switch (category) {
            case GENERAL -> 10.0;
            case FRESH -> 5.0;
            case HIGH_VALUE -> 3.0;
            default -> 10.0;
        };

        // 발주 유형별 가중치
        double poTypeMultiplier = switch (poType) {
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
            default -> 1.0;
        };

        // 성수기 가중치
        double seasonalMultiplier = seasonalConfigRepository.findActiveSeasonByDate(date)
                .map(config -> config.getMultiplier().doubleValue())
                .orElse(1.0);

        return basePct * poTypeMultiplier * seasonalMultiplier;
    }

    /**
     * 보관 유형 호환성 체크
     */
    private boolean isStorageTypeCompatible(
            StorageType productType,
            StorageType locationType,
            Location.Zone locationZone
    ) {
        // HAZMAT 상품은 HAZMAT zone만 허용
        if (productType == StorageType.HAZMAT) {
            return locationZone == Location.Zone.HAZMAT;
        }

        // FROZEN 상품은 FROZEN 로케이션만
        if (productType == StorageType.FROZEN) {
            return locationType == StorageType.FROZEN;
        }

        // COLD 상품은 COLD 또는 FROZEN 허용 (상위 호환)
        if (productType == StorageType.COLD) {
            return locationType == StorageType.COLD || locationType == StorageType.FROZEN;
        }

        // AMBIENT 상품은 AMBIENT만
        if (productType == StorageType.AMBIENT) {
            return locationType == StorageType.AMBIENT;
        }

        return false;
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(Supplier supplier, String penaltyType, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .reason(reason)
                .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 개수 확인
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 공급업체 상태를 HOLD로 변경
            supplier.setStatus("hold");
            supplierRepository.save(supplier);

            // 해당 공급업체의 모든 PENDING PO를 HOLD로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findAll().stream()
                    .filter(po -> po.getSupplier().getId().equals(supplier.getId()))
                    .filter(po -> po.getStatus() == PoStatus.PENDING)
                    .collect(Collectors.toList());

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PoStatus.HOLD);
            }

            purchaseOrderRepository.saveAll(pendingPOs);
        }
    }

    /**
     * 입고 확정 처리 (재고 반영 + PO 업데이트)
     */
    private void processConfirmation(InboundReceipt inboundReceipt, List<InboundReceiptLine> lines) {
        PurchaseOrder purchaseOrder = inboundReceipt.getPurchaseOrder();

        for (InboundReceiptLine line : lines) {
            // 1. Inventory 반영
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber(),
                    line.getExpiryDate()
            ).orElse(null);

            if (inventory == null) {
                inventory = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .lotNumber(line.getLotNumber())
                        .quantity(line.getReceivedQty())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(inboundReceipt.getReceivedDate())
                        .isExpired(false)
                        .build();
            } else {
                inventory.setQuantity(inventory.getQuantity() + line.getReceivedQty());
            }

            inventoryRepository.save(inventory);

            // 2. Location 적재량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getReceivedQty());
            locationRepository.save(location);

            // 3. PO Line 입고수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getReceivedQty());
            purchaseOrderLineRepository.save(poLine);
        }

        // 4. PO 상태 업데이트
        List<PurchaseOrderLine> allPoLines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrder.getId());
        boolean allCompleted = allPoLines.stream()
                .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = allPoLines.stream()
                .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            purchaseOrder.setStatus(PoStatus.COMPLETED);
        } else if (anyReceived) {
            purchaseOrder.setStatus(PoStatus.PARTIAL);
        }

        purchaseOrderRepository.save(purchaseOrder);

        // 5. InboundReceipt 상태 업데이트
        inboundReceipt.setStatus(ReceiptStatus.CONFIRMED);
        inboundReceipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(inboundReceipt);
    }

    /**
     * Response 빌드
     */
    private InboundReceiptResponse buildResponse(InboundReceipt inboundReceipt, List<InboundReceiptLine> lines) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .purchaseOrderLineId(line.getPurchaseOrderLine().getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .lotNumber(line.getLotNumber())
                        .receivedQty(line.getReceivedQty())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .id(inboundReceipt.getId())
                .receiptNumber(inboundReceipt.getReceiptNumber())
                .purchaseOrderId(inboundReceipt.getPurchaseOrder().getId())
                .poNumber(inboundReceipt.getPurchaseOrder().getPoNumber())
                .status(inboundReceipt.getStatus())
                .receivedDate(inboundReceipt.getReceivedDate())
                .confirmedAt(inboundReceipt.getConfirmedAt())
                .rejectionReason(inboundReceipt.getRejectionReason())
                .lines(lineResponses)
                .createdAt(inboundReceipt.getCreatedAt())
                .updatedAt(inboundReceipt.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InventoryAdjustmentService.java
============================================================
package com.wms.service;

import com.wms.dto.CycleCountCompleteRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.entity.CycleCount.CycleCountStatus;
import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryAdjustmentService {

    private final CycleCountRepository cycleCountRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 실사 시작
     */
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. 기본 검증
        validateCycleCountRequest(request);

        // 2. Entity 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                request.getProductId(),
                request.getLocationId(),
                request.getLotNumber(),
                request.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at location", "INVENTORY_NOT_FOUND"));

        // 4. 이미 진행 중인 실사가 있는지 확인
        List<CycleCount> inProgressCounts = cycleCountRepository.findByLocationIdAndStatus(
                request.getLocationId(),
                CycleCountStatus.IN_PROGRESS
        );
        if (!inProgressCounts.isEmpty()) {
            throw new BusinessException("Cycle count already in progress for this location", "CYCLE_COUNT_IN_PROGRESS");
        }

        // 5. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 6. CycleCount 엔티티 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .product(product)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .systemQty(inventory.getQuantity())
                .status(CycleCountStatus.IN_PROGRESS)
                .countedBy(request.getCountedBy())
                .startedAt(OffsetDateTime.now())
                .build();

        cycleCountRepository.save(cycleCount);

        return buildCycleCountResponse(cycleCount);
    }

    /**
     * 실사 완료
     */
    public CycleCountResponse completeCycleCount(UUID cycleCountId, CycleCountCompleteRequest request) {
        // 1. CycleCount 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new BusinessException("Cycle count not found", "CYCLE_COUNT_NOT_FOUND"));

        if (cycleCount.getStatus() != CycleCountStatus.IN_PROGRESS) {
            throw new BusinessException("Cycle count is not in progress", "INVALID_STATUS");
        }

        // 2. 실사 수량 입력 검증
        if (request.getCountedQty() == null || request.getCountedQty() < 0) {
            throw new BusinessException("Counted quantity must be non-negative", "INVALID_COUNTED_QTY");
        }

        // 3. 실사 수량 업데이트
        cycleCount.setCountedQty(request.getCountedQty());
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCountRepository.save(cycleCount);

        // 4. 차이가 있으면 자동으로 재고 조정 생성
        int difference = request.getCountedQty() - cycleCount.getSystemQty();
        if (difference != 0) {
            InventoryAdjustmentRequest adjRequest = InventoryAdjustmentRequest.builder()
                    .productId(cycleCount.getProduct().getId())
                    .locationId(cycleCount.getLocation().getId())
                    .lotNumber(cycleCount.getLotNumber())
                    .expiryDate(cycleCount.getExpiryDate())
                    .actualQty(request.getCountedQty())
                    .reason("[실사] 실제 재고와 시스템 재고 불일치")
                    .createdBy(cycleCount.getCountedBy())
                    .build();

            createAdjustmentFromCycleCount(adjRequest, cycleCount);
        }

        // 5. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        return buildCycleCountResponse(cycleCount);
    }

    /**
     * 재고 조정 생성
     */
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. 기본 검증
        validateAdjustmentRequest(request);

        // 2. Entity 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                request.getProductId(),
                request.getLocationId(),
                request.getLotNumber(),
                request.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at location", "INVENTORY_NOT_FOUND"));

        // 4. 차이 계산
        int systemQty = inventory.getQuantity();
        int actualQty = request.getActualQty();
        int differenceQty = actualQty - systemQty;

        // 5. 승인 상태 결정
        ApprovalStatus approvalStatus = determineApprovalStatus(
                product,
                location,
                systemQty,
                differenceQty
        );

        // 6. InventoryAdjustment 엔티티 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .systemQty(systemQty)
                .actualQty(actualQty)
                .differenceQty(differenceQty)
                .reason(request.getReason())
                .approvalStatus(approvalStatus)
                .createdBy(request.getCreatedBy())
                .build();

        inventoryAdjustmentRepository.save(adjustment);

        // 7. 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 승인
     */
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("Adjustment not found", "ADJUSTMENT_NOT_FOUND"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Can only approve pending adjustments", "INVALID_STATUS");
        }

        // 승인 처리
        adjustment.setApprovalStatus(ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        inventoryAdjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(adjustment);

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 거부
     */
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("Adjustment not found", "ADJUSTMENT_NOT_FOUND"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Can only reject pending adjustments", "INVALID_STATUS");
        }

        adjustment.setApprovalStatus(ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        inventoryAdjustmentRepository.save(adjustment);

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("Adjustment not found", "ADJUSTMENT_NOT_FOUND"));

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> getAdjustments(Pageable pageable) {
        Page<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findAll(pageable);
        return adjustments.map(this::buildAdjustmentResponse);
    }

    // ===== Private Helper Methods =====

    /**
     * 실사 요청 검증
     */
    private void validateCycleCountRequest(CycleCountRequest request) {
        if (request.getLocationId() == null) {
            throw new BusinessException("Location ID is required", "LOCATION_ID_REQUIRED");
        }
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", "PRODUCT_ID_REQUIRED");
        }
    }

    /**
     * 조정 요청 검증
     */
    private void validateAdjustmentRequest(InventoryAdjustmentRequest request) {
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", "PRODUCT_ID_REQUIRED");
        }
        if (request.getLocationId() == null) {
            throw new BusinessException("Location ID is required", "LOCATION_ID_REQUIRED");
        }
        if (request.getActualQty() == null || request.getActualQty() < 0) {
            throw new BusinessException("Actual quantity must be non-negative", "INVALID_ACTUAL_QTY");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("Reason is required", "REASON_REQUIRED");
        }
    }

    /**
     * 승인 상태 결정
     */
    private ApprovalStatus determineApprovalStatus(
            Product product,
            Location location,
            int systemQty,
            int differenceQty
    ) {
        // 1. HIGH_VALUE 카테고리: 차이가 0이 아닌 모든 경우 관리자 승인 필요
        if (product.getCategory() == ProductCategory.HIGH_VALUE && differenceQty != 0) {
            return ApprovalStatus.PENDING;
        }

        // 2. 시스템 수량이 0인데 실물이 발견된 경우: 무조건 승인 필요
        if (systemQty == 0 && differenceQty > 0) {
            return ApprovalStatus.PENDING;
        }

        // 3. 카테고리별 자동승인 임계치 확인
        double threshold = getAutoApprovalThreshold(product.getCategory());
        double diffPct = systemQty == 0 ? 100.0 : Math.abs((double) differenceQty / systemQty * 100.0);

        if (diffPct > threshold) {
            return ApprovalStatus.PENDING;
        }

        // 4. 연속 조정 감시 (7일 내 2회 이상)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = inventoryAdjustmentRepository.findRecentAdjustments(
                product.getId(),
                location.getId(),
                sevenDaysAgo
        );

        if (recentAdjustments.size() >= 2) {
            return ApprovalStatus.PENDING;
        }

        // 5. 모든 조건 통과 시 자동 승인
        return ApprovalStatus.AUTO_APPROVED;
    }

    /**
     * 카테고리별 자동승인 임계치
     */
    private double getAutoApprovalThreshold(ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 2.0;
        };
    }

    /**
     * 재고 조정 반영
     */
    private void applyAdjustment(InventoryAdjustment adjustment) {
        // 1. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                adjustment.getProduct().getId(),
                adjustment.getLocation().getId(),
                adjustment.getLotNumber(),
                adjustment.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found", "INVENTORY_NOT_FOUND"));

        // 2. 재고 수량 업데이트
        int newQty = adjustment.getActualQty();
        int oldQty = inventory.getQuantity();
        inventory.setQuantity(newQty);
        inventoryRepository.save(inventory);

        // 3. 로케이션 적재량 업데이트
        Location location = adjustment.getLocation();
        int qtyDifference = newQty - oldQty;
        location.setCurrentQty(location.getCurrentQty() + qtyDifference);
        locationRepository.save(location);

        // 4. HIGH_VALUE 카테고리인 경우 감사 로그 기록
        if (adjustment.getProduct().getCategory() == ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = AuditLog.builder()
                    .entityType("INVENTORY_ADJUSTMENT")
                    .entityId(adjustment.getId())
                    .action("APPROVED")
                    .description(String.format("HIGH_VALUE adjustment: %s (SKU: %s) from %d to %d at %s. Difference: %d",
                            adjustment.getProduct().getName(),
                            adjustment.getProduct().getSku(),
                            adjustment.getSystemQty(),
                            adjustment.getActualQty(),
                            adjustment.getLocation().getCode(),
                            adjustment.getDifferenceQty()))
                    .createdBy(adjustment.getApprovedBy() != null ? adjustment.getApprovedBy() : "SYSTEM")
                    .build();

            auditLogRepository.save(auditLog);
        }

        // 5. 안전재고 체크
        checkSafetyStockAfterAdjustment(adjustment.getProduct());
    }

    /**
     * 실사로부터 조정 생성 (내부 메서드)
     */
    private void createAdjustmentFromCycleCount(InventoryAdjustmentRequest request, CycleCount cycleCount) {
        // Entity 조회
        Product product = cycleCount.getProduct();
        Location location = cycleCount.getLocation();

        // 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                product.getId(),
                location.getId(),
                cycleCount.getLotNumber(),
                cycleCount.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found", "INVENTORY_NOT_FOUND"));

        // 차이 계산
        int systemQty = cycleCount.getSystemQty();
        int actualQty = request.getActualQty();
        int differenceQty = actualQty - systemQty;

        // 승인 상태 결정
        ApprovalStatus approvalStatus = determineApprovalStatus(
                product,
                location,
                systemQty,
                differenceQty
        );

        // 연속 조정 감시 태그 추가
        String reason = request.getReason();
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = inventoryAdjustmentRepository.findRecentAdjustments(
                product.getId(),
                location.getId(),
                sevenDaysAgo
        );
        if (recentAdjustments.size() >= 2) {
            reason = "[연속조정감시] " + reason;
        }

        // InventoryAdjustment 엔티티 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .cycleCount(cycleCount)
                .product(product)
                .location(location)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .systemQty(systemQty)
                .actualQty(actualQty)
                .differenceQty(differenceQty)
                .reason(reason)
                .approvalStatus(approvalStatus)
                .createdBy(request.getCreatedBy())
                .build();

        inventoryAdjustmentRepository.save(adjustment);

        // 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }
    }

    /**
     * 조정 후 안전재고 체크
     */
    private void checkSafetyStockAfterAdjustment(Product product) {
        // 전체 가용 재고 확인
        List<Inventory> allInventories = inventoryRepository.findByProductId(product.getId());

        int totalAvailableQty = allInventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule != null && totalAvailableQty <= rule.getMinQty()) {
            // 자동 재발주 요청 기록
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailableQty)
                    .reorderQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    /**
     * CycleCount Response 빌드
     */
    private CycleCountResponse buildCycleCountResponse(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .productId(cycleCount.getProduct().getId())
                .productSku(cycleCount.getProduct().getSku())
                .productName(cycleCount.getProduct().getName())
                .lotNumber(cycleCount.getLotNumber())
                .expiryDate(cycleCount.getExpiryDate())
                .systemQty(cycleCount.getSystemQty())
                .countedQty(cycleCount.getCountedQty())
                .status(cycleCount.getStatus())
                .countedBy(cycleCount.getCountedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .updatedAt(cycleCount.getUpdatedAt())
                .build();
    }

    /**
     * InventoryAdjustment Response 빌드
     */
    private InventoryAdjustmentResponse buildAdjustmentResponse(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .cycleCountId(adjustment.getCycleCount() != null ? adjustment.getCycleCount().getId() : null)
                .productId(adjustment.getProduct().getId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getId())
                .locationCode(adjustment.getLocation().getCode())
                .lotNumber(adjustment.getLotNumber())
                .expiryDate(adjustment.getExpiryDate())
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .differenceQty(adjustment.getDifferenceQty())
                .reason(adjustment.getReason())
                .approvalStatus(adjustment.getApprovalStatus())
                .approvedBy(adjustment.getApprovedBy())
                .approvedAt(adjustment.getApprovedAt())
                .createdBy(adjustment.getCreatedBy())
                .createdAt(adjustment.getCreatedAt())
                .updatedAt(adjustment.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.entity.AutoReorderLog.TriggerReason;
import com.wms.entity.Backorder.BackorderStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.ShipmentOrder.ShipmentStatus;
import com.wms.entity.ShipmentOrderLine.LineStatus;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 출고 지시서 생성
     */
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 출고번호 중복 체크
        if (shipmentOrderRepository.existsByShipmentNumber(request.getShipmentNumber())) {
            throw new BusinessException("Shipment number already exists", "DUPLICATE_SHIPMENT_NUMBER");
        }

        // 2. ShipmentOrder 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .status(ShipmentStatus.PENDING)
                .orderDate(request.getOrderDate() != null ? request.getOrderDate() : OffsetDateTime.now())
                .build();

        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // 3. HAZMAT 분리 출고 처리
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            if (product.getCategory() == ProductCategory.HAZMAT) {
                hazmatLines.add(lineRequest);
            } else {
                nonHazmatLines.add(lineRequest);
            }
        }

        // 4. HAZMAT과 FRESH가 함께 있으면 분리 출고
        boolean hasFresh = nonHazmatLines.stream()
                .anyMatch(line -> {
                    Product p = productRepository.findById(line.getProductId()).orElse(null);
                    return p != null && p.getCategory() == ProductCategory.FRESH;
                });

        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 상품만 별도 shipment_order로 분할 생성
            ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                    .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .status(ShipmentStatus.PENDING)
                    .orderDate(request.getOrderDate() != null ? request.getOrderDate() : OffsetDateTime.now())
                    .build();
            hazmatShipment = shipmentOrderRepository.save(hazmatShipment);

            // HAZMAT 라인 생성
            createShipmentLines(hazmatShipment, hazmatLines);

            // 비-HAZMAT 라인 생성 (원래 출고 지시서에)
            List<ShipmentOrderLine> lines = createShipmentLines(shipmentOrder, nonHazmatLines);

            return buildResponse(shipmentOrder, lines);
        } else {
            // 분리 불필요 시 모든 라인 생성
            List<ShipmentOrderLine> lines = createShipmentLines(shipmentOrder, request.getLines());
            return buildResponse(shipmentOrder, lines);
        }
    }

    /**
     * 피킹 실행
     */
    public ShipmentOrderResponse pickShipment(UUID id) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Shipment order not found", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentStatus.PENDING) {
            throw new BusinessException("Can only pick shipments in PENDING status", "INVALID_STATUS");
        }

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        // 피킹 상태로 변경
        shipmentOrder.setStatus(ShipmentStatus.PICKING);
        shipmentOrderRepository.save(shipmentOrder);

        // 각 라인별 피킹 처리
        for (ShipmentOrderLine line : lines) {
            processPicking(line);
        }

        // 출고 상태 업데이트
        updateShipmentStatus(shipmentOrder, lines);

        return buildResponse(shipmentOrder, lines);
    }

    /**
     * 출고 확정
     */
    public ShipmentOrderResponse shipOrder(UUID id) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Shipment order not found", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentStatus.PICKING && shipmentOrder.getStatus() != ShipmentStatus.PARTIAL) {
            throw new BusinessException("Can only ship orders in PICKING or PARTIAL status", "INVALID_STATUS");
        }

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        // 출고 확정 처리
        for (ShipmentOrderLine line : lines) {
            if (line.getPickedQty() > 0) {
                // 안전재고 체크
                checkAndTriggerSafetyStockReorder(line.getProduct());
            }
        }

        shipmentOrder.setStatus(ShipmentStatus.SHIPPED);
        shipmentOrder.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipmentOrder);

        return buildResponse(shipmentOrder, lines);
    }

    /**
     * 출고 상세 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID id) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Shipment order not found", "SHIPMENT_NOT_FOUND"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        return buildResponse(shipmentOrder, lines);
    }

    /**
     * 출고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<ShipmentOrderResponse> getShipmentOrders(Pageable pageable) {
        Page<ShipmentOrder> shipments = shipmentOrderRepository.findAll(pageable);

        return shipments.map(shipment -> {
            List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(shipment.getId());
            return buildResponse(shipment, lines);
        });
    }

    // ===== Private Helper Methods =====

    /**
     * 출고 라인 생성
     */
    private List<ShipmentOrderLine> createShipmentLines(
            ShipmentOrder shipmentOrder,
            List<ShipmentOrderRequest.ShipmentOrderLineRequest> lineRequests
    ) {
        List<ShipmentOrderLine> lines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineRequest : lineRequests) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipmentOrder)
                    .product(product)
                    .requestedQty(lineRequest.getRequestedQty())
                    .pickedQty(0)
                    .status(LineStatus.PENDING)
                    .build();

            lines.add(line);
        }

        return shipmentOrderLineRepository.saveAll(lines);
    }

    /**
     * 피킹 처리
     */
    private void processPicking(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // HAZMAT 상품의 경우 max_pick_qty 제한 확인
        if (product.getCategory() == ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                throw new BusinessException(
                        String.format("HAZMAT product exceeds max pick quantity: requested %d, max %d",
                                requestedQty, product.getMaxPickQty()),
                        "HAZMAT_MAX_PICK_EXCEEDED"
                );
            }
        }

        // 피킹 가능한 재고 조회 (FIFO/FEFO)
        List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

        int totalAvailable = availableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 부분출고 의사결정 트리
        if (totalAvailable == 0) {
            // 전량 백오더
            createBackorder(line, requestedQty);
            line.setStatus(LineStatus.BACKORDERED);
        } else if (totalAvailable < requestedQty * 0.3) {
            // 가용 재고 < 요청의 30%: 전량 백오더
            createBackorder(line, requestedQty);
            line.setStatus(LineStatus.BACKORDERED);
        } else if (totalAvailable >= requestedQty * 0.3 && totalAvailable < requestedQty * 0.7) {
            // 가용 재고 30% ~ 70%: 부분출고 + 백오더 + 긴급발주 트리거
            int pickedQty = pickFromInventories(availableInventories, totalAvailable, product);
            line.setPickedQty(pickedQty);
            line.setStatus(LineStatus.PARTIAL);

            createBackorder(line, requestedQty - pickedQty);
            triggerUrgentReorder(product, totalAvailable);
        } else if (totalAvailable >= requestedQty * 0.7 && totalAvailable < requestedQty) {
            // 가용 재고 70% 이상: 부분출고 + 백오더
            int pickedQty = pickFromInventories(availableInventories, totalAvailable, product);
            line.setPickedQty(pickedQty);
            line.setStatus(LineStatus.PARTIAL);

            createBackorder(line, requestedQty - pickedQty);
        } else {
            // 가용 재고 충분: 전량 출고
            int pickedQty = pickFromInventories(availableInventories, requestedQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(LineStatus.PICKED);
        }

        shipmentOrderLineRepository.save(line);
    }

    /**
     * 피킹 가능한 재고 조회 (FIFO/FEFO + 비즈니스 규칙)
     */
    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        List<Inventory> inventories = inventoryRepository.findByProductId(product.getId());

        LocalDate today = LocalDate.now();

        return inventories.stream()
                // 1. is_expired 제외
                .filter(inv -> !inv.getIsExpired())
                // 2. 유통기한 만료 제외
                .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
                // 3. 잔여율 < 10% 제외 (폐기 대상)
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(
                                inv.getManufactureDate(), inv.getExpiryDate(), today
                        );
                        return remainingPct >= 10.0;
                    }
                    return true;
                })
                // 4. 실사 동결 로케이션 제외
                .filter(inv -> !inv.getLocation().getIsFrozen())
                // 5. HAZMAT zone 전용 피킹 (HAZMAT 상품은 HAZMAT zone에서만)
                .filter(inv -> {
                    if (product.getCategory() == ProductCategory.HAZMAT) {
                        return inv.getLocation().getZone() == Location.Zone.HAZMAT;
                    }
                    return true;
                })
                // 6. 수량 > 0
                .filter(inv -> inv.getQuantity() > 0)
                // 7. 정렬: 잔여율 < 30% 최우선, 그 다음 FEFO, 마지막 FIFO
                .sorted((inv1, inv2) -> {
                    // 잔여율 우선순위
                    boolean inv1LowShelfLife = false;
                    boolean inv2LowShelfLife = false;

                    if (inv1.getExpiryDate() != null && inv1.getManufactureDate() != null) {
                        double pct1 = calculateRemainingShelfLifePct(
                                inv1.getManufactureDate(), inv1.getExpiryDate(), today
                        );
                        inv1LowShelfLife = pct1 < 30.0;
                    }

                    if (inv2.getExpiryDate() != null && inv2.getManufactureDate() != null) {
                        double pct2 = calculateRemainingShelfLifePct(
                                inv2.getManufactureDate(), inv2.getExpiryDate(), today
                        );
                        inv2LowShelfLife = pct2 < 30.0;
                    }

                    if (inv1LowShelfLife && !inv2LowShelfLife) return -1;
                    if (!inv1LowShelfLife && inv2LowShelfLife) return 1;

                    // FEFO: 유통기한 빠른 것 우선
                    if (inv1.getExpiryDate() != null && inv2.getExpiryDate() != null) {
                        int expCompare = inv1.getExpiryDate().compareTo(inv2.getExpiryDate());
                        if (expCompare != 0) return expCompare;
                    } else if (inv1.getExpiryDate() != null) {
                        return -1;
                    } else if (inv2.getExpiryDate() != null) {
                        return 1;
                    }

                    // FIFO: 먼저 입고된 것 우선
                    return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
                })
                .collect(Collectors.toList());
    }

    /**
     * 재고에서 피킹 수행
     */
    private int pickFromInventories(List<Inventory> inventories, int qtyToPick, Product product) {
        int remainingQty = qtyToPick;
        int totalPicked = 0;

        for (Inventory inventory : inventories) {
            if (remainingQty <= 0) break;

            int pickQty = Math.min(inventory.getQuantity(), remainingQty);

            // 재고 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // 로케이션 적재량 차감
            Location location = inventory.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고
            if (product.getStorageType() != location.getStorageType()) {
                createAuditLog("SHIPMENT", inventory.getId(),
                        "STORAGE_TYPE_MISMATCH",
                        String.format("Product storage type %s mismatches location storage type %s",
                                product.getStorageType(), location.getStorageType()));
            }

            totalPicked += pickQty;
            remainingQty -= pickQty;
        }

        return totalPicked;
    }

    /**
     * 백오더 생성
     */
    private void createBackorder(ShipmentOrderLine line, int backorderedQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .backorderedQty(backorderedQty)
                .status(BackorderStatus.OPEN)
                .build();

        backorderRepository.save(backorder);
    }

    /**
     * 긴급발주 트리거
     */
    private void triggerUrgentReorder(Product product, int currentStock) {
        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason(TriggerReason.URGENT_REORDER)
                .currentStock(currentStock)
                .reorderQty(0) // 긴급발주는 수량 미정
                .build();

        autoReorderLogRepository.save(log);
    }

    /**
     * 안전재고 체크 및 자동 재발주
     */
    private void checkAndTriggerSafetyStockReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule == null) return;

        // 전체 가용 재고 계산 (expired 제외)
        int totalAvailable = inventoryRepository.findByProductId(product.getId()).stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason(TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailable)
                    .reorderQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 감사 로그 생성
     */
    private void createAuditLog(String entityType, UUID entityId, String action, String description) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .description(description)
                .createdBy("SYSTEM")
                .build();

        auditLogRepository.save(log);
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalShelfLife <= 0) return 0.0;

        return (remainingShelfLife * 100.0) / totalShelfLife;
    }

    /**
     * 출고 상태 업데이트
     */
    private void updateShipmentStatus(ShipmentOrder shipmentOrder, List<ShipmentOrderLine> lines) {
        boolean allPicked = lines.stream().allMatch(line -> line.getStatus() == LineStatus.PICKED);
        boolean anyPicked = lines.stream().anyMatch(line -> line.getPickedQty() > 0);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentStatus.PICKING);
        } else if (anyPicked) {
            shipmentOrder.setStatus(ShipmentStatus.PARTIAL);
        }

        shipmentOrderRepository.save(shipmentOrder);
    }

    /**
     * Response 빌드
     */
    private ShipmentOrderResponse buildResponse(ShipmentOrder shipmentOrder, List<ShipmentOrderLine> lines) {
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
                .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                        .id(line.getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus())
                        .build())
                .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
                .id(shipmentOrder.getId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus())
                .orderDate(shipmentOrder.getOrderDate())
                .shippedAt(shipmentOrder.getShippedAt())
                .lines(lineResponses)
                .createdAt(shipmentOrder.getCreatedAt())
                .updatedAt(shipmentOrder.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.entity.Location.Zone;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.StockTransfer.TransferStatus;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
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
@Transactional
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행
     */
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // 1. 기본 검증
        validateBasicRequest(request);

        // 2. Entity 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("From location not found", "FROM_LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("To location not found", "TO_LOCATION_NOT_FOUND"));

        // 3. 동일 로케이션 체크
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new BusinessException("Cannot transfer to the same location", "SAME_LOCATION");
        }

        // 4. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("From location is frozen for cycle count", "FROM_LOCATION_FROZEN");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("To location is frozen for cycle count", "TO_LOCATION_FROZEN");
        }

        // 5. 재고 조회
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                product.getId(),
                fromLocation.getId(),
                request.getLotNumber(),
                request.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        // 6. 재고 부족 체크
        if (sourceInventory.getQuantity() < request.getTransferQty()) {
            throw new BusinessException(
                    String.format("Insufficient inventory: available %d, requested %d",
                            sourceInventory.getQuantity(), request.getTransferQty()),
                    "INSUFFICIENT_INVENTORY"
            );
        }

        // 7. 보관 유형 호환성 체크
        if (!isStorageTypeCompatible(product.getStorageType(), toLocation.getStorageType(), product.getCategory(), toLocation.getZone())) {
            throw new BusinessException(
                    String.format("Storage type incompatible: product %s cannot be moved to location %s",
                            product.getStorageType(), toLocation.getStorageType()),
                    "STORAGE_TYPE_INCOMPATIBLE"
            );
        }

        // 8. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 9. 유통기한 임박 상품 이동 제한
        if (product.getManagesExpiry() && request.getExpiryDate() != null) {
            validateExpiryConstraints(request.getExpiryDate(), sourceInventory.getManufactureDate(), toLocation.getZone());
        }

        // 10. 도착지 로케이션 용량 체크
        int newToLocationQty = toLocation.getCurrentQty() + request.getTransferQty();
        if (newToLocationQty > toLocation.getCapacity()) {
            throw new BusinessException(
                    String.format("To location capacity exceeded: current %d + transfer %d > capacity %d",
                            toLocation.getCurrentQty(), request.getTransferQty(), toLocation.getCapacity()),
                    "TO_LOCATION_CAPACITY_EXCEEDED"
            );
        }

        // 11. 대량 이동 승인 체크 (80% 이상)
        boolean requiresApproval = isLargeTransfer(sourceInventory.getQuantity(), request.getTransferQty());

        // 12. StockTransfer 엔티티 생성
        TransferStatus status = requiresApproval ? TransferStatus.PENDING_APPROVAL : TransferStatus.IMMEDIATE;

        StockTransfer stockTransfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .transferQty(request.getTransferQty())
                .transferStatus(status)
                .reason(request.getReason())
                .requestedBy(request.getRequestedBy())
                .transferredAt(OffsetDateTime.now())
                .build();

        stockTransferRepository.save(stockTransfer);

        // 13. 즉시 이동인 경우 재고 반영
        if (status == TransferStatus.IMMEDIATE) {
            executeInventoryMovement(stockTransfer, sourceInventory);
            checkSafetyStockAfterTransfer(product);
        }

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 승인
     */
    public StockTransferResponse approveTransfer(UUID id, String approvedBy) {
        StockTransfer stockTransfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Stock transfer not found", "TRANSFER_NOT_FOUND"));

        if (stockTransfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only approve transfers in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        // 재고 조회 (승인 시점)
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                stockTransfer.getProduct().getId(),
                stockTransfer.getFromLocation().getId(),
                stockTransfer.getLotNumber(),
                stockTransfer.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at source location", "INVENTORY_NOT_FOUND"));

        // 재고 부족 재확인
        if (sourceInventory.getQuantity() < stockTransfer.getTransferQty()) {
            throw new BusinessException(
                    String.format("Insufficient inventory at approval time: available %d, requested %d",
                            sourceInventory.getQuantity(), stockTransfer.getTransferQty()),
                    "INSUFFICIENT_INVENTORY"
            );
        }

        // 재고 반영
        executeInventoryMovement(stockTransfer, sourceInventory);

        // 상태 업데이트
        stockTransfer.setTransferStatus(TransferStatus.APPROVED);
        stockTransfer.setApprovedBy(approvedBy);
        stockTransfer.setApprovedAt(OffsetDateTime.now());
        stockTransferRepository.save(stockTransfer);

        // 안전재고 체크
        checkSafetyStockAfterTransfer(stockTransfer.getProduct());

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 거부
     */
    public StockTransferResponse rejectTransfer(UUID id, String approvedBy) {
        StockTransfer stockTransfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Stock transfer not found", "TRANSFER_NOT_FOUND"));

        if (stockTransfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only reject transfers in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        stockTransfer.setTransferStatus(TransferStatus.REJECTED);
        stockTransfer.setApprovedBy(approvedBy);
        stockTransfer.setApprovedAt(OffsetDateTime.now());
        stockTransferRepository.save(stockTransfer);

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID id) {
        StockTransfer stockTransfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Stock transfer not found", "TRANSFER_NOT_FOUND"));

        return buildResponse(stockTransfer);
    }

    /**
     * 재고 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public Page<StockTransferResponse> getTransfers(Pageable pageable) {
        Page<StockTransfer> transfers = stockTransferRepository.findAll(pageable);
        return transfers.map(this::buildResponse);
    }

    // ===== Private Helper Methods =====

    /**
     * 기본 요청 검증
     */
    private void validateBasicRequest(StockTransferRequest request) {
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", "PRODUCT_ID_REQUIRED");
        }
        if (request.getFromLocationId() == null) {
            throw new BusinessException("From location ID is required", "FROM_LOCATION_ID_REQUIRED");
        }
        if (request.getToLocationId() == null) {
            throw new BusinessException("To location ID is required", "TO_LOCATION_ID_REQUIRED");
        }
        if (request.getTransferQty() == null || request.getTransferQty() <= 0) {
            throw new BusinessException("Transfer quantity must be positive", "INVALID_TRANSFER_QTY");
        }
    }

    /**
     * 보관 유형 호환성 체크
     */
    private boolean isStorageTypeCompatible(
            StorageType productType,
            StorageType locationType,
            ProductCategory productCategory,
            Zone locationZone
    ) {
        // HAZMAT 상품은 HAZMAT zone만 허용
        if (productCategory == ProductCategory.HAZMAT) {
            return locationZone == Zone.HAZMAT;
        }

        // FROZEN → AMBIENT/COLD: 거부 (품질 위험)
        if (productType == StorageType.FROZEN) {
            return locationType == StorageType.FROZEN;
        }

        // COLD → AMBIENT: 거부
        if (productType == StorageType.COLD) {
            return locationType == StorageType.COLD || locationType == StorageType.FROZEN;
        }

        // AMBIENT → COLD/FROZEN: 허용 (상위 호환)
        if (productType == StorageType.AMBIENT) {
            return true;
        }

        return false;
    }

    /**
     * HAZMAT 혼적 금지 검증
     */
    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지 로케이션에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findByLocationId(toLocation.getId());

        boolean isHazmat = product.getCategory() == ProductCategory.HAZMAT;

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT의 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new BusinessException(
                        "Cannot mix HAZMAT with non-HAZMAT products in the same location",
                        "HAZMAT_SEGREGATION_VIOLATION"
                );
            }
            if (!isHazmat && existingIsHazmat) {
                throw new BusinessException(
                        "Cannot mix non-HAZMAT with HAZMAT products in the same location",
                        "HAZMAT_SEGREGATION_VIOLATION"
                );
            }
        }
    }

    /**
     * 유통기한 제약 검증
     */
    private void validateExpiryConstraints(LocalDate expiryDate, LocalDate manufactureDate, Zone toLocationZone) {
        LocalDate today = LocalDate.now();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("Cannot transfer expired products", "EXPIRED_PRODUCT");
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

            if (totalShelfLife > 0) {
                double remainingPct = (remainingShelfLife * 100.0) / totalShelfLife;

                // 잔여 유통기한 < 10%: SHIPPING zone만 허용
                if (remainingPct < 10) {
                    if (toLocationZone != Zone.SHIPPING) {
                        throw new BusinessException(
                                String.format("Products with <10%% shelf life can only be moved to SHIPPING zone (current: %.1f%%)",
                                        remainingPct),
                                "SHELF_LIFE_CONSTRAINT"
                        );
                    }
                }
            }
        }
    }

    /**
     * 대량 이동 여부 확인 (80% 이상)
     */
    private boolean isLargeTransfer(int currentQty, int transferQty) {
        return transferQty >= (currentQty * 0.8);
    }

    /**
     * 재고 이동 실행 (출발지 감소, 도착지 증가)
     */
    private void executeInventoryMovement(StockTransfer stockTransfer, Inventory sourceInventory) {
        // 1. 출발지 재고 감소
        int remainingQty = sourceInventory.getQuantity() - stockTransfer.getTransferQty();
        sourceInventory.setQuantity(remainingQty);
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 적재량 감소
        Location fromLocation = stockTransfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - stockTransfer.getTransferQty());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 재고가 있으면 증가, 없으면 신규 생성)
        Inventory targetInventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                stockTransfer.getProduct().getId(),
                stockTransfer.getToLocation().getId(),
                stockTransfer.getLotNumber(),
                stockTransfer.getExpiryDate()
        ).orElse(null);

        if (targetInventory == null) {
            targetInventory = Inventory.builder()
                    .product(stockTransfer.getProduct())
                    .location(stockTransfer.getToLocation())
                    .lotNumber(stockTransfer.getLotNumber())
                    .quantity(stockTransfer.getTransferQty())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .expiryDate(stockTransfer.getExpiryDate())
                    .receivedAt(sourceInventory.getReceivedAt())
                    .isExpired(false)
                    .build();
        } else {
            targetInventory.setQuantity(targetInventory.getQuantity() + stockTransfer.getTransferQty());
        }
        inventoryRepository.save(targetInventory);

        // 4. 도착지 로케이션 적재량 증가
        Location toLocation = stockTransfer.getToLocation();
        toLocation.setCurrentQty(toLocation.getCurrentQty() + stockTransfer.getTransferQty());
        locationRepository.save(toLocation);
    }

    /**
     * 이동 후 안전재고 체크
     */
    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventories = inventoryRepository.findByProductIdAndZone(
                product.getId(),
                Zone.STORAGE
        );

        int totalStorageQty = storageInventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule != null && totalStorageQty <= rule.getMinQty()) {
            // 자동 재발주 요청 기록
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStorageQty)
                    .reorderQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    /**
     * Response 빌드
     */
    private StockTransferResponse buildResponse(StockTransfer stockTransfer) {
        return StockTransferResponse.builder()
                .id(stockTransfer.getId())
                .productId(stockTransfer.getProduct().getId())
                .productSku(stockTransfer.getProduct().getSku())
                .productName(stockTransfer.getProduct().getName())
                .fromLocationId(stockTransfer.getFromLocation().getId())
                .fromLocationCode(stockTransfer.getFromLocation().getCode())
                .toLocationId(stockTransfer.getToLocation().getId())
                .toLocationCode(stockTransfer.getToLocation().getCode())
                .lotNumber(stockTransfer.getLotNumber())
                .expiryDate(stockTransfer.getExpiryDate())
                .transferQty(stockTransfer.getTransferQty())
                .transferStatus(stockTransfer.getTransferStatus())
                .reason(stockTransfer.getReason())
                .requestedBy(stockTransfer.getRequestedBy())
                .approvedBy(stockTransfer.getApprovedBy())
                .transferredAt(stockTransfer.getTransferredAt())
                .approvedAt(stockTransfer.getApprovedAt())
                .createdAt(stockTransfer.getCreatedAt())
                .updatedAt(stockTransfer.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
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

server:
  port: 8080
  servlet:
    context-path: /api/v1

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- WMS Database Schema

-- Products Table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(20) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    manages_expiry BOOLEAN NOT NULL DEFAULT false,
    shelf_life_days INTEGER,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Locations Table
CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    zone VARCHAR(50) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    current_qty INTEGER NOT NULL DEFAULT 0 CHECK (current_qty >= 0),
    is_frozen BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_capacity CHECK (current_qty <= capacity)
);

-- Inventory Table
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    is_expired BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, location_id, lot_number, expiry_date)
);

-- Suppliers Table
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supplier Penalties Table
CREATE TABLE supplier_penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(50) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    reason TEXT,
    penalized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_supplier_penalties_date ON supplier_penalties(supplier_id, penalized_at);

-- Purchase Orders Table
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number VARCHAR(100) UNIQUE NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    order_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Purchase Order Lines Table
CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_qty INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbound Receipts Table
CREATE TABLE inbound_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number VARCHAR(100) UNIQUE NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_date TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbound Receipt Lines Table
CREATE TABLE inbound_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    received_qty INTEGER NOT NULL CHECK (received_qty > 0),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seasonal Config Table
CREATE TABLE seasonal_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_date_range CHECK (end_date >= start_date)
);

-- Shipment Orders Table
CREATE TABLE shipment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_number VARCHAR(100) UNIQUE NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    order_date TIMESTAMPTZ NOT NULL,
    shipped_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Shipment Order Lines Table
CREATE TABLE shipment_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_id UUID NOT NULL REFERENCES shipment_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    requested_qty INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picked', 'partial', 'backordered')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Backorders Table
CREATE TABLE backorders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_line_id UUID NOT NULL REFERENCES shipment_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    backordered_qty INTEGER NOT NULL CHECK (backordered_qty > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Safety Stock Rules Table
CREATE TABLE safety_stock_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL UNIQUE REFERENCES products(id),
    min_qty INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Auto Reorder Logs Table
CREATE TABLE auto_reorder_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    trigger_reason VARCHAR(50) NOT NULL CHECK (trigger_reason IN ('SAFETY_STOCK_TRIGGER', 'URGENT_REORDER')),
    current_stock INTEGER NOT NULL,
    reorder_qty INTEGER NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Audit Logs Table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Stock Transfers Table
CREATE TABLE stock_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    from_location_id UUID NOT NULL REFERENCES locations(id),
    to_location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    expiry_date DATE,
    transfer_qty INTEGER NOT NULL CHECK (transfer_qty > 0),
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'immediate' CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    reason TEXT,
    requested_by VARCHAR(255),
    approved_by VARCHAR(255),
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_different_locations CHECK (from_location_id != to_location_id)
);

-- Cycle Counts Table
CREATE TABLE cycle_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    lot_number VARCHAR(100),
    expiry_date DATE,
    system_qty INTEGER NOT NULL,
    counted_qty INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    counted_by VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inventory Adjustments Table
CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_count_id UUID REFERENCES cycle_counts(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    expiry_date DATE,
    system_qty INTEGER NOT NULL,
    actual_qty INTEGER NOT NULL,
    difference_qty INTEGER NOT NULL,
    reason TEXT NOT NULL,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'pending' CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_inventory_location ON inventory(location_id);
CREATE INDEX idx_inventory_expiry ON inventory(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_po_lines_po ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_po_lines_product ON purchase_order_lines(product_id);
CREATE INDEX idx_inbound_lines_receipt ON inbound_receipt_lines(inbound_receipt_id);
CREATE INDEX idx_inbound_lines_po_line ON inbound_receipt_lines(purchase_order_line_id);
CREATE INDEX idx_seasonal_dates ON seasonal_config(start_date, end_date);
CREATE INDEX idx_shipment_lines_order ON shipment_order_lines(shipment_order_id);
CREATE INDEX idx_shipment_lines_product ON shipment_order_lines(product_id);
CREATE INDEX idx_backorders_line ON backorders(shipment_order_line_id);
CREATE INDEX idx_backorders_product ON backorders(product_id);
CREATE INDEX idx_safety_stock_product ON safety_stock_rules(product_id);
CREATE INDEX idx_auto_reorder_product ON auto_reorder_logs(product_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_stock_transfers_product ON stock_transfers(product_id);
CREATE INDEX idx_stock_transfers_from_location ON stock_transfers(from_location_id);
CREATE INDEX idx_stock_transfers_to_location ON stock_transfers(to_location_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(transfer_status);
CREATE INDEX idx_cycle_counts_location ON cycle_counts(location_id);
CREATE INDEX idx_cycle_counts_product ON cycle_counts(product_id);
CREATE INDEX idx_cycle_counts_status ON cycle_counts(status);
CREATE INDEX idx_inventory_adjustments_product ON inventory_adjustments(product_id);
CREATE INDEX idx_inventory_adjustments_location ON inventory_adjustments(location_id);
CREATE INDEX idx_inventory_adjustments_status ON inventory_adjustments(approval_status);
CREATE INDEX idx_inventory_adjustments_cycle_count ON inventory_adjustments(cycle_count_id);

