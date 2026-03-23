# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 내용

### 1. DB 스키마
- `cycle_counts` 테이블: 실사 세션 관리
- `inventory_adjustments` 테이블: 재고 조정 이력 (삭제/수정 불가)

### 2. Entity
- `CycleCount.java`: src/main/java/com/wms/entity/CycleCount.java:1
- `InventoryAdjustment.java`: src/main/java/com/wms/entity/InventoryAdjustment.java:1

### 3. Repository
- `CycleCountRepository.java`: src/main/java/com/wms/repository/CycleCountRepository.java:1
- `InventoryAdjustmentRepository.java`: src/main/java/com/wms/repository/InventoryAdjustmentRepository.java:1

### 4. Service
- `InventoryAdjustmentService.java`: src/main/java/com/wms/service/InventoryAdjustmentService.java:1
  - 카테고리별 자동승인 임계치 (GENERAL 5%, FRESH 3%, HAZMAT 1%, HIGH_VALUE 2%)
  - HIGH_VALUE 전수 검증 (차이가 0이 아니면 무조건 승인 필요, audit_logs 기록)
  - 연속 조정 감시 (7일 내 2회 이상 시 승인 격상, [연속조정감시] 태그)
  - 실사 동결 (cycle_count 시작 시 is_frozen=true, 완료 시 해제)
  - 조정 후 안전재고 체크 → 자동 재발주

### 5. Controller
- `InventoryAdjustmentController.java`: src/main/java/com/wms/controller/InventoryAdjustmentController.java:1
  - POST /api/v1/cycle-counts (실사 시작)
  - POST /api/v1/cycle-counts/{id}/complete (실사 완료)
  - POST /api/v1/inventory-adjustments (조정 생성)
  - POST /api/v1/inventory-adjustments/{id}/approve (승인)
  - POST /api/v1/inventory-adjustments/{id}/reject (거부)
  - GET /api/v1/inventory-adjustments/{id} (상세 조회)
  - GET /api/v1/inventory-adjustments (목록 조회)

