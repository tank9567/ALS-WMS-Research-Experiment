# CLI Output

재고 이동 기능 구현이 완료되었습니다.

## 구현 내용

### 1. DB 스키마
- `V3__create_stock_transfer_table.sql`: stock_transfers 테이블 생성
  - transfer_status 컬럼 포함 (immediate, pending_approval, approved, rejected)

### 2. Entity
- `StockTransfer.java`: 재고 이동 이력 엔티티
  - 출발지/도착지 로케이션, 상품, 수량, 승인 상태 관리

### 3. Repository
- `StockTransferRepository.java`: 재고 이동 조회 메서드
- `InventoryRepository.java`: findByLocation, findByProduct 메서드 추가

### 4. Service (비즈니스 로직)
`StockTransferService.java`에서 ALS-WMS-STK-002 규칙 준수:

**기본 규칙:**
- 단일 트랜잭션 처리 (출발지 차감 + 도착지 증가)
- 동일 로케이션 거부, 재고 부족 체크, 용량 체크
- 이동 이력 기록

**실사 동결 체크:**
- is_frozen=true 로케이션 이동 불가

**보관 유형 호환성:**
- FROZEN→AMBIENT 거부, COLD→AMBIENT 거부
- HAZMAT→비-HAZMAT zone 거부
- AMBIENT→COLD/FROZEN 허용

**위험물 혼적 금지:**
- 도착지에 HAZMAT/비-HAZMAT 혼재 시 거부

**유통기한 이동 제한:**
- 잔여 유통기한 <10% → SHIPPING zone만 허용
- 유통기한 만료 → 이동 불가

**대량 이동 승인:**
- 이동 수량 ≥80% → pending_approval 상태
- 승인/거부 처리 메서드 제공

**안전재고 체크:**
- 이동 후 STORAGE zone 재고가 안전재고 미달 시 auto_reorder_logs 기록

### 5. Controller (REST API)
`StockTransferController.java`:
- `POST /api/v1/stock-transfers` - 재고 이동 실행
- `POST /api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
- `POST /api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
- `GET /api/v1/stock-transfers/{id}` - 이동 상세 조회
- `GET /api/v1/stock-transfers` - 이동 이력 조회

