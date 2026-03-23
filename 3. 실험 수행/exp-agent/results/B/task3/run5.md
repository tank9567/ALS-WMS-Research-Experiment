# CLI Output

재고 이동 기능 구현이 완료되었습니다. 다음 구성 요소들이 생성되었습니다:

## 구현 내용

### 1. Entity
- `StockTransfer.java` - 재고 이동 이력 테이블 (src/main/java/com/wms/entity/StockTransfer.java:1)

### 2. Repository
- `StockTransferRepository.java` - 재고 이동 데이터 접근 (src/main/java/com/wms/repository/StockTransferRepository.java:1)
- `InventoryRepository.java` - 필요한 조회 메서드 추가 (src/main/java/com/wms/repository/InventoryRepository.java:16-17)
- `LocationRepository.java` - Zone별 조회 메서드 추가 (src/main/java/com/wms/repository/LocationRepository.java:14)

### 3. DTO
- `StockTransferRequest.java` - 이동 요청 DTO (src/main/java/com/wms/dto/StockTransferRequest.java:1)
- `StockTransferResponse.java` - 이동 응답 DTO (src/main/java/com/wms/dto/StockTransferResponse.java:1)

### 4. Service
- `StockTransferService.java` - 재고 이동 비즈니스 로직 (src/main/java/com/wms/service/StockTransferService.java:1)

### 5. Controller
- `StockTransferController.java` - REST API 엔드포인트 (src/main/java/com/wms/controller/StockTransferController.java:1)

## 구현된 비즈니스 규칙

1. **단일 트랜잭션 처리** - 출발지 차감과 도착지 증가를 한 트랜잭션으로 처리
2. **동일 로케이션 거부** - 출발지와 도착지가 같으면 에러
3. **재고 부족 체크** - 이동 수량이 출발지 재고보다 많으면 에러
4. **용량 체크** - 도착지 로케이션 capacity 초과 시 거부
5. **보관유형 호환성** - FROZEN→AMBIENT 거부, COLD→AMBIENT 거부 등
6. **HAZMAT 혼적 금지** - HAZMAT과 비-HAZMAT 동일 로케이션 혼적 금지
7. **유통기한 제한** - 잔여 10% 미만은 SHIPPING zone만 허용, 만료 제품은 이동 불가
8. **대량 이동 승인** - 80% 이상 이동 시 관리자 승인 필요 (pending_approval)
9. **안전재고 체크** - 이동 후 STORAGE zone 재고가 안전재고 이하이면 자동 재발주 기록
10. **실사 동결** - is_frozen=true 로케이션은 이동 불가
11. **FIFO 보존** - 이동 시 원래 received_at 유지

## API 엔드포인트

- `POST /api/v1/stock-transfers` - 재고 이동 실행
- `POST /api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
- `POST /api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
- `GET /api/v1/stock-transfers/{id}` - 이동 상세 조회
- `GET /api/v1/stock-transfers` - 이동 이력 조회