기존 코드 패턴과 일관성을 유지하며 요구사항의 모든 비즈니스 규칙을 구현했습니다.


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
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    public InboundReceiptController(InboundReceiptService inboundReceiptService) {
        this.inboundReceiptService = inboundReceiptService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam String reason) {
        try {
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InventoryAdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.*;
import com.wms.entity.CycleCount;
import com.wms.entity.InventoryAdjustment;
import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import com.wms.repository.InventoryRepository;
import com.wms.repository.LocationRepository;
import com.wms.repository.ProductRepository;
import com.wms.repository.InventoryAdjustmentRepository;
import com.wms.service.InventoryAdjustmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;

    public InventoryAdjustmentController(
            InventoryAdjustmentService inventoryAdjustmentService,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            ProductRepository productRepository) {
        this.inventoryAdjustmentService = inventoryAdjustmentService;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
    }

    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @RequestBody CycleCountRequest request) {
        try {
            CycleCount cycleCount = inventoryAdjustmentService.startCycleCount(
                    request.getLocationId(),
                    request.getProductId(),
                    request.getStartedBy()
            );

            CycleCountResponse response = buildCycleCountResponse(cycleCount);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable UUID id,
            @RequestBody CycleCountCompleteRequest request) {
        try {
            CycleCount cycleCount = inventoryAdjustmentService.completeCycleCount(
                    id,
                    request.getActualQty(),
                    request.getReason()
            );

            CycleCountResponse response = buildCycleCountResponse(cycleCount);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @RequestBody InventoryAdjustmentRequest request) {
        try {
            Inventory inventory = request.getInventoryId() != null
                    ? inventoryRepository.findById(request.getInventoryId()).orElse(null)
                    : null;

            Location location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));

            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            int differenceQty = request.getActualQty() - request.getSystemQty();

            InventoryAdjustment adjustment = inventoryAdjustmentService.createInventoryAdjustment(
                    inventory,
                    location,
                    product,
                    request.getSystemQty(),
                    request.getActualQty(),
                    differenceQty,
                    request.getReason()
            );

            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody InventoryAdjustmentApprovalRequest request) {
        try {
            InventoryAdjustment adjustment = inventoryAdjustmentService.approveAdjustment(
                    id,
                    request.getApprovedBy()
            );

            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable UUID id,
            @RequestBody InventoryAdjustmentRejectionRequest request) {
        try {
            InventoryAdjustment adjustment = inventoryAdjustmentService.rejectAdjustment(
                    id,
                    request.getRejectionReason()
            );

            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable UUID id) {
        try {
            InventoryAdjustment adjustment = inventoryAdjustmentService.getAdjustment(id);
            InventoryAdjustmentResponse response = buildAdjustmentResponse(adjustment);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAllAdjustments() {
        try {
            List<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findAll();
            List<InventoryAdjustmentResponse> responses = adjustments.stream()
                    .map(this::buildAdjustmentResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // === Helper Methods ===

    private CycleCountResponse buildCycleCountResponse(CycleCount cycleCount) {
        CycleCountResponse response = new CycleCountResponse();
        response.setId(cycleCount.getId());
        response.setLocationId(cycleCount.getLocation().getId());
        response.setProductId(cycleCount.getProduct().getId());
        response.setStatus(cycleCount.getStatus().name());
        response.setStartedBy(cycleCount.getStartedBy());
        response.setStartedAt(cycleCount.getStartedAt());
        response.setCompletedAt(cycleCount.getCompletedAt());
        return response;
    }

    private InventoryAdjustmentResponse buildAdjustmentResponse(InventoryAdjustment adjustment) {
        InventoryAdjustmentResponse response = new InventoryAdjustmentResponse();
        response.setId(adjustment.getId());
        response.setInventoryId(adjustment.getInventory() != null ? adjustment.getInventory().getId() : null);
        response.setLocationId(adjustment.getLocation().getId());
        response.setProductId(adjustment.getProduct().getId());
        response.setSystemQty(adjustment.getSystemQty());
        response.setActualQty(adjustment.getActualQty());
        response.setDifferenceQty(adjustment.getDifferenceQty());
        response.setReason(adjustment.getReason());
        response.setApprovalStatus(adjustment.getApprovalStatus().name());
        response.setApprovedBy(adjustment.getApprovedBy());
        response.setApprovedAt(adjustment.getApprovedAt());
        response.setRejectionReason(adjustment.getRejectionReason());
        response.setCreatedAt(adjustment.getCreatedAt());
        return response;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    public ShipmentOrderController(ShipmentOrderService shipmentOrderService) {
        this.shipmentOrderService = shipmentOrderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipmentOrder(@PathVariable UUID id) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.pickShipmentOrder(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipmentOrder(@PathVariable UUID id) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.shipShipmentOrder(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(@PathVariable UUID id) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        try {
            StockTransferResponse response = stockTransferService.createStockTransfer(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "admin") String approvedBy) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestParam String reason) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(id, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
            @PathVariable UUID id) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        try {
            List<StockTransferResponse> responses = stockTransferService.getAllTransfers();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApiResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private ErrorInfo error;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, new ErrorInfo(message));
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, null, new ErrorInfo(message, code));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String message;
        private String code;

        public ErrorInfo(String message) {
            this.message = message;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountCompleteRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

@Data
public class CycleCountCompleteRequest {
    private Integer actualQty;
    private String reason;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CycleCountRequest {
    private UUID locationId;
    private UUID productId;
    private String startedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountResponse.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CycleCountResponse {
    private UUID id;
    private UUID locationId;
    private UUID productId;
    private String status;
    private String startedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptLineRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptLineRequest {

    @NotNull
    private UUID purchaseOrderLineId;

    @NotNull
    private UUID productId;

    @NotNull
    private UUID locationId;

    @NotNull
    @Positive
    private Integer quantity;

    private String lotNumber;

    private LocalDate manufactureDate;

    private LocalDate expiryDate;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptRequest {

    @NotNull
    private String receiptNumber;

    @NotNull
    private UUID purchaseOrderId;

    @NotNull
    private OffsetDateTime receivedAt;

    @NotNull
    private List<InboundReceiptLineRequest> lines;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptResponse {

    private UUID id;
    private String receiptNumber;
    private UUID purchaseOrderId;
    private String status;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private String rejectionReason;
    private List<InboundReceiptLineResponse> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundReceiptLineResponse {
        private UUID id;
        private UUID purchaseOrderLineId;
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentApprovalRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

@Data
public class InventoryAdjustmentApprovalRequest {
    private String approvedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRejectionRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

@Data
public class InventoryAdjustmentRejectionRequest {
    private String rejectionReason;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class InventoryAdjustmentRequest {
    private UUID inventoryId;
    private UUID locationId;
    private UUID productId;
    private Integer systemQty;
    private Integer actualQty;
    private String reason;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentResponse.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class InventoryAdjustmentResponse {
    private UUID id;
    private UUID inventoryId;
    private UUID locationId;
    private UUID productId;
    private Integer systemQty;
    private Integer actualQty;
    private Integer differenceQty;
    private String reason;
    private String approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String rejectionReason;
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderLineRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ShipmentOrderLineRequest {
    private UUID productId;
    private Integer requestedQuantity;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShipmentOrderRequest {
    private String orderNumber;
    private String customerName;
    private List<ShipmentOrderLineRequest> lines;
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ShipmentOrderResponse {
    private UUID id;
    private String orderNumber;
    private String customerName;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Data
    public static class ShipmentOrderLineResponse {
        private UUID id;
        private UUID productId;
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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferRequest {
    private UUID inventoryId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.StockTransfer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferResponse {
    private UUID id;
    private UUID inventoryId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String transferStatus;
    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private String approvedBy;
    private String rejectionReason;

    public static StockTransferResponse from(StockTransfer transfer) {
        return new StockTransferResponse(
            transfer.getId(),
            transfer.getInventory().getId(),
            transfer.getFromLocation().getId(),
            transfer.getToLocation().getId(),
            transfer.getQuantity(),
            transfer.getTransferStatus().name(),
            transfer.getRequestedAt(),
            transfer.getApprovedAt(),
            transfer.getApprovedBy(),
            transfer.getRejectionReason()
        );
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50, name = "entity_type")
    private String entityType;

    @Column(nullable = false, name = "entity_id")
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100, name = "user_id")
    private String userId;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoReorderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, name = "trigger_reason")
    private TriggerReason triggerReason;

    @Column(nullable = false, name = "reorder_qty")
    private Integer reorderQty;

    @Column(nullable = false, name = "current_stock")
    private Integer currentStock;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum TriggerReason {
        SAFETY_STOCK_TRIGGER, EMERGENCY_REORDER
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleCountStatus status = CycleCountStatus.in_progress;

    @Column(name = "started_by", length = 100)
    private String startedBy;

    @Column(nullable = false, name = "started_at")
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum CycleCountStatus {
        in_progress, completed
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50, name = "receipt_number")
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceiptStatus status = ReceiptStatus.inspecting;

    @Column(nullable = false, name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number", "expiry_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(nullable = false)
    private Boolean expired = false;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}


============================================================
// FILE: src\main\java\com\wms\entity\InventoryAdjustment.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, name = "system_qty")
    private Integer systemQty;

    @Column(nullable = false, name = "actual_qty")
    private Integer actualQty;

    @Column(nullable = false, name = "difference_qty")
    private Integer differenceQty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.pending;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum ApprovalStatus {
        auto_approved, pending, approved, rejected
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "storage_type")
    private Product.StorageType storageType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, name = "current_quantity")
    private Integer currentQuantity = 0;

    @Column(nullable = false, name = "is_frozen")
    private Boolean isFrozen = false;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Column(nullable = false, length = 10)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "storage_type")
    private StorageType storageType;

    @Column(nullable = false, name = "requires_expiry_tracking")
    private Boolean requiresExpiryTracking = false;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50, name = "po_number")
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "po_type")
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoStatus status = PoStatus.pending;

    @Column(nullable = false, name = "ordered_at")
    private OffsetDateTime orderedAt;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, name = "ordered_quantity")
    private Integer orderedQuantity;

    @Column(nullable = false, name = "received_quantity")
    private Integer receivedQuantity = 0;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}


============================================================
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SafetyStockRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false, name = "min_qty")
    private Integer minQty;

    @Column(nullable = false, name = "reorder_qty")
    private Integer reorderQty;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
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
public class SeasonalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, name = "season_name")
    private String seasonName;

    @Column(nullable = false, name = "start_date")
    private LocalDate startDate;

    @Column(nullable = false, name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier = BigDecimal.valueOf(1.5);

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50, name = "order_number")
    private String orderNumber;

    @Column(nullable = false, name = "customer_name")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.pending;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, name = "requested_quantity")
    private Integer requestedQuantity;

    @Column(nullable = false, name = "picked_quantity")
    private Integer pickedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status = LineStatus.pending;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    @Column(nullable = false, length = 30, name = "transfer_status")
    private TransferStatus transferStatus = TransferStatus.immediate;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, name = "penalty_type")
    private PenaltyType penaltyType;

    @Column(nullable = false, name = "occurred_at")
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    @Query("SELECT cc FROM CycleCount cc WHERE cc.location.id = :locationId AND cc.status = 'in_progress'")
    Optional<CycleCount> findInProgressByLocationId(@Param("locationId") UUID locationId);

    @Query("SELECT cc FROM CycleCount cc WHERE cc.location.id = :locationId AND cc.product.id = :productId AND cc.status = 'in_progress'")
    Optional<CycleCount> findInProgressByLocationAndProduct(@Param("locationId") UUID locationId, @Param("productId") UUID productId);

    List<CycleCount> findByLocationId(UUID locationId);
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

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.location.id = :locationId AND ia.product.id = :productId AND ia.createdAt >= :since")
    List<InventoryAdjustment> findRecentAdjustments(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId,
            @Param("since") OffsetDateTime since);

    @Query("SELECT COUNT(ia) FROM InventoryAdjustment ia WHERE ia.location.id = :locationId AND ia.product.id = :productId AND ia.createdAt >= :since")
    long countRecentAdjustments(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId,
            @Param("since") OffsetDateTime since);

    List<InventoryAdjustment> findByProductId(UUID productId);

    List<InventoryAdjustment> findByLocationId(UUID locationId);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Modifying
    @Query("UPDATE PurchaseOrder po SET po.status = 'hold' WHERE po.supplier.id = :supplierId AND po.status = 'pending'")
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

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.id = :supplierId AND sp.occurredAt >= :since")
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
// FILE: src\main\java\com\wms\service\InboundReceiptService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptLineRequest;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    public InboundReceiptService(
            InboundReceiptRepository inboundReceiptRepository,
            InboundReceiptLineRepository inboundReceiptLineRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderLineRepository purchaseOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SupplierPenaltyRepository supplierPenaltyRepository,
            SeasonalConfigRepository seasonalConfigRepository) {
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.inboundReceiptLineRepository = inboundReceiptLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.supplierPenaltyRepository = supplierPenaltyRepository;
        this.seasonalConfigRepository = seasonalConfigRepository;
    }

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new IllegalArgumentException("PO not found"));

        // 2. 입고 전표 생성 (inspecting 상태)
        InboundReceipt receipt = new InboundReceipt();
        receipt.setReceiptNumber(request.getReceiptNumber());
        receipt.setPurchaseOrder(po);
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receipt.setReceivedAt(request.getReceivedAt());
        receipt = inboundReceiptRepository.save(receipt);

        // 3. 입고 라인 검증 및 생성
        List<InboundReceiptLine> lines = new ArrayList<>();
        boolean needsApproval = false;

        for (InboundReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new IllegalArgumentException("PO Line not found"));

            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));

            // 3-1. 실사 동결 로케이션 체크
            if (location.getIsFrozen()) {
                throw new IllegalArgumentException("Location is frozen for cycle count");
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 검증
            if (product.getRequiresExpiryTracking()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("Expiry date is required for product: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("Manufacture date is required for product: " + product.getSku());
                }

                // 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        LocalDate.now()
                );

                Integer minPct = product.getMinRemainingShelfLifePct() != null
                        ? product.getMinRemainingShelfLifePct()
                        : 30;

                if (remainingPct < minPct) {
                    // 페널티 기록 및 거부
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "Shelf life remaining: " + remainingPct + "%, minimum: " + minPct + "%");
                    throw new IllegalArgumentException("Shelf life remaining is too short: " + remainingPct + "%");
                }

                if (remainingPct >= minPct && remainingPct <= 50) {
                    needsApproval = true;
                }
            }

            // 3-4. 초과입고 체크
            int allowedQty = calculateAllowedQuantity(poLine, po, product);
            int totalReceived = poLine.getReceivedQuantity() + lineReq.getQuantity();

            if (totalReceived > allowedQty) {
                // 페널티 기록 및 거부
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        "Received: " + totalReceived + ", Allowed: " + allowedQty);
                throw new IllegalArgumentException("Over delivery detected. Allowed: " + allowedQty +
                        ", Trying to receive: " + totalReceived);
            }

            // 3-5. 로케이션 용량 체크
            if (location.getCurrentQuantity() + lineReq.getQuantity() > location.getCapacity()) {
                throw new IllegalArgumentException("Location capacity exceeded");
            }

            // 입고 라인 생성
            InboundReceiptLine line = new InboundReceiptLine();
            line.setInboundReceipt(receipt);
            line.setPurchaseOrderLine(poLine);
            line.setProduct(product);
            line.setLocation(location);
            line.setQuantity(lineReq.getQuantity());
            line.setLotNumber(lineReq.getLotNumber());
            line.setManufactureDate(lineReq.getManufactureDate());
            line.setExpiryDate(lineReq.getExpiryDate());

            lines.add(inboundReceiptLineRepository.save(line));
        }

        // 4. 유통기한 경고가 있으면 pending_approval 상태로 변경
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
            receipt = inboundReceiptRepository.save(receipt);
        }

        return buildResponse(receipt, lines);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting) {
            throw new IllegalArgumentException("Receipt is not in inspecting status");
        }

        // 1. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Inventory inventory = new Inventory();
            inventory.setProduct(line.getProduct());
            inventory.setLocation(line.getLocation());
            inventory.setQuantity(line.getQuantity());
            inventory.setLotNumber(line.getLotNumber());
            inventory.setManufactureDate(line.getManufactureDate());
            inventory.setExpiryDate(line.getExpiryDate());
            inventory.setReceivedAt(receipt.getReceivedAt());
            inventoryRepository.save(inventory);

            // 로케이션 현재 수량 업데이트
            Location location = line.getLocation();
            location.setCurrentQuantity(location.getCurrentQuantity() + line.getQuantity());
            location.setUpdatedAt(OffsetDateTime.now());
            locationRepository.save(location);

            // PO Line 입고 수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() + line.getQuantity());
            poLine.setUpdatedAt(OffsetDateTime.now());
            purchaseOrderLineRepository.save(poLine);
        }

        // 2. PO 상태 업데이트
        PurchaseOrder po = receipt.getPurchaseOrder();
        List<PurchaseOrderLine> allPoLines = purchaseOrderLineRepository.findByPurchaseOrderId(po.getId());

        boolean allFullyReceived = allPoLines.stream()
                .allMatch(line -> line.getReceivedQuantity() >= line.getOrderedQuantity());
        boolean anyPartiallyReceived = allPoLines.stream()
                .anyMatch(line -> line.getReceivedQuantity() > 0 && line.getReceivedQuantity() < line.getOrderedQuantity());

        if (allFullyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyPartiallyReceived || allPoLines.stream().anyMatch(line -> line.getReceivedQuantity() > 0)) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        po.setUpdatedAt(OffsetDateTime.now());
        purchaseOrderRepository.save(po);

        // 3. 입고 확정
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = inboundReceiptRepository.save(receipt);

        return buildResponse(receipt, lines);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID id, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receipt.setRejectionReason(reason);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = inboundReceiptRepository.save(receipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        return buildResponse(receipt, lines);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalArgumentException("Receipt is not pending approval");
        }

        // pending_approval -> inspecting으로 변경하여 이후 confirm 가능하도록
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = inboundReceiptRepository.save(receipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        return buildResponse(receipt, lines);
    }

    public InboundReceiptResponse getInboundReceipt(UUID id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);
        return buildResponse(receipt, lines);
    }

    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(receipt -> {
                    List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(receipt.getId());
                    return buildResponse(receipt, lines);
                })
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT은 HAZMAT zone만 허용
        if (productType == Product.StorageType.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new IllegalArgumentException("HAZMAT products must be stored in HAZMAT zone");
        }

        // FROZEN 상품은 FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new IllegalArgumentException("FROZEN products require FROZEN storage");
        }

        // COLD 상품은 COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
                locationType != Product.StorageType.COLD &&
                locationType != Product.StorageType.FROZEN) {
            throw new IllegalArgumentException("COLD products require COLD or FROZEN storage");
        }

        // AMBIENT 상품은 AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("AMBIENT products require AMBIENT storage");
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private int calculateAllowedQuantity(PurchaseOrderLine poLine, PurchaseOrder po, Product product) {
        int orderedQty = poLine.getOrderedQuantity();

        // 카테고리별 기본 허용률
        double categoryRate = switch (product.getCategory()) {
            case GENERAL -> 0.10;
            case FRESH -> 0.05;
            case HAZMAT -> 0.00;
            case HIGH_VALUE -> 0.03;
        };

        // HAZMAT은 무조건 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            return orderedQty;
        }

        // PO 타입별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 성수기 가중치
        double seasonalMultiplier = 1.0;
        var seasonOpt = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now());
        if (seasonOpt.isPresent()) {
            seasonalMultiplier = seasonOpt.get().getMultiplier().doubleValue();
        }

        // 최종 허용률 계산
        double finalRate = categoryRate * poTypeMultiplier * seasonalMultiplier;

        return orderedQty + (int) (orderedQty * finalRate);
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType type, String description) {
        SupplierPenalty penalty = new SupplierPenalty();
        penalty.setSupplier(supplier);
        penalty.setPenaltyType(type);
        penalty.setDescription(description);
        penalty.setOccurredAt(OffsetDateTime.now());
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상이면 PO hold
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            purchaseOrderRepository.holdPendingOrdersBySupplierId(supplier.getId());
        }
    }

    private InboundReceiptResponse buildResponse(InboundReceipt receipt, List<InboundReceiptLine> lines) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setId(receipt.getId());
        response.setReceiptNumber(receipt.getReceiptNumber());
        response.setPurchaseOrderId(receipt.getPurchaseOrder().getId());
        response.setStatus(receipt.getStatus().name());
        response.setReceivedAt(receipt.getReceivedAt());
        response.setConfirmedAt(receipt.getConfirmedAt());
        response.setRejectionReason(receipt.getRejectionReason());

        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    InboundReceiptResponse.InboundReceiptLineResponse lineResp =
                            new InboundReceiptResponse.InboundReceiptLineResponse();
                    lineResp.setId(line.getId());
                    lineResp.setPurchaseOrderLineId(line.getPurchaseOrderLine().getId());
                    lineResp.setProductId(line.getProduct().getId());
                    lineResp.setLocationId(line.getLocation().getId());
                    lineResp.setQuantity(line.getQuantity());
                    lineResp.setLotNumber(line.getLotNumber());
                    lineResp.setManufactureDate(line.getManufactureDate());
                    lineResp.setExpiryDate(line.getExpiryDate());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);
        return response;
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InventoryAdjustmentService.java
============================================================
package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public InventoryAdjustmentService(
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            CycleCountRepository cycleCountRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            ProductRepository productRepository,
            AuditLogRepository auditLogRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.cycleCountRepository = cycleCountRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.auditLogRepository = auditLogRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public CycleCount startCycleCount(UUID locationId, UUID productId, String startedBy) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // 1. 동일 로케이션+상품에 대해 이미 진행 중인 실사가 있는지 확인
        var existingCount = cycleCountRepository.findInProgressByLocationAndProduct(locationId, productId);
        if (existingCount.isPresent()) {
            throw new IllegalArgumentException("Cycle count already in progress for this location and product");
        }

        // 2. 로케이션 동결
        location.setIsFrozen(true);
        location.setUpdatedAt(OffsetDateTime.now());
        locationRepository.save(location);

        // 3. 실사 시작
        CycleCount cycleCount = new CycleCount();
        cycleCount.setLocation(location);
        cycleCount.setProduct(product);
        cycleCount.setStatus(CycleCount.CycleCountStatus.in_progress);
        cycleCount.setStartedBy(startedBy);
        cycleCount.setStartedAt(OffsetDateTime.now());

        return cycleCountRepository.save(cycleCount);
    }

    @Transactional
    public CycleCount completeCycleCount(UUID cycleCountId, Integer actualQty, String reason) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new IllegalArgumentException("Cycle count not found"));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.in_progress) {
            throw new IllegalArgumentException("Cycle count is not in progress");
        }

        // 1. 재고 조회 (해당 로케이션+상품)
        Inventory inventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(cycleCount.getLocation().getId()) &&
                        inv.getProduct().getId().equals(cycleCount.getProduct().getId()))
                .findFirst()
                .orElse(null);

        int systemQty = inventory != null ? inventory.getQuantity() : 0;
        int differenceQty = actualQty - systemQty;

        // 2. 차이가 있으면 재고 조정 생성
        if (differenceQty != 0) {
            createInventoryAdjustment(inventory, cycleCount.getLocation(), cycleCount.getProduct(),
                    systemQty, actualQty, differenceQty, reason);
        }

        // 3. 실사 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.completed);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCount.setUpdatedAt(OffsetDateTime.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        // 4. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        location.setUpdatedAt(OffsetDateTime.now());
        locationRepository.save(location);

        return cycleCount;
    }

    @Transactional
    public InventoryAdjustment createInventoryAdjustment(Inventory inventory, Location location, Product product,
                                                          int systemQty, int actualQty, int differenceQty, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required");
        }

        // 1. 연속 조정 감시 (최근 7일 내 2회 이상)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        long recentAdjustmentCount = inventoryAdjustmentRepository.countRecentAdjustments(
                location.getId(), product.getId(), sevenDaysAgo);

        boolean forcePendingApproval = recentAdjustmentCount >= 2;
        String adjustedReason = reason;
        if (forcePendingApproval) {
            adjustedReason = "[연속조정감시] " + reason;
        }

        // 2. 시스템 재고가 0인데 실물이 발견된 경우 무조건 승인 필요
        if (systemQty == 0 && actualQty > 0) {
            forcePendingApproval = true;
        }

        // 3. 카테고리별 자동승인 임계치 계산
        double threshold = switch (product.getCategory()) {
            case GENERAL -> 0.05;
            case FRESH -> 0.03;
            case HAZMAT -> 0.01;
            case HIGH_VALUE -> 0.02;
        };

        double diffPct = systemQty == 0 ? 1.0 : Math.abs((double) differenceQty / systemQty);

        // 4. HIGH_VALUE는 차이가 0이 아닌 모든 경우 관리자 승인 필요
        InventoryAdjustment.ApprovalStatus approvalStatus;
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && differenceQty != 0) {
            approvalStatus = InventoryAdjustment.ApprovalStatus.pending;
        } else if (forcePendingApproval || diffPct > threshold) {
            approvalStatus = InventoryAdjustment.ApprovalStatus.pending;
        } else {
            approvalStatus = InventoryAdjustment.ApprovalStatus.auto_approved;
        }

        // 5. 재고 조정 생성
        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setInventory(inventory);
        adjustment.setLocation(location);
        adjustment.setProduct(product);
        adjustment.setSystemQty(systemQty);
        adjustment.setActualQty(actualQty);
        adjustment.setDifferenceQty(differenceQty);
        adjustment.setReason(adjustedReason);
        adjustment.setApprovalStatus(approvalStatus);

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // 6. 자동 승인인 경우 즉시 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.auto_approved) {
            applyAdjustment(adjustment);
        }

        return adjustment;
    }

    @Transactional
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.pending) {
            throw new IllegalArgumentException("Adjustment is not pending approval");
        }

        // 1. 승인 상태로 변경
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.approved);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment.setUpdatedAt(OffsetDateTime.now());
        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // 2. 재고 반영
        applyAdjustment(adjustment);

        // 3. HIGH_VALUE인 경우 감사 로그 기록
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = new AuditLog();
            auditLog.setEntityType("InventoryAdjustment");
            auditLog.setEntityId(adjustment.getId());
            auditLog.setAction("APPROVED");
            auditLog.setDescription("HIGH_VALUE adjustment approved. System: " +
                    adjustment.getSystemQty() + " -> Actual: " + adjustment.getActualQty());
            auditLog.setUserId(approvedBy);
            auditLogRepository.save(auditLog);
        }

        return adjustment;
    }

    @Transactional
    public InventoryAdjustment rejectAdjustment(UUID adjustmentId, String rejectionReason) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.pending) {
            throw new IllegalArgumentException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.rejected);
        adjustment.setRejectionReason(rejectionReason);
        adjustment.setUpdatedAt(OffsetDateTime.now());

        return inventoryAdjustmentRepository.save(adjustment);
    }

    public InventoryAdjustment getAdjustment(UUID adjustmentId) {
        return inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));
    }

    // === Helper Methods ===

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Inventory inventory = adjustment.getInventory();
        Location location = adjustment.getLocation();
        Product product = adjustment.getProduct();

        // 1. 재고가 없었는데 새로 생긴 경우
        if (inventory == null && adjustment.getActualQty() > 0) {
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(location);
            newInventory.setQuantity(adjustment.getActualQty());
            newInventory.setReceivedAt(OffsetDateTime.now());
            inventoryRepository.save(newInventory);

            location.setCurrentQuantity(location.getCurrentQuantity() + adjustment.getActualQty());
        } else if (inventory != null) {
            // 2. 재고 조정 반영
            int oldQty = inventory.getQuantity();
            int newQty = adjustment.getActualQty();

            inventory.setQuantity(newQty);
            inventory.setUpdatedAt(OffsetDateTime.now());
            inventoryRepository.save(inventory);

            // 로케이션 수량 조정
            location.setCurrentQuantity(location.getCurrentQuantity() - oldQty + newQty);
        }

        location.setUpdatedAt(OffsetDateTime.now());
        locationRepository.save(location);

        // 3. 안전재고 체크
        checkSafetyStock(product);
    }

    private void checkSafetyStock(Product product) {
        var safetyStockRuleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        if (safetyStockRuleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = safetyStockRuleOpt.get();

        // 전체 가용 재고 계산 (expired=false인 것만)
        int totalAvailableStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()) && !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주
        if (totalAvailableStock <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER);
            log.setReorderQty(rule.getReorderQty());
            log.setCurrentStock(totalAvailableStock);
            autoReorderLogRepository.save(log);
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderLineRequest;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
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

    public ShipmentOrderService(
            ShipmentOrderRepository shipmentOrderRepository,
            ShipmentOrderLineRepository shipmentOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            BackorderRepository backorderRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository,
            AuditLogRepository auditLogRepository) {
        this.shipmentOrderRepository = shipmentOrderRepository;
        this.shipmentOrderLineRepository = shipmentOrderLineRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.backorderRepository = backorderRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // HAZMAT과 FRESH 분리 여부 체크
        List<ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 비-HAZMAT이 함께 있으면 분리
        if (!hazmatLines.isEmpty() && !nonHazmatLines.isEmpty()) {
            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder nonHazmatOrder = createShipmentOrderInternal(
                    request.getOrderNumber(),
                    request.getCustomerName(),
                    nonHazmatLines
            );

            // HAZMAT 출고 지시서 생성 (별도)
            ShipmentOrder hazmatOrder = createShipmentOrderInternal(
                    request.getOrderNumber() + "-HAZMAT",
                    request.getCustomerName(),
                    hazmatLines
            );

            // 비-HAZMAT 출고 지시서를 기본으로 반환
            List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(nonHazmatOrder.getId());
            return buildResponse(nonHazmatOrder, lines);
        } else {
            // 분리 불필요, 일반 생성
            ShipmentOrder order = createShipmentOrderInternal(
                    request.getOrderNumber(),
                    request.getCustomerName(),
                    request.getLines()
            );

            List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(order.getId());
            return buildResponse(order, lines);
        }
    }

    private ShipmentOrder createShipmentOrderInternal(String orderNumber, String customerName,
                                                      List<ShipmentOrderLineRequest> lineRequests) {
        ShipmentOrder order = new ShipmentOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerName(customerName);
        order.setStatus(ShipmentOrder.ShipmentStatus.pending);
        order = shipmentOrderRepository.save(order);

        for (ShipmentOrderLineRequest lineReq : lineRequests) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            ShipmentOrderLine line = new ShipmentOrderLine();
            line.setShipmentOrder(order);
            line.setProduct(product);
            line.setRequestedQuantity(lineReq.getRequestedQuantity());
            line.setPickedQuantity(0);
            line.setStatus(ShipmentOrderLine.LineStatus.pending);
            shipmentOrderLineRepository.save(line);
        }

        return order;
    }

    @Transactional
    public ShipmentOrderResponse pickShipmentOrder(UUID id) {
        ShipmentOrder order = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new IllegalArgumentException("Order is not in pending status");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.picking);
        order = shipmentOrderRepository.save(order);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);

        for (ShipmentOrderLine line : lines) {
            Product product = line.getProduct();
            int requestedQty = line.getRequestedQuantity();

            // HAZMAT 상품인 경우 max_pick_qty 체크
            if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
                if (requestedQty > product.getMaxPickQty()) {
                    throw new IllegalArgumentException("HAZMAT product exceeds max pick quantity: " + product.getSku());
                }
            }

            // 피킹 가능한 재고 조회 (FIFO/FEFO)
            List<Inventory> availableInventory = getAvailableInventoryForPicking(product);

            int remainingToPick = requestedQty;
            int totalPicked = 0;

            for (Inventory inv : availableInventory) {
                if (remainingToPick <= 0) break;

                // 실사 동결된 로케이션은 피킹 불가
                if (inv.getLocation().getIsFrozen()) {
                    continue;
                }

                int pickQty = Math.min(inv.getQuantity(), remainingToPick);

                // 재고 차감
                inv.setQuantity(inv.getQuantity() - pickQty);
                inv.setUpdatedAt(OffsetDateTime.now());
                inventoryRepository.save(inv);

                // 로케이션 현재 수량 감소
                Location location = inv.getLocation();
                location.setCurrentQuantity(location.getCurrentQuantity() - pickQty);
                location.setUpdatedAt(OffsetDateTime.now());
                locationRepository.save(location);

                // 보관 유형 불일치 경고
                if (product.getStorageType() != location.getStorageType()) {
                    createAuditLog("SHIPMENT_PICK", order.getId(),
                            "Storage type mismatch: Product " + product.getSku() +
                                    " (" + product.getStorageType() + ") picked from location " +
                                    location.getCode() + " (" + location.getStorageType() + ")");
                }

                totalPicked += pickQty;
                remainingToPick -= pickQty;
            }

            // 부분출고 의사결정 트리
            double fulfillmentRate = (double) totalPicked / requestedQty;

            if (fulfillmentRate >= 0.70) {
                // 70% 이상: 부분출고 + 백오더
                line.setPickedQuantity(totalPicked);
                if (totalPicked >= requestedQty) {
                    line.setStatus(ShipmentOrderLine.LineStatus.picked);
                } else {
                    line.setStatus(ShipmentOrderLine.LineStatus.partial);
                    createBackorder(line, requestedQty - totalPicked);
                }
            } else if (fulfillmentRate >= 0.30) {
                // 30% ~ 70%: 부분출고 + 백오더 + 긴급발주
                line.setPickedQuantity(totalPicked);
                line.setStatus(ShipmentOrderLine.LineStatus.partial);
                createBackorder(line, requestedQty - totalPicked);
                createEmergencyReorder(product, totalPicked);
            } else {
                // 30% 미만: 전량 백오더
                line.setPickedQuantity(0);
                line.setStatus(ShipmentOrderLine.LineStatus.backordered);
                createBackorder(line, requestedQty);
            }

            line.setUpdatedAt(OffsetDateTime.now());
            shipmentOrderLineRepository.save(line);
        }

        // 출고 지시서 상태 업데이트
        lines = shipmentOrderLineRepository.findByShipmentOrderId(id);
        boolean allPicked = lines.stream().allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.picked);
        boolean anyPartial = lines.stream().anyMatch(l ->
                l.getStatus() == ShipmentOrderLine.LineStatus.partial ||
                        l.getStatus() == ShipmentOrderLine.LineStatus.backordered);

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.picking);
        } else if (anyPartial) {
            order.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }

        order.setUpdatedAt(OffsetDateTime.now());
        order = shipmentOrderRepository.save(order);

        return buildResponse(order, lines);
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID id) {
        ShipmentOrder order = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found"));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
                order.getStatus() != ShipmentOrder.ShipmentStatus.partial) {
            throw new IllegalArgumentException("Order is not ready for shipping");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        order.setShippedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order = shipmentOrderRepository.save(order);

        // 출고 후 안전재고 체크
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProduct());
        }

        return buildResponse(order, lines);
    }

    public ShipmentOrderResponse getShipmentOrder(UUID id) {
        ShipmentOrder order = shipmentOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(id);
        return buildResponse(order, lines);
    }

    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> orders = shipmentOrderRepository.findAll();
        return orders.stream()
                .map(order -> {
                    List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderId(order.getId());
                    return buildResponse(order, lines);
                })
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private List<Inventory> getAvailableInventoryForPicking(Product product) {
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getExpired())
                .collect(Collectors.toList());

        // 유통기한이 지난 재고 제외
        LocalDate today = LocalDate.now();
        allInventory = allInventory.stream()
                .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        // 잔여율 < 10% 재고 폐기 전환
        for (Inventory inv : allInventory) {
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                        inv.getManufactureDate(),
                        inv.getExpiryDate(),
                        today
                );

                if (remainingPct < 10) {
                    inv.setExpired(true);
                    inv.setUpdatedAt(OffsetDateTime.now());
                    inventoryRepository.save(inv);
                }
            }
        }

        // expired 재고 필터링
        allInventory = allInventory.stream()
                .filter(inv -> !inv.getExpired())
                .collect(Collectors.toList());

        // HAZMAT은 HAZMAT zone에서만 피킹
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            allInventory = allInventory.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 유통기한 관리 상품: FEFO + 잔여율 우선순위
        if (product.getRequiresExpiryTracking()) {
            allInventory.sort((inv1, inv2) -> {
                LocalDate exp1 = inv1.getExpiryDate();
                LocalDate exp2 = inv2.getExpiryDate();

                if (exp1 == null && exp2 == null) {
                    return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
                }
                if (exp1 == null) return 1;
                if (exp2 == null) return -1;

                // 잔여율 계산
                double rem1 = calculateRemainingShelfLifePct(inv1.getManufactureDate(), exp1, today);
                double rem2 = calculateRemainingShelfLifePct(inv2.getManufactureDate(), exp2, today);

                // 잔여율 < 30%인 재고 최우선
                boolean priority1 = rem1 < 30;
                boolean priority2 = rem2 < 30;

                if (priority1 && !priority2) return -1;
                if (!priority1 && priority2) return 1;

                // 동일 우선순위에서는 유통기한 빠른 순
                int expCompare = exp1.compareTo(exp2);
                if (expCompare != 0) return expCompare;

                // 유통기한 같으면 FIFO
                return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
            });
        } else {
            // 일반 상품: FIFO
            allInventory.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return allInventory;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            return 100;
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void createBackorder(ShipmentOrderLine line, int quantity) {
        Backorder backorder = new Backorder();
        backorder.setShipmentOrderLine(line);
        backorder.setProduct(line.getProduct());
        backorder.setQuantity(quantity);
        backorder.setStatus(Backorder.BackorderStatus.open);
        backorderRepository.save(backorder);
    }

    private void createEmergencyReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);

        if (rule != null) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerReason(AutoReorderLog.TriggerReason.EMERGENCY_REORDER);
            log.setReorderQty(rule.getReorderQty());
            log.setCurrentStock(currentStock);
            autoReorderLogRepository.save(log);
        }
    }

    private void checkSafetyStock(Product product) {
        // 전체 가용 재고 계산 (모든 로케이션 합산, expired 제외)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId()).orElse(null);

        if (rule != null && totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER);
            log.setReorderQty(rule.getReorderQty());
            log.setCurrentStock(totalAvailable);
            autoReorderLogRepository.save(log);
        }
    }

    private void createAuditLog(String action, UUID entityId, String description) {
        AuditLog log = new AuditLog();
        log.setEntityType("SHIPMENT_ORDER");
        log.setEntityId(entityId);
        log.setAction(action);
        log.setDescription(description);
        auditLogRepository.save(log);
    }

    private ShipmentOrderResponse buildResponse(ShipmentOrder order, List<ShipmentOrderLine> lines) {
        ShipmentOrderResponse response = new ShipmentOrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setCustomerName(order.getCustomerName());
        response.setStatus(order.getStatus().name());
        response.setCreatedAt(order.getCreatedAt());
        response.setShippedAt(order.getShippedAt());

        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    ShipmentOrderResponse.ShipmentOrderLineResponse lineResp =
                            new ShipmentOrderResponse.ShipmentOrderLineResponse();
                    lineResp.setId(line.getId());
                    lineResp.setProductId(line.getProduct().getId());
                    lineResp.setRequestedQuantity(line.getRequestedQuantity());
                    lineResp.setPickedQuantity(line.getPickedQuantity());
                    lineResp.setStatus(line.getStatus().name());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);
        return response;
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public StockTransferResponse createStockTransfer(StockTransferRequest request) {
        // 1. 엔티티 조회
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("To location not found"));

        // 2. 기본 검증
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same location");
        }

        if (!inventory.getLocation().getId().equals(fromLocation.getId())) {
            throw new IllegalArgumentException("Inventory location does not match from location");
        }

        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (inventory.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Insufficient inventory quantity");
        }

        // 3. 실사 동결 로케이션 체크
        if (fromLocation.getIsFrozen()) {
            throw new IllegalArgumentException("From location is frozen for cycle count");
        }

        if (toLocation.getIsFrozen()) {
            throw new IllegalArgumentException("To location is frozen for cycle count");
        }

        // 4. 도착지 용량 체크
        if (toLocation.getCurrentQuantity() + request.getQuantity() > toLocation.getCapacity()) {
            throw new IllegalArgumentException("Destination location capacity exceeded");
        }

        Product product = inventory.getProduct();

        // 5. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 6. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getRequiresExpiryTracking() && inventory.getExpiryDate() != null) {
            double remainingPct = calculateRemainingShelfLifePct(
                    inventory.getManufactureDate(),
                    inventory.getExpiryDate(),
                    LocalDate.now()
            );

            // 유통기한 만료
            if (inventory.getExpiryDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Cannot transfer expired inventory");
            }

            // 잔여 유통기한 < 10%는 SHIPPING zone으로만 이동 가능
            if (remainingPct < 10 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new IllegalArgumentException("Inventory with <10% shelf life can only be moved to SHIPPING zone");
            }
        }

        // 8. 대량 이동 승인 체크 (80% 이상)
        boolean needsApproval = false;
        double transferRatio = (double) request.getQuantity() / inventory.getQuantity();
        if (transferRatio >= 0.80) {
            needsApproval = true;
        }

        // 9. StockTransfer 엔티티 생성
        StockTransfer transfer = new StockTransfer();
        transfer.setInventory(inventory);
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setQuantity(request.getQuantity());
        transfer.setRequestedAt(OffsetDateTime.now());

        if (needsApproval) {
            transfer.setTransferStatus(StockTransfer.TransferStatus.pending_approval);
        } else {
            transfer.setTransferStatus(StockTransfer.TransferStatus.immediate);
            // 즉시 이동 실행
            executeTransfer(transfer);
        }

        transfer = stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID id, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        // 이동 실행
        executeTransfer(transfer);

        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedAt(OffsetDateTime.now());
        transfer.setApprovedBy(approvedBy);
        transfer.setUpdatedAt(OffsetDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID id, String reason) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setRejectionReason(reason);
        transfer.setUpdatedAt(OffsetDateTime.now());
        transfer = stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    public StockTransferResponse getTransfer(UUID id) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return StockTransferResponse.from(transfer);
    }

    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private void executeTransfer(StockTransfer transfer) {
        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        int quantity = transfer.getQuantity();

        // 1. 출발지 재고 차감
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setUpdatedAt(OffsetDateTime.now());

        // 2. 출발지 로케이션 수량 차감
        fromLocation.setCurrentQuantity(fromLocation.getCurrentQuantity() - quantity);
        fromLocation.setUpdatedAt(OffsetDateTime.now());

        // 3. 도착지 재고 생성 또는 업데이트
        // 동일 상품, 도착지, 로트, 유통기한이 같은 재고가 있으면 합산, 없으면 신규 생성
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(inventory.getProduct().getId()) &&
                               inv.getLocation().getId().equals(toLocation.getId()) &&
                               (inv.getLotNumber() == null && inventory.getLotNumber() == null ||
                                inv.getLotNumber() != null && inv.getLotNumber().equals(inventory.getLotNumber())) &&
                               (inv.getExpiryDate() == null && inventory.getExpiryDate() == null ||
                                inv.getExpiryDate() != null && inv.getExpiryDate().equals(inventory.getExpiryDate())))
                .collect(Collectors.toList());

        if (!existingInventories.isEmpty()) {
            // 기존 재고에 합산
            Inventory existing = existingInventories.get(0);
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setUpdatedAt(OffsetDateTime.now());
            inventoryRepository.save(existing);
        } else {
            // 신규 재고 생성
            Inventory newInventory = new Inventory();
            newInventory.setProduct(inventory.getProduct());
            newInventory.setLocation(toLocation);
            newInventory.setQuantity(quantity);
            newInventory.setLotNumber(inventory.getLotNumber());
            newInventory.setManufactureDate(inventory.getManufactureDate());
            newInventory.setExpiryDate(inventory.getExpiryDate());
            newInventory.setReceivedAt(inventory.getReceivedAt());
            newInventory.setExpired(inventory.getExpired());
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 수량 증가
        toLocation.setCurrentQuantity(toLocation.getCurrentQuantity() + quantity);
        toLocation.setUpdatedAt(OffsetDateTime.now());

        // 5. 변경사항 저장
        inventoryRepository.save(inventory);
        locationRepository.save(fromLocation);
        locationRepository.save(toLocation);

        // 6. 안전재고 체크 (STORAGE zone만)
        checkSafetyStock(inventory.getProduct());
    }

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move FROZEN products to AMBIENT storage");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move COLD products to AMBIENT storage");
        }

        // HAZMAT 상품 → 비-HAZMAT zone: 거부
        if (productType == Product.StorageType.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new IllegalArgumentException("HAZMAT products must stay in HAZMAT zone");
        }

        // AMBIENT 상품 → COLD/FROZEN: 허용 (상위 호환)
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 있는 재고 확인
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()) && inv.getQuantity() > 0)
                .collect(Collectors.toList());

        boolean productIsHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory existing : existingInventories) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (productIsHazmat != existingIsHazmat) {
                throw new IllegalArgumentException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            return 100.0;
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void checkSafetyStock(Product product) {
        // STORAGE zone 내 전체 재고 확인
        int totalStorageStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()) &&
                               inv.getLocation().getZone() == Location.Zone.STORAGE &&
                               !inv.getExpired() &&
                               inv.getQuantity() > 0)
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 확인
        safetyStockRuleRepository.findByProductId(product.getId()).ifPresent(rule -> {
            if (totalStorageStock <= rule.getMinQty()) {
                // 자동 재발주 기록
                AutoReorderLog log = new AutoReorderLog();
                log.setProduct(product);
                log.setTriggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER);
                log.setReorderQty(rule.getReorderQty());
                log.setCurrentStock(totalStorageStock);
                autoReorderLogRepository.save(log);
            }
        });
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
spring.application.name=wms