모든 구현은 기존 입고/출고 코드 스타일과 일관성을 유지하며, ALS 문서의 비즈니스 규칙을 준수합니다.


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
    <name>WMS Inbound Module</name>
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
// FILE: src\main\java\com\wms\controller\InboundController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.entity.InboundReceipt;
import com.wms.service.InboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<ApiResponse<InboundReceipt>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceipt receipt = inboundService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 확정
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceipt>> confirmReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.confirmReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 확정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 거부
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceipt>> rejectReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.rejectReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 거부 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 유통기한 경고 승인
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceipt>> approveShelfLifeWarning(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.approveShelfLifeWarning(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("승인 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceipt>> getReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.getReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceipt>>> getAllReceipts() {
        try {
            List<InboundReceipt> receipts = inboundService.getAllReceipts();
            return ResponseEntity.ok(ApiResponse.success(receipts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.entity.ShipmentOrder;
import com.wms.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    /**
     * 출고 지시서 생성
     * POST /api/v1/shipment-orders
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrder>> createShipmentOrder(@RequestBody ShipmentOrderRequest request) {
        try {
            ShipmentOrder shipment = shipmentService.createShipmentOrder(request);
            return ResponseEntity.ok(ApiResponse.success(shipment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 피킹 실행
     * POST /api/v1/shipment-orders/{id}/pick
     */
    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrder>> executePicking(@PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrder shipment = shipmentService.executePicking(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(shipment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 출고 확정
     * POST /api/v1/shipment-orders/{id}/ship
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrder>> confirmShipment(@PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrder shipment = shipmentService.confirmShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(shipment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 출고 지시서 상세 조회
     * GET /api/v1/shipment-orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrder>> getShipmentOrder(@PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrder shipment = shipmentService.getShipmentOrder(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(shipment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 출고 지시서 목록 조회
     * GET /api/v1/shipment-orders
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrder>>> getAllShipmentOrders() {
        try {
            List<ShipmentOrder> shipments = shipmentService.getAllShipmentOrders();
            return ResponseEntity.ok(ApiResponse.success(shipments));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.TransferApprovalRequest;
import com.wms.entity.StockTransfer;
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

    /**
     * 재고 이동 실행
     * POST /api/v1/stock-transfers
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransfer>> transferStock(
            @RequestBody StockTransferRequest request) {
        try {
            StockTransfer transfer = stockTransferService.transferStock(
                    request.getFromLocationId(),
                    request.getToLocationId(),
                    request.getProductId(),
                    request.getLotNumber(),
                    request.getQuantity(),
                    request.getRequestedBy()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(transfer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 이동 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 대량 이동 승인
     * POST /api/v1/stock-transfers/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransfer>> approveTransfer(
            @PathVariable UUID id,
            @RequestBody TransferApprovalRequest request) {
        try {
            StockTransfer transfer = stockTransferService.approveTransfer(id, request.getApprovedBy());
            return ResponseEntity.ok(ApiResponse.success(transfer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("승인 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 대량 이동 거부
     * POST /api/v1/stock-transfers/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransfer>> rejectTransfer(
            @PathVariable UUID id,
            @RequestBody TransferApprovalRequest request) {
        try {
            StockTransfer transfer = stockTransferService.rejectTransfer(id, request.getApprovedBy());
            return ResponseEntity.ok(ApiResponse.success(transfer));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("거부 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 이동 상세 조회
     * GET /api/v1/stock-transfers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransfer>> getTransfer(@PathVariable UUID id) {
        try {
            StockTransfer transfer = stockTransferService.getTransfer(id);
            return ResponseEntity.ok(ApiResponse.success(transfer));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 이동 이력 조회
     * GET /api/v1/stock-transfers
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransfer>>> getAllTransfers() {
        try {
            List<StockTransfer> transfers = stockTransferService.getAllTransfers();
            return ResponseEntity.ok(ApiResponse.success(transfers));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private ErrorDetail error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder().message(message).build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundLineRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundLineRequest {
    private UUID productId;
    private UUID locationId;
    private Integer quantity;
    private String lotNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptRequest {
    private UUID poId;
    private String receivedBy;
    private List<InboundLineRequest> lines;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentLineRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ShipmentLineRequest {
    private UUID productId;
    private Integer requestedQty;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ShipmentOrderRequest {
    private String customerName;
    private String createdBy;
    private List<ShipmentLineRequest> lines;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferRequest {
    private UUID fromLocationId;
    private UUID toLocationId;
    private UUID productId;
    private String lotNumber;
    private Integer quantity;
    private String requestedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\TransferApprovalRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferApprovalRequest {
    private String approvedBy;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (auditId == null) {
            auditId = UUID.randomUUID();
        }
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoReorderLog {

    @Id
    @Column(name = "log_id")
    private UUID logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private Reason reason;

    @Column(name = "triggered_at")
    private OffsetDateTime triggeredAt;

    public enum Reason {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }

    @PrePersist
    protected void onCreate() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
        triggeredAt = OffsetDateTime.now();
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Backorder {

    @Id
    @Column(name = "backorder_id")
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_line_id", nullable = false)
    private ShipmentOrderLine shipmentOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.open;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    public enum Status {
        open, fulfilled, cancelled
    }

    @PrePersist
    protected void onCreate() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.inspecting;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum Status {
        inspecting, pending_approval, confirmed, rejected
    }

    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        receivedAt = OffsetDateTime.now();
        createdAt = OffsetDateTime.now();
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
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptLine {

    @Id
    @Column(name = "receipt_line_id")
    private UUID receiptLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }

    @PrePersist
    protected void onCreate() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Category category = Category.GENERAL;

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

    public enum Category {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
    }

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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.pending;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum Status {
        pending, partial, completed, cancelled, hold
    }

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"po_id", "product_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLine {

    @Id
    @Column(name = "po_line_id")
    private UUID poLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyStockRule {

    @Id
    @Column(name = "rule_id")
    private UUID ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrder {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.pending;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "picked_at")
    private OffsetDateTime pickedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    public enum Status {
        pending, picking, partial, shipped, cancelled
    }

    @PrePersist
    protected void onCreate() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrderLine {

    @Id
    @Column(name = "line_id")
    private UUID lineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.pending;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum Status {
        pending, picked, partial, backordered
    }

    @PrePersist
    protected void onCreate() {
        if (lineId == null) {
            lineId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransfer {

    @Id
    @Column(name = "transfer_id")
    private UUID transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 50)
    private TransferStatus transferStatus;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "transfer_date")
    private OffsetDateTime transferDate;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum TransferStatus {
        immediate, pending_approval, approved, rejected
    }

    @PrePersist
    protected void onCreate() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Status status = Status.active;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum Status {
        active, hold, inactive
    }

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPenalty {

    @Id
    @Column(name = "penalty_id")
    private UUID penaltyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private PenaltyType penaltyType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }

    @PrePersist
    protected void onCreate() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
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
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceipt;
import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {
    List<InboundReceiptLine> findByInboundReceipt(InboundReceipt inboundReceipt);
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
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByProductAndLocationAndLotNumber(Product product, Location location, String lotNumber);
    List<Inventory> findByLocation(Location location);
    List<Inventory> findByProduct(Product product);
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

import com.wms.entity.PurchaseOrder;
import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPurchaseOrder(PurchaseOrder purchaseOrder);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import com.wms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findBySupplierAndStatus(Supplier supplier, PurchaseOrder.Status status);
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
    Optional<SafetyStockRule> findByProduct_ProductId(UUID productId);
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
    @Query("SELECT s FROM SeasonalConfig s WHERE s.isActive = true " +
           "AND s.startDate <= :date AND s.endDate >= :date")
    Optional<SeasonalConfig> findActiveSeasonForDate(@Param("date") LocalDate date);
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
    List<ShipmentOrderLine> findByShipmentOrder_ShipmentId(UUID shipmentId);
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

    List<StockTransfer> findByTransferStatus(StockTransfer.TransferStatus transferStatus);

    List<StockTransfer> findByProductProductId(UUID productId);

    List<StockTransfer> findByFromLocationLocationId(UUID locationId);

    List<StockTransfer> findByToLocationLocationId(UUID locationId);
}


============================================================
// FILE: src\main\java\com\wms\repository\SupplierPenaltyRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Supplier;
import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {
    List<SupplierPenalty> findBySupplierAndCreatedAtAfter(Supplier supplier, OffsetDateTime since);
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
// FILE: src\main\java\com\wms\service\InboundService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundLineRequest;
import com.wms.dto.InboundReceiptRequest;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboundService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태)
     * ALS-WMS-INB-002: 입고 등록 시 모든 검증 수행
     */
    @Transactional
    public InboundReceipt createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 확인
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다: " + request.getPoId()));

        if (po.getStatus() == PurchaseOrder.Status.hold) {
            throw new IllegalStateException("보류된 발주서입니다. 입고가 불가능합니다.");
        }

        // 2. InboundReceipt 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.Status.inspecting)
                .receivedBy(request.getReceivedBy())
                .build();
        inboundReceiptRepository.save(receipt);

        // 3. 각 라인별 검증 및 저장
        boolean requiresApproval = false;

        for (InboundLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineReq.getLocationId()));

            // 3.1. 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new IllegalStateException("실사 중인 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3.2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3.3. 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3.4. 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        LocalDate.now()
                );

                int minPct = product.getMinRemainingShelfLifePct();

                if (remainingPct < minPct) {
                    // 유통기한 부족 -> 거부 및 페널티
                    recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                            SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            String.format("유통기한 잔여율 %.1f%% (기준: %d%%)", remainingPct, minPct));
                    throw new IllegalStateException(
                            String.format("유통기한 잔여율이 부족합니다 (%.1f%% < %d%%): %s",
                                    remainingPct, minPct, product.getSku()));
                }

                if (remainingPct >= 30 && remainingPct <= 50) {
                    // 경고 범위 -> 승인 필요
                    requiresApproval = true;
                }
            }

            // 3.5. 초과입고 체크
            validateOverReceive(po, product, lineReq.getQuantity());

            // 3.6. InboundReceiptLine 생성
            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
            inboundReceiptLineRepository.save(line);
        }

        // 4. 유통기한 경고가 있으면 pending_approval 상태로 변경
        if (requiresApproval) {
            receipt.setStatus(InboundReceipt.Status.pending_approval);
            inboundReceiptRepository.save(receipt);
        }

        // 5. 공급업체 페널티 누적 체크 (30일 내 3회 이상 -> PO hold)
        checkSupplierPenaltyThreshold(po.getSupplier());

        return receipt;
    }

    /**
     * 초과입고 검증
     * ALS-WMS-INB-002: 카테고리별 허용률 + PO유형 가중치 + 성수기 가중치
     */
    private void validateOverReceive(PurchaseOrder po, Product product, int incomingQty) {
        // PO Line 찾기
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);
        PurchaseOrderLine poLine = poLines.stream()
                .filter(line -> line.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("발주서에 해당 상품이 없습니다: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalReceiving = receivedQty + incomingQty;

        // 1. 카테고리별 기본 허용률
        double baseTolerance = switch (product.getCategory()) {
            case GENERAL -> 0.10;
            case FRESH -> 0.05;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 0.03;
        };

        // 2. HAZMAT은 무조건 0% (가중치 무시)
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (totalReceiving > orderedQty) {
                recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                        SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        String.format("HAZMAT 초과입고 시도: %d > %d", totalReceiving, orderedQty));
                throw new IllegalStateException(
                        String.format("HAZMAT 상품은 초과입고가 불가능합니다 (발주: %d, 입고시도: %d)",
                                orderedQty, totalReceiving));
            }
            return;
        }

        // 3. PO 유형별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 4. 성수기 가중치
        double seasonalMultiplier = 1.0;
        var activeSeason = seasonalConfigRepository.findActiveSeasonForDate(LocalDate.now());
        if (activeSeason.isPresent()) {
            seasonalMultiplier = activeSeason.get().getMultiplier().doubleValue();
        }

        // 5. 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonalMultiplier;
        int maxAllowed = (int) (orderedQty * (1.0 + finalTolerance));

        // 6. 검증
        if (totalReceiving > maxAllowed) {
            recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("초과입고: %d > %d (허용률: %.1f%%)",
                            totalReceiving, maxAllowed, finalTolerance * 100));
            throw new IllegalStateException(
                    String.format("초과입고 한도를 초과했습니다 (발주: %d, 최대허용: %d, 입고시도: %d)",
                            orderedQty, maxAllowed, totalReceiving));
        }
    }

    /**
     * 보관 유형 호환성 검증
     * ALS-WMS-INB-002: 상품과 로케이션의 보관 유형 호환성 체크
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT 상품 -> HAZMAT zone만 허용
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                        String.format("HAZMAT 상품은 HAZMAT zone에만 입고할 수 있습니다: %s", product.getSku()));
            }
            return;
        }

        // 보관 유형 호환성
        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD ||
                        locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
        };

        if (!compatible) {
            throw new IllegalStateException(
                    String.format("보관 유형이 호환되지 않습니다 (상품: %s, 로케이션: %s): %s",
                            productType, locationType, product.getSku()));
        }
    }

    /**
     * 유통기한 잔여율 계산
     * (만료일 - 오늘) / (만료일 - 제조일) * 100
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate,
                                                   LocalDate expiryDate,
                                                   LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(Supplier supplier, UUID poId,
                                       SupplierPenalty.PenaltyType type,
                                       String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .poId(poId)
                .penaltyType(type)
                .description(description)
                .build();
        supplierPenaltyRepository.save(penalty);
    }

    /**
     * 공급업체 페널티 임계치 체크
     * 최근 30일 내 3회 이상 -> 모든 pending PO를 hold로 변경
     */
    private void checkSupplierPenaltyThreshold(Supplier supplier) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        List<SupplierPenalty> recentPenalties =
                supplierPenaltyRepository.findBySupplierAndCreatedAtAfter(supplier, thirtyDaysAgo);

        if (recentPenalties.size() >= 3) {
            // 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPOs =
                    purchaseOrderRepository.findBySupplierAndStatus(supplier, PurchaseOrder.Status.pending);

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.Status.hold);
                purchaseOrderRepository.save(po);
            }
        }
    }

    /**
     * 입고 확정
     * ALS-WMS-INB-002: confirmed 상태로 변경하고 재고 반영
     */
    @Transactional
    public InboundReceipt confirmReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting &&
            receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 확정할 수 있습니다.");
        }

        // 1. 상태 변경
        receipt.setStatus(InboundReceipt.Status.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(receipt);

        // 2. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceipt(receipt);
        for (InboundReceiptLine line : lines) {
            updateInventory(line);
            updatePurchaseOrderLine(receipt.getPurchaseOrder(), line.getProduct(), line.getQuantity());
        }

        // 3. PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        return receipt;
    }

    /**
     * 재고 반영
     */
    private void updateInventory(InboundReceiptLine line) {
        // 기존 inventory 찾기 (product + location + lot)
        var existingInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                line.getProduct(), line.getLocation(), line.getLotNumber());

        if (existingInventory.isPresent()) {
            // 기존 재고 증가
            Inventory inv = existingInventory.get();
            inv.setQuantity(inv.getQuantity() + line.getQuantity());
            inventoryRepository.save(inv);
        } else {
            // 신규 재고 생성
            Inventory inv = Inventory.builder()
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(OffsetDateTime.now())
                    .build();
            inventoryRepository.save(inv);
        }

        // Location current_qty 갱신
        Location location = line.getLocation();
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);
    }

    /**
     * PO Line received_qty 누적 갱신
     */
    private void updatePurchaseOrderLine(PurchaseOrder po, Product product, int quantity) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);
        PurchaseOrderLine poLine = poLines.stream()
                .filter(line -> line.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow();

        poLine.setReceivedQty(poLine.getReceivedQty() + quantity);
        purchaseOrderLineRepository.save(poLine);
    }

    /**
     * PO 상태 갱신 (모든 라인 완납 -> completed, 일부 -> partial)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);

        boolean allCompleted = poLines.stream()
                .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());

        boolean anyReceived = poLines.stream()
                .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            po.setStatus(PurchaseOrder.Status.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.Status.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceipt rejectReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting &&
            receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 거부할 수 있습니다.");
        }

        receipt.setStatus(InboundReceipt.Status.rejected);
        inboundReceiptRepository.save(receipt);

        return receipt;
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     */
    @Transactional
    public InboundReceipt approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다.");
        }

        receipt.setStatus(InboundReceipt.Status.inspecting);
        inboundReceiptRepository.save(receipt);

        return receipt;
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceipt getReceipt(UUID receiptId) {
        return inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceipt> getAllReceipts() {
        return inboundReceiptRepository.findAll();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentLineRequest;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.entity.*;
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

    /**
     * 출고 지시서 생성
     * ALS-WMS-OUT-002: HAZMAT+FRESH 분리 출고
     */
    @Transactional
    public ShipmentOrder createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 상품 정보 조회 및 HAZMAT+FRESH 분리 체크
        Map<UUID, Product> productMap = new HashMap<>();
        boolean hasHazmat = false;
        boolean hasFresh = false;

        for (ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));
            productMap.put(product.getProductId(), product);

            if (product.getCategory() == Product.Category.HAZMAT) {
                hasHazmat = true;
            }
            if (product.getCategory() == Product.Category.FRESH) {
                hasFresh = true;
            }
        }

        // 2. HAZMAT+FRESH 공존 시 분리 출고
        if (hasHazmat && hasFresh) {
            return createSeparatedShipments(request, productMap);
        }

        // 3. 일반 출고 지시서 생성
        ShipmentOrder shipment = ShipmentOrder.builder()
                .customerName(request.getCustomerName())
                .createdBy(request.getCreatedBy())
                .status(ShipmentOrder.Status.pending)
                .build();
        shipmentOrderRepository.save(shipment);

        // 4. 출고 라인 생성
        for (ShipmentLineRequest lineReq : request.getLines()) {
            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipment)
                    .product(productMap.get(lineReq.getProductId()))
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.Status.pending)
                    .build();
            shipmentOrderLineRepository.save(line);
        }

        return shipment;
    }

    /**
     * HAZMAT+FRESH 분리 출고 처리
     * ALS-WMS-OUT-002 Constraint: 동일 출고 지시서에 HAZMAT + FRESH 상품이 공존하면 분리 출고
     */
    private ShipmentOrder createSeparatedShipments(ShipmentOrderRequest request, Map<UUID, Product> productMap) {
        // 1. 비-HAZMAT 출고 지시서 생성 (원본)
        ShipmentOrder mainShipment = ShipmentOrder.builder()
                .customerName(request.getCustomerName())
                .createdBy(request.getCreatedBy())
                .status(ShipmentOrder.Status.pending)
                .build();
        shipmentOrderRepository.save(mainShipment);

        // 2. HAZMAT 전용 출고 지시서 생성
        ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                .customerName(request.getCustomerName() + " (HAZMAT)")
                .createdBy(request.getCreatedBy())
                .status(ShipmentOrder.Status.pending)
                .build();
        shipmentOrderRepository.save(hazmatShipment);

        // 3. 라인 분리
        for (ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productMap.get(lineReq.getProductId());
            ShipmentOrder targetShipment = (product.getCategory() == Product.Category.HAZMAT)
                    ? hazmatShipment : mainShipment;

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(targetShipment)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.Status.pending)
                    .build();
            shipmentOrderLineRepository.save(line);
        }

        // 원본(비-HAZMAT) 출고 지시서 반환
        return mainShipment;
    }

    /**
     * 피킹 실행
     * ALS-WMS-OUT-002: FIFO/FEFO, 만료 임박 우선, 부분출고 의사결정
     */
    @Transactional
    public ShipmentOrder executePicking(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.Status.pending) {
            throw new IllegalStateException("대기 상태의 출고만 피킹할 수 있습니다.");
        }

        shipment.setStatus(ShipmentOrder.Status.picking);
        shipmentOrderRepository.save(shipment);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            pickLine(line);
        }

        // 피킹 완료 후 출고 지시서 상태 업데이트
        updateShipmentStatus(shipment);

        return shipment;
    }

    /**
     * 라인별 피킹 처리
     * ALS-WMS-OUT-002: FIFO/FEFO + 잔여율 우선순위 + 부분출고 의사결정
     */
    private void pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // 1. 피킹 가능 재고 조회 (FIFO/FEFO + 만료 임박 우선)
        List<Inventory> pickableInventories = getPickableInventories(product);

        // 2. 가용 재고 합산
        int totalAvailable = pickableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 부분출고 의사결정 트리 (ALS-WMS-OUT-002)
        if (totalAvailable == 0) {
            // 전량 백오더
            handleFullBackorder(line, requestedQty);
        } else if (totalAvailable < requestedQty * 0.3) {
            // 가용 < 30%: 전량 백오더 (부분출고 안 함)
            handleFullBackorder(line, requestedQty);
        } else if (totalAvailable < requestedQty * 0.7) {
            // 30% ≤ 가용 < 70%: 부분출고 + 백오더 + 긴급발주 트리거
            int pickedQty = performPicking(line, pickableInventories, totalAvailable);
            handlePartialBackorder(line, pickedQty, requestedQty - pickedQty);
            triggerUrgentReorder(product);
        } else if (totalAvailable < requestedQty) {
            // 70% ≤ 가용 < 100%: 부분출고 + 백오더
            int pickedQty = performPicking(line, pickableInventories, totalAvailable);
            handlePartialBackorder(line, pickedQty, requestedQty - pickedQty);
        } else {
            // 가용 ≥ 100%: 정상 출고
            performPicking(line, pickableInventories, requestedQty);
            line.setStatus(ShipmentOrderLine.Status.picked);
        }

        shipmentOrderLineRepository.save(line);
    }

    /**
     * 피킹 가능 재고 조회
     * ALS-WMS-OUT-002: FIFO/FEFO + 만료 임박 우선
     */
    private List<Inventory> getPickableInventories(Product product) {
        LocalDate today = LocalDate.now();

        // 1. 기본 필터: is_expired=false, is_frozen=false, expiry_date >= today
        List<Inventory> allInventories = inventoryRepository.findByProduct_ProductId(product.getProductId());

        List<Inventory> pickable = allInventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> {
                    // 실사 동결 로케이션 제외
                    Location loc = inv.getLocation();
                    return !loc.getIsFrozen();
                })
                .filter(inv -> {
                    // 유통기한 지난 것 제외
                    if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 2. HAZMAT 카테고리는 HAZMAT zone만
        if (product.getCategory() == Product.Category.HAZMAT) {
            pickable = pickable.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 3. 잔여율 < 10% 재고 제외 및 is_expired 마킹
        pickable = pickable.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(
                                inv.getManufactureDate(), inv.getExpiryDate(), today);
                        if (remainingPct < 10) {
                            // 출고 불가 -> is_expired=true 설정
                            inv.setIsExpired(true);
                            inventoryRepository.save(inv);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 4. FIFO/FEFO 정렬
        if (product.getHasExpiry()) {
            // FEFO → FIFO (유통기한 관리 상품)
            pickable.sort((a, b) -> {
                // 잔여율 <30% 최우선
                double aPct = calculateRemainingShelfLifePct(a.getManufactureDate(), a.getExpiryDate(), today);
                double bPct = calculateRemainingShelfLifePct(b.getManufactureDate(), b.getExpiryDate(), today);

                boolean aUrgent = aPct < 30;
                boolean bUrgent = bPct < 30;

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;

                // FEFO
                int cmp = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (cmp != 0) return cmp;

                // FIFO
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO만 (유통기한 비관리 상품)
            pickable.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return pickable;
    }

    /**
     * 실제 피킹 수행
     */
    private int performPicking(ShipmentOrderLine line, List<Inventory> inventories, int qtyToPick) {
        Product product = line.getProduct();
        int remainingToPick = qtyToPick;
        int totalPicked = 0;

        for (Inventory inv : inventories) {
            if (remainingToPick == 0) break;

            // HAZMAT max_pick_qty 체크
            int maxPick = remainingToPick;
            if (product.getCategory() == Product.Category.HAZMAT && product.getMaxPickQty() != null) {
                maxPick = Math.min(maxPick, product.getMaxPickQty());
            }

            int pickQty = Math.min(inv.getQuantity(), maxPick);

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inventoryRepository.save(inv);

            // 로케이션 현재 수량 차감
            Location loc = inv.getLocation();
            loc.setCurrentQty(loc.getCurrentQty() - pickQty);
            locationRepository.save(loc);

            // 보관 유형 불일치 경고
            if (!inv.getLocation().getStorageType().equals(product.getStorageType())) {
                createAuditLog("SHIPMENT_PICKING", line.getLineId(),
                        "보관 유형 불일치: 로케이션=" + loc.getStorageType() + ", 상품=" + product.getStorageType(),
                        line.getShipmentOrder().getCreatedBy());
            }

            totalPicked += pickQty;
            remainingToPick -= pickQty;
        }

        line.setPickedQty(line.getPickedQty() + totalPicked);
        return totalPicked;
    }

    /**
     * 부분출고 처리
     */
    private void handlePartialBackorder(ShipmentOrderLine line, int pickedQty, int shortageQty) {
        line.setStatus(ShipmentOrderLine.Status.partial);

        // 백오더 생성
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.Status.open)
                .build();
        backorderRepository.save(backorder);
    }

    /**
     * 전량 백오더 처리
     */
    private void handleFullBackorder(ShipmentOrderLine line, int shortageQty) {
        line.setPickedQty(0);
        line.setStatus(ShipmentOrderLine.Status.backordered);

        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.Status.open)
                .build();
        backorderRepository.save(backorder);
    }

    /**
     * 긴급발주 트리거
     * ALS-WMS-OUT-002: 가용 30%~70% 구간에서 긴급발주
     */
    private void triggerUrgentReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .reorderQty(rule.getReorderQty())
                    .reason(AutoReorderLog.Reason.URGENT_REORDER)
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 출고 확정
     * ALS-WMS-OUT-002: 안전재고 체크
     */
    @Transactional
    public ShipmentOrder confirmShipment(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrder.Status.picking && shipment.getStatus() != ShipmentOrder.Status.partial) {
            throw new IllegalStateException("피킹 중이거나 부분출고 상태만 확정할 수 있습니다.");
        }

        shipment.setStatus(ShipmentOrder.Status.shipped);
        shipment.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipment);

        // 안전재고 체크
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProduct());
        }

        return shipment;
    }

    /**
     * 안전재고 체크
     * ALS-WMS-OUT-002 Constraint: 출고 후 안전재고 체크
     */
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule == null) return;

        // 전체 가용 재고 합산 (is_expired=false만)
        int totalAvailable = inventoryRepository.findByProduct_ProductId(product.getProductId()).stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주
        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .reorderQty(rule.getReorderQty())
                    .reason(AutoReorderLog.Reason.SAFETY_STOCK_TRIGGER)
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 출고 상태 업데이트
     */
    private void updateShipmentStatus(ShipmentOrder shipment) {
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipment.getShipmentId());

        boolean allPicked = lines.stream().allMatch(line -> line.getStatus() == ShipmentOrderLine.Status.picked);
        boolean anyPartial = lines.stream().anyMatch(line -> line.getStatus() == ShipmentOrderLine.Status.partial);

        if (allPicked) {
            shipment.setStatus(ShipmentOrder.Status.picking); // 피킹 완료, 확정 대기
        } else if (anyPartial) {
            shipment.setStatus(ShipmentOrder.Status.partial);
        }

        shipment.setPickedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipment);
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays == 0) return 0;
        return (double) remainingDays / totalDays * 100;
    }

    /**
     * 감사 로그 생성
     */
    private void createAuditLog(String action, UUID entityId, String description, String createdBy) {
        AuditLog log = AuditLog.builder()
                .entityType("SHIPMENT")
                .entityId(entityId)
                .action(action)
                .description(description)
                .createdBy(createdBy)
                .build();
        auditLogRepository.save(log);
    }

    /**
     * 출고 지시서 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrder getShipmentOrder(UUID shipmentId) {
        return shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentId));
    }

    /**
     * 출고 지시서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ShipmentOrder> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 재고 이동 실행
     * ALS-WMS-STK-002: 재고 이동 시 모든 검증 수행
     */
    @Transactional
    public StockTransfer transferStock(UUID fromLocationId, UUID toLocationId,
                                       UUID productId, String lotNumber,
                                       Integer quantity, String requestedBy) {
        // 1. 기본 검증
        Location fromLocation = locationRepository.findById(fromLocationId)
                .orElseThrow(() -> new IllegalArgumentException("출발지 로케이션을 찾을 수 없습니다: " + fromLocationId));

        Location toLocation = locationRepository.findById(toLocationId)
                .orElseThrow(() -> new IllegalArgumentException("도착지 로케이션을 찾을 수 없습니다: " + toLocationId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        // 2. 동일 로케이션 체크
        if (fromLocationId.equals(toLocationId)) {
            throw new IllegalStateException("출발지와 도착지가 동일합니다.");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new IllegalStateException("실사 중인 로케이션에서 이동할 수 없습니다: " + fromLocation.getCode());
        }
        if (toLocation.getIsFrozen()) {
            throw new IllegalStateException("실사 중인 로케이션으로 이동할 수 없습니다: " + toLocation.getCode());
        }

        // 4. 출발지 재고 확인
        Inventory fromInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        product, fromLocation, lotNumber)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("출발지에 재고가 없습니다 (상품: %s, 로케이션: %s, 로트: %s)",
                                product.getSku(), fromLocation.getCode(), lotNumber)));

        if (fromInventory.getQuantity() < quantity) {
            throw new IllegalStateException(
                    String.format("출발지 재고가 부족합니다 (요청: %d, 가용: %d)",
                            quantity, fromInventory.getQuantity()));
        }

        // 5. 보관 유형 호환성 검증
        validateStorageTypeCompatibility(product, toLocation);

        // 6. 위험물 혼적 금지 검증
        validateHazmatSegregation(product, toLocation);

        // 7. 유통기한 이동 제한 검증
        validateExpiryDateRestriction(product, toLocation, fromInventory);

        // 8. 도착지 용량 체크
        if (toLocation.getCurrentQty() + quantity > toLocation.getCapacity()) {
            throw new IllegalStateException(
                    String.format("도착지 로케이션 용량 초과 (현재: %d, 이동: %d, 용량: %d)",
                            toLocation.getCurrentQty(), quantity, toLocation.getCapacity()));
        }

        // 9. 대량 이동 체크 (80% 이상)
        double transferRatio = (double) quantity / fromInventory.getQuantity();
        boolean requiresApproval = transferRatio >= 0.8;

        StockTransfer.TransferStatus status = requiresApproval
                ? StockTransfer.TransferStatus.pending_approval
                : StockTransfer.TransferStatus.immediate;

        // 10. StockTransfer 이력 생성
        StockTransfer transfer = StockTransfer.builder()
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .product(product)
                .lotNumber(lotNumber)
                .quantity(quantity)
                .transferStatus(status)
                .requestedBy(requestedBy)
                .build();
        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 반영
        if (status == StockTransfer.TransferStatus.immediate) {
            executeTransfer(transfer);
        }

        return transfer;
    }

    /**
     * 보관 유형 호환성 검증
     * ALS-WMS-STK-002: 도착지 로케이션의 보관 유형이 상품과 호환되어야 함
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // HAZMAT 상품 -> HAZMAT zone만 허용
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (toLocation.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                        "HAZMAT 상품은 HAZMAT zone으로만 이동할 수 있습니다: " + product.getSku());
            }
            return;
        }

        // 보관 유형 호환성
        boolean compatible = switch (productType) {
            case FROZEN -> {
                if (locationType == Product.StorageType.AMBIENT) {
                    yield false;
                }
                yield true;
            }
            case COLD -> {
                if (locationType == Product.StorageType.AMBIENT) {
                    yield false;
                }
                yield true;
            }
            case AMBIENT -> true; // AMBIENT는 모든 로케이션 허용
        };

        if (!compatible) {
            throw new IllegalStateException(
                    String.format("%s 상품은 %s 로케이션으로 이동할 수 없습니다",
                            productType, locationType));
        }
    }

    /**
     * 위험물 혼적 금지 검증
     * ALS-WMS-STK-002: HAZMAT과 비-HAZMAT의 동일 로케이션 혼적 전면 금지
     */
    private void validateHazmatSegregation(Product product, Location toLocation) {
        boolean isHazmat = product.getCategory() == Product.Category.HAZMAT;

        // 도착지에 이미 적재된 상품 확인
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.Category.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 체크
            if (isHazmat != existingIsHazmat) {
                throw new IllegalStateException(
                        "비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다");
            }
        }
    }

    /**
     * 유통기한 이동 제한 검증
     * ALS-WMS-STK-002: 유통기한 임박/만료 상품의 이동 제한
     */
    private void validateExpiryDateRestriction(Product product, Location toLocation,
                                                Inventory fromInventory) {
        if (!product.getHasExpiry() || fromInventory.getExpiryDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = fromInventory.getExpiryDate();
        LocalDate manufactureDate = fromInventory.getManufactureDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new IllegalStateException("유통기한이 만료된 재고는 이동할 수 없습니다");
        }

        // 잔여 유통기한 비율 계산
        if (manufactureDate != null) {
            double remainingPct = calculateRemainingShelfLifePct(manufactureDate, expiryDate, today);

            // 잔여 유통기한 < 10% -> SHIPPING zone만 허용
            if (remainingPct < 10.0) {
                if (toLocation.getZone() != Location.Zone.SHIPPING) {
                    throw new IllegalStateException(
                            String.format("잔여 유통기한이 %.1f%%인 재고는 SHIPPING zone으로만 이동 가능합니다",
                                    remainingPct));
                }
            }
        }
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate,
                                                   LocalDate expiryDate,
                                                   LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 대량 이동 승인
     * ALS-WMS-STK-002: 80% 이상 대량 이동 승인 처리
     */
    @Transactional
    public StockTransfer approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("재고 이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다");
        }

        // 상태 변경
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        // 재고 이동 실행
        executeTransfer(transfer);

        return transfer;
    }

    /**
     * 대량 이동 거부
     * ALS-WMS-STK-002: 80% 이상 대량 이동 거부 처리
     */
    @Transactional
    public StockTransfer rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("재고 이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 거부할 수 있습니다");
        }

        // 상태 변경
        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        return transfer;
    }

    /**
     * 재고 이동 실행 (단일 트랜잭션)
     * ALS-WMS-STK-002: 출발지 차감 + 도착지 증가를 원자적으로 처리
     */
    private void executeTransfer(StockTransfer transfer) {
        // 1. 출발지 재고 차감
        Inventory fromInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        transfer.getProduct(), transfer.getFromLocation(), transfer.getLotNumber())
                .orElseThrow(() -> new IllegalStateException("출발지 재고를 찾을 수 없습니다"));

        int remainingQty = fromInventory.getQuantity() - transfer.getQuantity();
        if (remainingQty < 0) {
            throw new IllegalStateException("출발지 재고가 부족합니다");
        }

        if (remainingQty == 0) {
            // 재고가 0이면 삭제
            inventoryRepository.delete(fromInventory);
        } else {
            fromInventory.setQuantity(remainingQty);
            inventoryRepository.save(fromInventory);
        }

        // 출발지 로케이션 current_qty 갱신
        Location fromLocation = transfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 2. 도착지 재고 증가
        var toInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                transfer.getProduct(), transfer.getToLocation(), transfer.getLotNumber());

        if (toInventory.isPresent()) {
            // 기존 재고 증가
            Inventory inv = toInventory.get();
            inv.setQuantity(inv.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(inv);
        } else {
            // 신규 재고 생성 (received_at은 원래 값 유지)
            Inventory inv = Inventory.builder()
                    .product(transfer.getProduct())
                    .location(transfer.getToLocation())
                    .quantity(transfer.getQuantity())
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(fromInventory.getExpiryDate())
                    .manufactureDate(fromInventory.getManufactureDate())
                    .receivedAt(fromInventory.getReceivedAt()) // 원래 received_at 유지
                    .build();
            inventoryRepository.save(inv);
        }

        // 도착지 로케이션 current_qty 갱신
        Location toLocation = transfer.getToLocation();
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 3. 이동 완료 시각 기록
        transfer.setTransferDate(OffsetDateTime.now());
        stockTransferRepository.save(transfer);

        // 4. 안전재고 체크
        checkSafetyStock(transfer.getProduct());
    }

    /**
     * 안전재고 체크
     * ALS-WMS-STK-002: 이동 후 STORAGE zone 안전재고 체크
     */
    private void checkSafetyStock(Product product) {
        // 안전재고 규칙 확인
        var safetyRule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId());
        if (safetyRule.isEmpty()) {
            return;
        }

        SafetyStockRule rule = safetyRule.get();

        // STORAGE zone의 전체 재고 합산
        int totalStorageQty = inventoryRepository.findByProduct(product).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주 로그 생성
        if (totalStorageQty < rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .reorderQty(rule.getReorderQty())
                    .reason(AutoReorderLog.Reason.SAFETY_STOCK_TRIGGER)
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 재고 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransfer getTransfer(UUID transferId) {
        return stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("재고 이동 이력을 찾을 수 없습니다: " + transferId));
    }

    /**
     * 재고 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public List<StockTransfer> getAllTransfers() {
        return stockTransferRepository.findAll();
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
// FILE: src\main\resources\application.properties
============================================================
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/wms
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# JSON Configuration
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.time-zone=UTC

# Server Configuration
server.port=8080

# Logging
logging.level.com.wms=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE


============================================================
// FILE: src\main\resources\db\migration\V2__create_shipment_tables.sql
============================================================
-- V2__create_shipment_tables.sql
-- 출고 관련 테이블 추가

-- 출고 지시서
CREATE TABLE shipment_orders (
    shipment_id UUID PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    picked_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    CONSTRAINT chk_shipment_status CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled'))
);

-- 출고 품목
CREATE TABLE shipment_order_lines (
    line_id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_orders(shipment_id),
    product_id UUID NOT NULL REFERENCES products(product_id),
    requested_qty INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_line_status CHECK (status IN ('pending', 'picked', 'partial', 'backordered'))
);

CREATE INDEX idx_shipment_lines_shipment ON shipment_order_lines(shipment_id);
CREATE INDEX idx_shipment_lines_product ON shipment_order_lines(product_id);

-- 백오더
CREATE TABLE backorders (
    backorder_id UUID PRIMARY KEY,
    shipment_line_id UUID NOT NULL REFERENCES shipment_order_lines(line_id),
    product_id UUID NOT NULL REFERENCES products(product_id),
    shortage_qty INTEGER NOT NULL CHECK (shortage_qty > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fulfilled_at TIMESTAMPTZ,
    CONSTRAINT chk_backorder_status CHECK (status IN ('open', 'fulfilled', 'cancelled'))
);

CREATE INDEX idx_backorders_shipment_line ON backorders(shipment_line_id);
CREATE INDEX idx_backorders_product ON backorders(product_id);
CREATE INDEX idx_backorders_status ON backorders(status);

-- 안전재고 규칙
CREATE TABLE safety_stock_rules (
    rule_id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE REFERENCES products(product_id),
    min_qty INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_safety_stock_product ON safety_stock_rules(product_id);

-- 자동 재발주 로그
CREATE TABLE auto_reorder_logs (
    log_id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(product_id),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    reason VARCHAR(50) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reorder_reason CHECK (reason IN ('SAFETY_STOCK_TRIGGER', 'URGENT_REORDER'))
);

CREATE INDEX idx_reorder_logs_product ON auto_reorder_logs(product_id);
CREATE INDEX idx_reorder_logs_triggered ON auto_reorder_logs(triggered_at);

-- 감사 로그
CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at);


============================================================
// FILE: src\main\resources\db\migration\V3__create_stock_transfer_table.sql
============================================================
-- stock_transfers 테이블 생성
CREATE TABLE stock_transfers (
    transfer_id UUID PRIMARY KEY,
    from_location_id UUID NOT NULL,
    to_location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    lot_number VARCHAR(50),
    quantity INT NOT NULL CHECK (quantity > 0),
    transfer_status VARCHAR(50) NOT NULL,
    requested_by VARCHAR(100),
    approved_by VARCHAR(100),
    transfer_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_transfer_from_location FOREIGN KEY (from_location_id) REFERENCES locations(location_id),
    CONSTRAINT fk_stock_transfer_to_location FOREIGN KEY (to_location_id) REFERENCES locations(location_id),
    CONSTRAINT fk_stock_transfer_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_stock_transfers_from_location ON stock_transfers(from_location_id);
CREATE INDEX idx_stock_transfers_to_location ON stock_transfers(to_location_id);
CREATE INDEX idx_stock_transfers_product ON stock_transfers(product_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(transfer_status);
CREATE INDEX idx_stock_transfers_created_at ON stock_transfers(created_at);


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- ========================================
-- WMS Database Schema (Level 2)
-- Based on: ALS-WMS-CORE-002
-- ========================================

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
-- 8. 계절 설정 (Seasonal Config)
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
-- 9. 안전재고 기준 (Safety Stock Rules)
-- ========================================
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