기존 코드(입고/출고)와 동일한 구조와 패턴을 따르며, 설계 문서의 모든 요구사항이 구현되었습니다.


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
    <description>Warehouse Management System Backend API</description>

    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot Starter Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Starter Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Boot Starter Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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

        <!-- Spring Boot Starter Test -->
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
import com.wms.exception.BusinessException;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts - 입고 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm - 입고 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(@PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject - 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        try {
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve - 입고 승인 (pending_approval 상태)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(@PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/inbound-receipts/{id} - 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(@PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/inbound-receipts - 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (BusinessException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 비즈니스 예외 처리
     */
    private <T> ResponseEntity<ApiResponse<T>> handleBusinessException(BusinessException e) {
        HttpStatus status;

        switch (e.getCode()) {
            case "NOT_FOUND":
                status = HttpStatus.NOT_FOUND;
                break;
            case "VALIDATION_ERROR":
            case "SHELF_LIFE_REJECTION":
                status = HttpStatus.BAD_REQUEST;
                break;
            case "OVER_DELIVERY":
            case "STORAGE_INCOMPATIBLE":
            case "LOCATION_FROZEN":
            case "INVALID_STATUS":
                status = HttpStatus.CONFLICT;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 전역 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.exception.BusinessException;
import com.wms.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentService shipmentService;

    /**
     * 출고 지시서 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request) {
        try {
            ShipmentOrderResponse response = shipmentService.createShipmentOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(getHttpStatus(e.getCode()))
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 피킹 실행
     */
    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipment(@PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrderResponse response = shipmentService.pickShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(getHttpStatus(e.getCode()))
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 출고 확정
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipment(@PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrderResponse response = shipmentService.shipShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(getHttpStatus(e.getCode()))
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 출고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(@PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrderResponse response = shipmentService.getShipmentOrder(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(getHttpStatus(e.getCode()))
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 출고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        try {
            List<ShipmentOrderResponse> responses = shipmentService.getAllShipmentOrders();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (BusinessException e) {
            return ResponseEntity.status(getHttpStatus(e.getCode()))
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * HTTP 상태 코드 매핑
     */
    private HttpStatus getHttpStatus(String errorCode) {
        switch (errorCode) {
            case "NOT_FOUND":
                return HttpStatus.NOT_FOUND;
            case "VALIDATION_ERROR":
            case "INVALID_STATUS":
                return HttpStatus.BAD_REQUEST;
            case "STORAGE_INCOMPATIBLE":
            case "LOCATION_FROZEN":
                return HttpStatus.CONFLICT;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.exception.BusinessException;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
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
     * POST /api/v1/stock-transfers - 재고 이동 실행
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        try {
            StockTransferResponse response = stockTransferService.executeTransfer(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/approve - 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/reject - 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(id, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/stock-transfers/{id} - 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/stock-transfers - 이동 이력 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        try {
            List<StockTransferResponse> responses = stockTransferService.getAllTransfers();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (BusinessException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 비즈니스 예외 처리
     */
    private <T> ResponseEntity<ApiResponse<T>> handleBusinessException(BusinessException e) {
        HttpStatus status;

        switch (e.getCode()) {
            case "NOT_FOUND":
                status = HttpStatus.NOT_FOUND;
                break;
            case "SAME_LOCATION":
            case "INSUFFICIENT_STOCK":
            case "CAPACITY_EXCEEDED":
            case "STORAGE_INCOMPATIBLE":
            case "HAZMAT_MIXING_FORBIDDEN":
            case "EXPIRED_PRODUCT":
            case "EXPIRY_TRANSFER_RESTRICTED":
            case "LOCATION_FROZEN":
            case "INVALID_STATUS":
                status = HttpStatus.CONFLICT;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 전역 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
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
    private ErrorResponse error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorResponse.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorResponse {
        private String code;
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    @NotNull(message = "PO ID is required")
    private UUID poId;

    @NotBlank(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "At least one receipt line is required")
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
    private String poNumber;
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
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
    }
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
    private OffsetDateTime requestedAt;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ShipmentOrderLineResponse> lines;
    private List<PickDetail> pickDetails;
    private List<BackorderInfo> backorders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PickDetail {
        private UUID productId;
        private String productSku;
        private UUID locationId;
        private String locationCode;
        private Integer pickedQty;
        private String lotNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackorderInfo {
        private UUID backorderId;
        private UUID productId;
        private String productSku;
        private Integer shortageQty;
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

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferredBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

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

    private UUID transferId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferStatus;
    private String transferredBy;
    private String approvedBy;
    private OffsetDateTime transferredAt;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
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

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
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
import org.hibernate.annotations.CreationTimestamp;

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
    @Column(name = "reorder_log_id")
    private UUID reorderLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

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

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum TriggerType {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }

    @PrePersist
    public void prePersist() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
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
import org.hibernate.annotations.CreationTimestamp;

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
    private BackorderStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    public enum BackorderStatus {
        open, fulfilled, cancelled
    }

    @PrePersist
    public void prePersist() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        if (status == null) {
            status = BackorderStatus.open;
        }
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

import java.time.OffsetDateTime;
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
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReceiptStatus status;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (status == null) {
            status = ReceiptStatus.inspecting;
        }
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
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Data
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
    public void prePersist() {
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @Column(name = "inventory_id")
    private UUID inventoryId;

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

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
        if (quantity == null) {
            quantity = 0;
        }
        if (isExpired == null) {
            isExpired = false;
        }
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
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        if (storageType == null) {
            storageType = StorageType.AMBIENT;
        }
        if (currentQty == null) {
            currentQty = 0;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isFrozen == null) {
            isFrozen = false;
        }
    }

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
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
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "sku", unique = true, nullable = false, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "has_expiry", nullable = false)
    private Boolean hasExpiry;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @Column(name = "manufacture_date_required", nullable = false)
    private Boolean manufactureDateRequired;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
        if (category == null) {
            category = ProductCategory.GENERAL;
        }
        if (storageType == null) {
            storageType = StorageType.AMBIENT;
        }
        if (unit == null) {
            unit = "EA";
        }
        if (hasExpiry == null) {
            hasExpiry = false;
        }
        if (manufactureDateRequired == null) {
            manufactureDateRequired = false;
        }
        if (minRemainingShelfLifePct == null) {
            minRemainingShelfLifePct = 30;
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PoStatus status;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
        if (poType == null) {
            poType = PoType.NORMAL;
        }
        if (status == null) {
            status = PoStatus.pending;
        }
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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
@Data
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @PrePersist
    public void prePersist() {
        if (poLineId == null) {
            poLineId = UUID.randomUUID();
        }
        if (receivedQty == null) {
            receivedQty = 0;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @Column(name = "rule_id")
    private UUID ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
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
import org.hibernate.annotations.CreationTimestamp;

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
    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
        if (multiplier == null) {
            multiplier = new BigDecimal("1.50");
        }
        if (isActive == null) {
            isActive = true;
        }
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 30)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    public enum ShipmentStatus {
        pending, picking, partial, shipped, cancelled
    }

    @PrePersist
    public void prePersist() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        if (status == null) {
            status = ShipmentStatus.pending;
        }
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

import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderLine {

    @Id
    @Column(name = "shipment_line_id")
    private UUID shipmentLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LineStatus status;

    public enum LineStatus {
        pending, picked, partial, backordered
    }

    @PrePersist
    public void prePersist() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
        if (pickedQty == null) {
            pickedQty = 0;
        }
        if (status == null) {
            status = LineStatus.pending;
        }
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
    @Column(name = "transfer_id")
    private UUID transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    private TransferStatus transferStatus;

    @Column(name = "transferred_by", nullable = false, length = 100)
    private String transferredBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @CreationTimestamp
    @Column(name = "transferred_at")
    private OffsetDateTime transferredAt;

    @PrePersist
    public void prePersist() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (transferStatus == null) {
            transferStatus = TransferStatus.immediate;
        }
    }

    public enum TransferStatus {
        immediate, pending_approval, approved, rejected
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
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
        if (status == null) {
            status = SupplierStatus.active;
        }
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
import org.hibernate.annotations.CreationTimestamp;

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

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
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
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
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
    List<Inventory> findByProductAndLocation(Product product, Location location);
}


============================================================
// FILE: src\main\java\com\wms\repository\LocationRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByCode(String code);
    List<Location> findByZone(Location.Zone zone);
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

import com.wms.entity.Product;
import com.wms.entity.PurchaseOrder;
import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    Optional<PurchaseOrderLine> findByPurchaseOrderAndProduct(PurchaseOrder purchaseOrder, Product product);
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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
    List<PurchaseOrder> findBySupplierAndStatus(Supplier supplier, PurchaseOrder.PoStatus status);
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

import com.wms.entity.Supplier;
import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier = :supplier AND sp.createdAt >= :startDate")
    long countBySupplierAndCreatedAtAfter(@Param("supplier") Supplier supplier, @Param("startDate") OffsetDateTime startDate);
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
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
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
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태로 생성)
     */
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Purchase order not found"));

        // 2. 입고 receipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .receivedBy(request.getReceivedBy())
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedAt(OffsetDateTime.now())
                .build();

        // 3. 입고 라인 검증 및 추가
        List<InboundReceiptLine> lines = new ArrayList<>();
        boolean requiresApproval = false;

        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Location not found: " + lineReq.getLocationId()));

            // 3-1. 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                if (lineReq.getExpiryDate() == null || lineReq.getManufactureDate() == null) {
                    throw new BusinessException("VALIDATION_ERROR", "Expiry date and manufacture date are required for product: " + product.getSku());
                }

                // 잔여 유통기한 비율 체크
                double remainingPct = calculateRemainingShelfLifePct(lineReq.getExpiryDate(), lineReq.getManufactureDate());

                if (remainingPct < product.getMinRemainingShelfLifePct()) {
                    // 거부 및 페널티 부과
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE, po.getPoId(),
                            "Remaining shelf life " + remainingPct + "% is below minimum " + product.getMinRemainingShelfLifePct() + "%");
                    throw new BusinessException("SHELF_LIFE_REJECTION", "Remaining shelf life is below minimum threshold");
                }

                if (remainingPct >= 30 && remainingPct < 50) {
                    // 승인 필요
                    requiresApproval = true;
                }
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 실사 동결 로케이션 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count: " + location.getCode());
            }

            // 3-4. 초과입고 검증 (확정 시 체크하지만 미리 검증)
            validateOverDelivery(po, product, lineReq.getQuantity());

            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();

            lines.add(line);
        }

        receipt.setLines(lines);

        // 승인이 필요한 경우 상태 변경
        if (requiresApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 확정
     */
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be confirmed in current status: " + receipt.getStatus());
        }

        PurchaseOrder po = receipt.getPurchaseOrder();

        // 각 라인별 처리
        for (InboundReceiptLine line : receipt.getLines()) {
            Product product = line.getProduct();
            Location location = line.getLocation();

            // 1. 초과입고 재검증
            validateOverDelivery(po, product, line.getQuantity());

            // 2. 재고 반영
            Inventory inventory = inventoryRepository
                    .findByProductAndLocationAndLotNumber(product, location, line.getLotNumber())
                    .orElse(Inventory.builder()
                            .product(product)
                            .location(location)
                            .lotNumber(line.getLotNumber())
                            .quantity(0)
                            .expiryDate(line.getExpiryDate())
                            .manufactureDate(line.getManufactureDate())
                            .receivedAt(OffsetDateTime.now())
                            .isExpired(false)
                            .build());

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());

            if (inventory.getExpiryDate() == null && line.getExpiryDate() != null) {
                inventory.setExpiryDate(line.getExpiryDate());
            }
            if (inventory.getManufactureDate() == null && line.getManufactureDate() != null) {
                inventory.setManufactureDate(line.getManufactureDate());
            }

            inventoryRepository.save(inventory);

            // 3. 로케이션 current_qty 증가
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 4. PO line received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderAndProduct(po, product)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "PO line not found for product: " + product.getSku()));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 5. PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 6. 입고 상태 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 거부
     */
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() == InboundReceipt.ReceiptStatus.confirmed ||
            receipt.getStatus() == InboundReceipt.ReceiptStatus.rejected) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be rejected in current status: " + receipt.getStatus());
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 승인 (pending_approval 상태에서)
     */
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in pending_approval status");
        }

        // 승인 후 inspecting으로 변경하여 확정 가능하게 함
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        return mapToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 초과입고 검증
     */
    private void validateOverDelivery(PurchaseOrder po, Product product, int incomingQty) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderAndProduct(po, product)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not in PO: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalAfterReceipt = receivedQty + incomingQty;

        // 카테고리별 기본 허용률
        double allowanceRate = getCategoryAllowanceRate(product.getCategory());

        // 발주 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 성수기 가중치
        double seasonalMultiplier = getSeasonalMultiplier();

        // HAZMAT은 어떤 경우에도 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            allowanceRate = 0.0;
        } else {
            allowanceRate = allowanceRate * poTypeMultiplier * seasonalMultiplier;
        }

        double maxAllowed = orderedQty * (1 + allowanceRate / 100.0);

        if (totalAfterReceipt > maxAllowed) {
            // 초과입고 페널티 기록
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                    "Over delivery: ordered=" + orderedQty + ", total=" + totalAfterReceipt + ", allowed=" + maxAllowed);

            throw new BusinessException("OVER_DELIVERY", "Exceeded maximum allowed quantity. Ordered: " + orderedQty +
                    ", Total after receipt: " + totalAfterReceipt + ", Max allowed: " + maxAllowed);
        }
    }

    /**
     * 카테고리별 초과입고 허용률 (기본값)
     */
    private double getCategoryAllowanceRate(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 10.0;
            case FRESH:
                return 5.0;
            case HAZMAT:
                return 0.0;
            case HIGH_VALUE:
                return 3.0;
            default:
                return 10.0;
        }
    }

    /**
     * 발주 유형별 가중치
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        switch (poType) {
            case NORMAL:
                return 1.0;
            case URGENT:
                return 2.0;
            case IMPORT:
                return 1.5;
            default:
                return 1.0;
        }
    }

    /**
     * 성수기 가중치
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeasonByDate(today)
                .map(season -> season.getMultiplier().doubleValue())
                .orElse(1.0);
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = location.getStorageType();

        // HAZMAT은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("STORAGE_INCOMPATIBLE", "HAZMAT products must be stored in HAZMAT zone");
            }
            return;
        }

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Location.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "FROZEN products require FROZEN location");
        }

        // COLD 상품 → COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
            locationType != Location.StorageType.COLD &&
            locationType != Location.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "COLD products require COLD or FROZEN location");
        }

        // AMBIENT 상품 → AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "AMBIENT products require AMBIENT location");
        }
    }

    /**
     * 공급업체 페널티 기록 및 PO hold 처리
     */
    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType, UUID poId, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .description(description)
                .poId(poId)
                .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierAndCreatedAtAfter(supplier, thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 pending PO를 모두 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findBySupplierAndStatus(supplier, PurchaseOrder.PoStatus.pending);
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(po);
            }

            // 공급업체 상태도 hold로
            supplier.setStatus(Supplier.SupplierStatus.hold);
        }
    }

    /**
     * PO 상태 업데이트
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQty() > 0) {
                anyReceived = true;
            }
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allCompleted = false;
            }
        }

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity -> Response DTO 변환
     */
    private InboundReceiptResponse mapToResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .receiptLineId(line.getReceiptLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getLocationId())
                        .locationCode(line.getLocation().getCode())
                        .quantity(line.getQuantity())
                        .lotNumber(line.getLotNumber())
                        .expiryDate(line.getExpiryDate())
                        .manufactureDate(line.getManufactureDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .receiptId(receipt.getReceiptId())
                .poId(receipt.getPurchaseOrder().getPoId())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
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
import com.wms.exception.BusinessException;
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
@Transactional
public class ShipmentService {

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
        // HAZMAT과 FRESH 분리 출고 체크
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 FRESH가 같이 있으면 분리
        boolean needsSeparation = !hazmatLines.isEmpty() && nonHazmatLines.stream().anyMatch(lineReq -> {
            Product product = productRepository.findById(lineReq.getProductId()).orElse(null);
            return product != null && product.getCategory() == Product.ProductCategory.FRESH;
        });

        if (needsSeparation) {
            // HAZMAT 별도 출고 지시서 생성
            if (!hazmatLines.isEmpty()) {
                ShipmentOrderRequest hazmatRequest = ShipmentOrderRequest.builder()
                        .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                        .customerName(request.getCustomerName())
                        .requestedAt(request.getRequestedAt())
                        .lines(hazmatLines)
                        .build();
                createShipmentOrderInternal(hazmatRequest);
            }

            // 나머지 출고 지시서 생성
            if (!nonHazmatLines.isEmpty()) {
                ShipmentOrderRequest nonHazmatRequest = ShipmentOrderRequest.builder()
                        .shipmentNumber(request.getShipmentNumber())
                        .customerName(request.getCustomerName())
                        .requestedAt(request.getRequestedAt())
                        .lines(nonHazmatLines)
                        .build();
                return createShipmentOrderInternal(nonHazmatRequest);
            } else {
                throw new BusinessException("VALIDATION_ERROR", "Shipment order has only HAZMAT items, which are separated");
            }
        } else {
            return createShipmentOrderInternal(request);
        }
    }

    /**
     * 출고 지시서 생성 (내부 메서드)
     */
    private ShipmentOrderResponse createShipmentOrderInternal(ShipmentOrderRequest request) {
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : OffsetDateTime.now())
                .status(ShipmentOrder.ShipmentStatus.pending)
                .build();

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipmentOrder)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.pending)
                    .build();

            lines.add(line);
        }

        shipmentOrder.setLines(lines);
        ShipmentOrder savedOrder = shipmentOrderRepository.save(shipmentOrder);

        return mapToResponse(savedOrder, null, null);
    }

    /**
     * 피킹 실행
     */
    public ShipmentOrderResponse pickShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("INVALID_STATUS", "Shipment order is not in pending status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);

        List<ShipmentOrderResponse.PickDetail> allPickDetails = new ArrayList<>();
        List<ShipmentOrderResponse.BackorderInfo> allBackorders = new ArrayList<>();

        for (ShipmentOrderLine line : shipmentOrder.getLines()) {
            Product product = line.getProduct();
            int requestedQty = line.getRequestedQty();

            // HAZMAT 상품 최대 피킹 수량 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
                if (requestedQty > product.getMaxPickQty()) {
                    requestedQty = product.getMaxPickQty();
                    // 나머지는 별도 처리 필요 (여기서는 백오더로 처리)
                }
            }

            // 피킹 가능한 재고 조회 (FIFO/FEFO 적용)
            List<Inventory> availableInventories = getAvailableInventoriesForPicking(product);

            int remainingQty = requestedQty;
            int totalPickedQty = 0;

            for (Inventory inventory : availableInventories) {
                if (remainingQty == 0) break;

                // 실사 동결 로케이션 제외
                if (inventory.getLocation().getIsFrozen()) {
                    continue;
                }

                int pickQty = Math.min(remainingQty, inventory.getQuantity());

                // 재고 차감
                inventory.setQuantity(inventory.getQuantity() - pickQty);
                inventoryRepository.save(inventory);

                // 로케이션 current_qty 차감
                Location location = inventory.getLocation();
                location.setCurrentQty(location.getCurrentQty() - pickQty);
                locationRepository.save(location);

                // 보관 유형 불일치 경고
                if (inventory.getLocation().getStorageType() != product.getStorageType()) {
                    recordAuditLog("STORAGE_TYPE_MISMATCH", "SHIPMENT", shipmentId,
                            Map.of("product_id", product.getProductId().toString(),
                                    "location_id", location.getLocationId().toString(),
                                    "product_storage_type", product.getStorageType().toString(),
                                    "location_storage_type", location.getStorageType().toString()),
                            "SYSTEM");
                }

                allPickDetails.add(ShipmentOrderResponse.PickDetail.builder()
                        .productId(product.getProductId())
                        .productSku(product.getSku())
                        .locationId(location.getLocationId())
                        .locationCode(location.getCode())
                        .pickedQty(pickQty)
                        .lotNumber(inventory.getLotNumber())
                        .build());

                remainingQty -= pickQty;
                totalPickedQty += pickQty;
            }

            // 부분출고 의사결정 트리
            int availableQty = totalPickedQty;
            double availableRatio = (double) availableQty / requestedQty;

            if (availableQty >= requestedQty) {
                // 전량 피킹
                line.setPickedQty(totalPickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.picked);
            } else if (availableRatio >= 0.7) {
                // 부분출고 + 백오더
                line.setPickedQty(totalPickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.partial);

                Backorder backorder = createBackorder(line, requestedQty - totalPickedQty);
                allBackorders.add(mapBackorderToInfo(backorder));
            } else if (availableRatio >= 0.3) {
                // 부분출고 + 백오더 + 긴급발주
                line.setPickedQty(totalPickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.partial);

                Backorder backorder = createBackorder(line, requestedQty - totalPickedQty);
                allBackorders.add(mapBackorderToInfo(backorder));

                // 긴급발주 트리거
                recordAutoReorder(product, AutoReorderLog.TriggerType.URGENT_REORDER, "SYSTEM");
            } else {
                // 전량 백오더 (부분출고 안 함)
                // 이미 피킹한 수량 롤백
                for (ShipmentOrderResponse.PickDetail detail : allPickDetails) {
                    if (detail.getProductId().equals(product.getProductId())) {
                        Inventory inv = inventoryRepository.findByProductAndLocationAndLotNumber(
                                        product,
                                        locationRepository.findById(detail.getLocationId()).orElse(null),
                                        detail.getLotNumber())
                                .orElse(null);
                        if (inv != null) {
                            inv.setQuantity(inv.getQuantity() + detail.getPickedQty());
                            inventoryRepository.save(inv);

                            Location loc = inv.getLocation();
                            loc.setCurrentQty(loc.getCurrentQty() + detail.getPickedQty());
                            locationRepository.save(loc);
                        }
                    }
                }
                allPickDetails.removeIf(detail -> detail.getProductId().equals(product.getProductId()));

                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);

                Backorder backorder = createBackorder(line, requestedQty);
                allBackorders.add(mapBackorderToInfo(backorder));
            }

            shipmentOrderLineRepository.save(line);

            // 출고 완료 후 안전재고 체크
            checkSafetyStockAfterShipment(product);
        }

        // 전체 상태 업데이트
        boolean allPicked = shipmentOrder.getLines().stream()
                .allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.picked);
        boolean anyPicked = shipmentOrder.getLines().stream()
                .anyMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.picked || l.getStatus() == ShipmentOrderLine.LineStatus.partial);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        } else if (anyPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.partial);
        } else {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.pending);
        }

        shipmentOrderRepository.save(shipmentOrder);

        return mapToResponse(shipmentOrder, allPickDetails, allBackorders);
    }

    /**
     * 출고 확정
     */
    public ShipmentOrderResponse shipShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
            shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new BusinessException("INVALID_STATUS", "Shipment order cannot be shipped in current status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        shipmentOrder.setShippedAt(OffsetDateTime.now());

        shipmentOrderRepository.save(shipmentOrder);

        return mapToResponse(shipmentOrder, null, null);
    }

    /**
     * 출고 상세 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Shipment order not found"));

        return mapToResponse(shipmentOrder, null, null);
    }

    /**
     * 출고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> orders = shipmentOrderRepository.findAll();
        return orders.stream()
                .map(order -> mapToResponse(order, null, null))
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 피킹 가능한 재고 조회 (FIFO/FEFO 적용)
     */
    private List<Inventory> getAvailableInventoriesForPicking(Product product) {
        // 1. 기본 조건: 수량 > 0, 만료 안 됨, is_expired = false
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().equals(product))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> {
                    // 유통기한 지난 재고 제외
                    if (inv.getExpiryDate() != null) {
                        return !inv.getExpiryDate().isBefore(LocalDate.now());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 2. HAZMAT은 HAZMAT zone만
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            inventories = inventories.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 3. 잔여 유통기한 < 10% 제외 및 is_expired 표시
        inventories = inventories.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                        if (remainingPct < 10) {
                            inv.setIsExpired(true);
                            inventoryRepository.save(inv);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 4. 정렬: FEFO/FIFO
        if (product.getHasExpiry()) {
            // FEFO: 유통기한 오름차순 우선, 잔여율 < 30% 최우선
            inventories.sort((a, b) -> {
                boolean aUrgent = false;
                boolean bUrgent = false;

                if (a.getExpiryDate() != null && a.getManufactureDate() != null) {
                    double aPct = calculateRemainingShelfLifePct(a.getExpiryDate(), a.getManufactureDate());
                    aUrgent = aPct < 30;
                }
                if (b.getExpiryDate() != null && b.getManufactureDate() != null) {
                    double bPct = calculateRemainingShelfLifePct(b.getExpiryDate(), b.getManufactureDate());
                    bUrgent = bPct < 30;
                }

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;

                // 유통기한 비교
                if (a.getExpiryDate() != null && b.getExpiryDate() != null) {
                    int expCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                    if (expCompare != 0) return expCompare;
                }

                // 입고일 비교 (FIFO)
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO: 입고일 오름차순
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 백오더 생성
     */
    private Backorder createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.open)
                .build();

        return backorderRepository.save(backorder);
    }

    /**
     * 출고 완료 후 안전재고 체크
     */
    private void checkSafetyStockAfterShipment(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct(product);
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        // 전체 가용 재고 계산 (is_expired = false)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().equals(product))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            recordAutoReorder(product, AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER, "SYSTEM");
        }
    }

    /**
     * 자동 재발주 기록
     */
    private void recordAutoReorder(Product product, AutoReorderLog.TriggerType triggerType, String triggeredBy) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct(product);
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        int currentStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().equals(product))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(triggerType)
                .currentStock(currentStock)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy(triggeredBy)
                .build();

        autoReorderLogRepository.save(log);
    }

    /**
     * 감사 로그 기록
     */
    private void recordAuditLog(String eventType, String entityType, UUID entityId,
                                 Map<String, Object> details, String performedBy) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .performedBy(performedBy)
                .build();

        auditLogRepository.save(log);
    }

    /**
     * Entity -> Response DTO 변환
     */
    private ShipmentOrderResponse mapToResponse(ShipmentOrder order,
                                                  List<ShipmentOrderResponse.PickDetail> pickDetails,
                                                  List<ShipmentOrderResponse.BackorderInfo> backorders) {
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = order.getLines().stream()
                .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
                .shipmentId(order.getShipmentId())
                .shipmentNumber(order.getShipmentNumber())
                .customerName(order.getCustomerName())
                .status(order.getStatus().name())
                .requestedAt(order.getRequestedAt())
                .shippedAt(order.getShippedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .lines(lineResponses)
                .pickDetails(pickDetails)
                .backorders(backorders)
                .build();
    }

    /**
     * Backorder -> BackorderInfo 변환
     */
    private ShipmentOrderResponse.BackorderInfo mapBackorderToInfo(Backorder backorder) {
        return ShipmentOrderResponse.BackorderInfo.builder()
                .backorderId(backorder.getBackorderId())
                .productId(backorder.getProduct().getProductId())
                .productSku(backorder.getProduct().getSku())
                .shortageQty(backorder.getShortageQty())
                .status(backorder.getStatus().name())
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
import com.wms.exception.BusinessException;
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
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "To location not found"));

        // 2. 동일 로케이션 체크
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new BusinessException("SAME_LOCATION", "From and to locations must be different");
        }

        // 3. 출발지 재고 조회
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, fromLocation, request.getLotNumber())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Source inventory not found"));

        // 4. 이동 수량 체크
        if (request.getQuantity() > sourceInventory.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock at source location");
        }

        // 5. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "From location is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "To location is frozen for cycle count");
        }

        // 6. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED", "Destination location capacity exceeded");
        }

        // 7. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 8. HAZMAT 혼적 금지 체크
        validateHazmatCompatibility(product, toLocation);

        // 9. 유통기한 임박 상품 이동 제한
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateTransfer(sourceInventory, toLocation);
        }

        // 10. 대량 이동 승인 체크
        StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.immediate;
        double transferPct = (request.getQuantity() * 100.0) / sourceInventory.getQuantity();
        if (transferPct >= 80.0) {
            transferStatus = StockTransfer.TransferStatus.pending_approval;
        }

        // 11. StockTransfer 레코드 생성
        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .reason(request.getReason())
                .transferStatus(transferStatus)
                .transferredBy(request.getTransferredBy())
                .build();

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        // 12. 즉시 이동인 경우 재고 반영
        if (transferStatus == StockTransfer.TransferStatus.immediate) {
            performTransfer(savedTransfer, sourceInventory);
        }

        return mapToResponse(savedTransfer);
    }

    /**
     * 대량 이동 승인
     */
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        // 출발지 재고 재조회
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(
                        transfer.getProduct(),
                        transfer.getFromLocation(),
                        transfer.getLotNumber()
                )
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Source inventory not found"));

        // 재고 이동 실행
        performTransfer(transfer, sourceInventory);

        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        return mapToResponse(savedTransfer);
    }

    /**
     * 대량 이동 거부
     */
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        return mapToResponse(savedTransfer);
    }

    /**
     * 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Transfer not found"));

        return mapToResponse(transfer);
    }

    /**
     * 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        List<StockTransfer> transfers = stockTransferRepository.findAll();
        return transfers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 실제 재고 이동 수행
     */
    private void performTransfer(StockTransfer transfer, Inventory sourceInventory) {
        Product product = transfer.getProduct();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        int quantity = transfer.getQuantity();

        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 current_qty 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 있으면 증가, 없으면 생성)
        Inventory destInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, toLocation, transfer.getLotNumber())
                .orElse(Inventory.builder()
                        .product(product)
                        .location(toLocation)
                        .lotNumber(transfer.getLotNumber())
                        .quantity(0)
                        .expiryDate(sourceInventory.getExpiryDate())
                        .manufactureDate(sourceInventory.getManufactureDate())
                        .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지 (FIFO 보존)
                        .isExpired(sourceInventory.getIsExpired())
                        .build());

        destInventory.setQuantity(destInventory.getQuantity() + quantity);
        inventoryRepository.save(destInventory);

        // 4. 도착지 로케이션 current_qty 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone 기준)
        checkSafetyStockAfterTransfer(product);
    }

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = toLocation.getStorageType();

        // HAZMAT은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (toLocation.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("STORAGE_INCOMPATIBLE", "HAZMAT products must be moved to HAZMAT zone");
            }
            return;
        }

        // FROZEN 상품 → AMBIENT 로케이션 거부
        if (productType == Product.StorageType.FROZEN && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "FROZEN products cannot be moved to AMBIENT location");
        }

        // COLD 상품 → AMBIENT 로케이션 거부
        if (productType == Product.StorageType.COLD && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "COLD products cannot be moved to AMBIENT location");
        }

        // AMBIENT 상품 → COLD/FROZEN 허용 (상위 호환)
    }

    /**
     * HAZMAT 혼적 금지 검증
     */
    private void validateHazmatCompatibility(Product product, Location toLocation) {
        boolean isHazmat = (product.getCategory() == Product.ProductCategory.HAZMAT);

        // 도착지에 기존 재고가 있는지 확인
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        for (Inventory inv : existingInventories) {
            if (inv.getQuantity() > 0) {
                boolean existingIsHazmat = (inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT);

                // HAZMAT과 비-HAZMAT 혼적 금지
                if (isHazmat != existingIsHazmat) {
                    throw new BusinessException("HAZMAT_MIXING_FORBIDDEN",
                            "HAZMAT and non-HAZMAT products cannot be stored in the same location");
                }
            }
        }
    }

    /**
     * 유통기한 임박 상품 이동 제한 검증
     */
    private void validateExpiryDateTransfer(Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();

        if (expiryDate == null || manufactureDate == null) {
            return;
        }

        // 유통기한 만료 체크
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("EXPIRED_PRODUCT", "Expired products cannot be transferred");
        }

        // 잔여 유통기한 비율 계산
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return;
        }

        double remainingPct = (remainingDays * 100.0) / totalDays;

        // 잔여 유통기한 < 10% → SHIPPING zone만 허용
        if (remainingPct < 10) {
            if (toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("EXPIRY_TRANSFER_RESTRICTED",
                        "Products with less than 10% shelf life can only be moved to SHIPPING zone");
            }
        }
    }

    /**
     * 이동 후 안전재고 체크
     */
    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        List<Location> storageLocations = locationRepository.findByZone(Location.Zone.STORAGE);
        int totalStorageQty = 0;

        for (Location loc : storageLocations) {
            List<Inventory> inventories = inventoryRepository.findByProductAndLocation(product, loc);
            for (Inventory inv : inventories) {
                if (!inv.getIsExpired()) {
                    totalStorageQty += inv.getQuantity();
                }
            }
        }

        // 안전재고 규칙 조회
        safetyStockRuleRepository.findByProduct(product).ifPresent(rule -> {
            if (totalStorageQty <= rule.getMinQty()) {
                // 자동 재발주 요청 기록
                AutoReorderLog log = AutoReorderLog.builder()
                        .product(product)
                        .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                        .currentStock(totalStorageQty)
                        .minQty(rule.getMinQty())
                        .reorderQty(rule.getReorderQty())
                        .triggeredBy("SYSTEM")
                        .build();

                autoReorderLogRepository.save(log);
            }
        });
    }

    /**
     * Entity -> Response DTO 변환
     */
    private StockTransferResponse mapToResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .transferId(transfer.getTransferId())
                .productId(transfer.getProduct().getProductId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getLocationId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getLocationId())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .lotNumber(transfer.getLotNumber())
                .reason(transfer.getReason())
                .transferStatus(transfer.getTransferStatus().name())
                .transferredBy(transfer.getTransferredBy())
                .approvedBy(transfer.getApprovedBy())
                .transferredAt(transfer.getTransferredAt())
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
        jdbc:
          time_zone: UTC
    database-platform: org.hibernate.dialect.PostgreSQLDialect
  sql:
    init:
      mode: never