# PostgreSQL Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/wms
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# Server Configuration
server.port=8080


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- WMS Database Schema

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(10) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    requires_expiry_tracking BOOLEAN NOT NULL DEFAULT FALSE,
    shelf_life_days INTEGER,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Locations table
CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    zone VARCHAR(20) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    capacity INTEGER NOT NULL,
    current_quantity INTEGER NOT NULL DEFAULT 0,
    is_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inventory table
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    lot_number VARCHAR(100),
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    expired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, location_id, lot_number, expiry_date)
);

-- Suppliers table
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Supplier Penalties table
CREATE TABLE supplier_penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(30) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase Orders table
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    ordered_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase Order Lines table
CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_quantity INTEGER NOT NULL CHECK (ordered_quantity > 0),
    received_quantity INTEGER NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inbound Receipts table
CREATE TABLE inbound_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inbound Receipt Lines table
CREATE TABLE inbound_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    lot_number VARCHAR(100),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seasonal Config table
CREATE TABLE seasonal_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(3, 2) NOT NULL DEFAULT 1.5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Shipment Orders table
CREATE TABLE shipment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    shipped_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Shipment Order Lines table
CREATE TABLE shipment_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_id UUID NOT NULL REFERENCES shipment_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    picked_quantity INTEGER NOT NULL DEFAULT 0 CHECK (picked_quantity >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picked', 'partial', 'backordered')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backorders table
CREATE TABLE backorders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_line_id UUID NOT NULL REFERENCES shipment_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Safety Stock Rules table
CREATE TABLE safety_stock_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) UNIQUE,
    min_qty INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Auto Reorder Logs table