server:
  port: 8080


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- 1. 상품 마스터
CREATE TABLE IF NOT EXISTS products (
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

-- 2. 로케이션
CREATE TABLE IF NOT EXISTS locations (
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

-- 3. 재고
CREATE TABLE IF NOT EXISTS inventory (
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

-- 4. 공급업체
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 5. 공급업체 페널티
CREATE TABLE IF NOT EXISTS supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 6. 발주서
CREATE TABLE IF NOT EXISTS purchase_orders (
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

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    po_line_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    ordered_qty     INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty    INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    unit_price      NUMERIC(12,2),
    UNIQUE (po_id, product_id)
);

-- 7. 입고
CREATE TABLE IF NOT EXISTS inbound_receipts (
    receipt_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'inspecting'
                    CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_by     VARCHAR(100) NOT NULL,
    received_at     TIMESTAMPTZ DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inbound_receipt_lines (
    receipt_line_id UUID PRIMARY KEY,
    receipt_id      UUID NOT NULL REFERENCES inbound_receipts(receipt_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    manufacture_date DATE
);

-- 8. 출고 지시서
CREATE TABLE IF NOT EXISTS shipment_orders (
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

CREATE TABLE IF NOT EXISTS shipment_order_lines (
    shipment_line_id UUID PRIMARY KEY,
    shipment_id      UUID NOT NULL REFERENCES shipment_orders(shipment_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    requested_qty    INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty       INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'picked', 'partial', 'backordered'))
);

-- 9. 백오더
CREATE TABLE IF NOT EXISTS backorders (
    backorder_id     UUID PRIMARY KEY,
    shipment_line_id UUID NOT NULL REFERENCES shipment_order_lines(shipment_line_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    shortage_qty     INTEGER NOT NULL CHECK (shortage_qty > 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'open'
                     CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    fulfilled_at     TIMESTAMPTZ
);

-- 10. 재고 이동
CREATE TABLE IF NOT EXISTS stock_transfers (
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

-- 11. 재고 조정
CREATE TABLE IF NOT EXISTS inventory_adjustments (
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

-- 12. 감사 로그
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. 안전재고 기준
CREATE TABLE IF NOT EXISTS safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. 자동 재발주 이력
CREATE TABLE IF NOT EXISTS auto_reorder_logs (
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

-- 15. 계절 설정
CREATE TABLE IF NOT EXISTS seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. 실사 세션
CREATE TABLE IF NOT EXISTS cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