CREATE TABLE auto_reorder_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    trigger_reason VARCHAR(50) NOT NULL CHECK (trigger_reason IN ('SAFETY_STOCK_TRIGGER', 'EMERGENCY_REORDER')),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    current_stock INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Stock Transfers table
CREATE TABLE stock_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    from_location_id UUID NOT NULL REFERENCES locations(id),
    to_location_id UUID NOT NULL REFERENCES locations(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'immediate' CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    requested_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    approved_by VARCHAR(100),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit Logs table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    user_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cycle Counts table
CREATE TABLE cycle_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    started_by VARCHAR(100),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inventory Adjustments table
CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    system_qty INTEGER NOT NULL,
    actual_qty INTEGER NOT NULL,
    difference_qty INTEGER NOT NULL,
    reason TEXT NOT NULL,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'pending' CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_location_id ON inventory(location_id);
CREATE INDEX idx_inventory_expiry_date ON inventory(expiry_date);
CREATE INDEX idx_supplier_penalties_supplier_id ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_occurred_at ON supplier_penalties(occurred_at);
CREATE INDEX idx_purchase_order_lines_po_id ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_inbound_receipt_lines_receipt_id ON inbound_receipt_lines(inbound_receipt_id);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date);
CREATE INDEX idx_shipment_order_lines_order_id ON shipment_order_lines(shipment_order_id);
CREATE INDEX idx_backorders_line_id ON backorders(shipment_order_line_id);
CREATE INDEX idx_backorders_product_id ON backorders(product_id);
CREATE INDEX idx_safety_stock_rules_product_id ON safety_stock_rules(product_id);
CREATE INDEX idx_auto_reorder_logs_product_id ON auto_reorder_logs(product_id);
CREATE INDEX idx_stock_transfers_inventory_id ON stock_transfers(inventory_id);
CREATE INDEX idx_stock_transfers_from_location_id ON stock_transfers(from_location_id);
CREATE INDEX idx_stock_transfers_to_location_id ON stock_transfers(to_location_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(transfer_status);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_cycle_counts_location_id ON cycle_counts(location_id);
CREATE INDEX idx_cycle_counts_product_id ON cycle_counts(product_id);
CREATE INDEX idx_cycle_counts_status ON cycle_counts(status);
CREATE INDEX idx_inventory_adjustments_inventory_id ON inventory_adjustments(inventory_id);
CREATE INDEX idx_inventory_adjustments_location_product ON inventory_adjustments(location_id, product_id);
CREATE INDEX idx_inventory_adjustments_created_at ON inventory_adjustments(created_at);

