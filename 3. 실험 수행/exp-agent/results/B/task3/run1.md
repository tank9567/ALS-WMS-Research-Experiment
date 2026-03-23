# CLI Output

재고 이동 기능 구현이 완료되었습니다.

## 구현 완료 내역

### 1. Entity
- `StockTransfer.java` - stock_transfers 테이블 매핑

### 2. Repository
- `StockTransferRepository.java` - 재고 이동 데이터 접근
- `InventoryRepository.java` - 필요한 메서드 추가 (findByProduct, findByLocation 등)
- `SafetyStockRuleRepository.java` - findByProduct 메서드 추가

### 3. DTO
- `StockTransferRequest.java` - 이동 요청 DTO
- `StockTransferResponse.java` - 이동 응답 DTO

### 4. Service
`StockTransferService.java`에 다음 비즈니스 로직 구현:
- 단일 트랜잭션으로 출발지 차감/도착지 증가
- 동일 로케이션 거부
- 재고 부족 체크
- 용량 체크
- 보관 유형 호환성 검증 (FROZEN→AMBIENT 거부 등)
- HAZMAT 혼적 금지
- 유통기한 <10% → SHIPPING zone만 허용
- 유통기한 만료 → 이동 불가
- 대량 이동(≥80%) → 관리자 승인 (pending_approval)
- 실사 동결 로케이션 이동 불가
- STORAGE zone 안전재고 체크

### 5. Controller
`StockTransferController.java`에 다음 REST API 구현:
- `POST /api/v1/stock-transfers` - 재고 이동 실행
- `POST /api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
- `POST /api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
- `GET /api/v1/stock-transfers/{id}` - 이동 상세 조회
- `GET /api/v1/stock-transfers` - 이동 이력 조회

모든 요구사항을 설계 문서(design.md)에 따라 구현했습니다.


# Generated Code


============================================================
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import org.springframework.http.HttpStatus;
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
            @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        try {
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        try {
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        try {
            InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        try {
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipment(
            @PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.pickShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipment(
            @PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.shipShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable("id") UUID shipmentId) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        try {
            List<ShipmentOrderResponse> responses = shipmentOrderService.getAllShipmentOrders();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
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
import com.wms.service.StockTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> transferStock(
            @RequestBody StockTransferRequest request) {
        try {
            StockTransferResponse response = stockTransferService.transferStock(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @RequestBody Map<String, String> request) {
        try {
            String approvedBy = request.get("approvedBy");
            if (approvedBy == null || approvedBy.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("INVALID_REQUEST", "approvedBy is required"));
            }

            StockTransferResponse response = stockTransferService.approveTransfer(transferId, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @RequestBody Map<String, String> request) {
        try {
            String approvedBy = request.get("approvedBy");
            if (approvedBy == null || approvedBy.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("INVALID_REQUEST", "approvedBy is required"));
            }

            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransferById(
            @PathVariable("id") UUID transferId) {
        try {
            StockTransferResponse response = stockTransferService.getTransferById(transferId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        try {
            List<StockTransferResponse> responses = stockTransferService.getAllTransfers();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApiResponse.java
============================================================
package com.wms.dto;

public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message));
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public static class ErrorDetail {
        private String code;
        private String message;

        public ErrorDetail() {
        }

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        // Getters and Setters
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InboundReceiptRequest {

    private UUID poId;
    private String receivedBy;
    private List<InboundReceiptLineRequest> lines;

    // Getters and Setters
    public UUID getPoId() {
        return poId;
    }

    public void setPoId(UUID poId) {
        this.poId = poId;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public List<InboundReceiptLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<InboundReceiptLineRequest> lines) {
        this.lines = lines;
    }

    public static class InboundReceiptLineRequest {
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;

        // Getters and Setters
        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public UUID getLocationId() {
            return locationId;
        }

        public void setLocationId(UUID locationId) {
            this.locationId = locationId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        public LocalDate getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
        }

        public LocalDate getManufactureDate() {
            return manufactureDate;
        }

        public void setManufactureDate(LocalDate manufactureDate) {
            this.manufactureDate = manufactureDate;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class InboundReceiptResponse {

    private UUID receiptId;
    private UUID poId;
    private String poNumber;
    private String status;
    private String receivedBy;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private List<InboundReceiptLineResponse> lines;

    // Getters and Setters
    public UUID getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(UUID receiptId) {
        this.receiptId = receiptId;
    }

    public UUID getPoId() {
        return poId;
    }

    public void setPoId(UUID poId) {
        this.poId = poId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public List<InboundReceiptLineResponse> getLines() {
        return lines;
    }

    public void setLines(List<InboundReceiptLineResponse> lines) {
        this.lines = lines;
    }

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

        // Getters and Setters
        public UUID getReceiptLineId() {
            return receiptLineId;
        }

        public void setReceiptLineId(UUID receiptLineId) {
            this.receiptLineId = receiptLineId;
        }

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public String getProductSku() {
            return productSku;
        }

        public void setProductSku(String productSku) {
            this.productSku = productSku;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public UUID getLocationId() {
            return locationId;
        }

        public void setLocationId(UUID locationId) {
            this.locationId = locationId;
        }

        public String getLocationCode() {
            return locationCode;
        }

        public void setLocationCode(String locationCode) {
            this.locationCode = locationCode;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        public LocalDate getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
        }

        public LocalDate getManufactureDate() {
            return manufactureDate;
        }

        public void setManufactureDate(LocalDate manufactureDate) {
            this.manufactureDate = manufactureDate;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderRequest {

    private String shipmentNumber;
    private String customerName;
    private OffsetDateTime requestedAt;
    private List<ShipmentOrderLineRequest> lines;

    // Getters and Setters
    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public List<ShipmentOrderLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentOrderLineRequest> lines) {
        this.lines = lines;
    }

    public static class ShipmentOrderLineRequest {
        private UUID productId;
        private Integer requestedQty;

        // Getters and Setters
        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public Integer getRequestedQty() {
            return requestedQty;
        }

        public void setRequestedQty(Integer requestedQty) {
            this.requestedQty = requestedQty;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private List<PickDetail> pickDetails;
    private List<BackorderInfo> backorders;

    // Getters and Setters
    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(OffsetDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public List<ShipmentOrderLineResponse> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentOrderLineResponse> lines) {
        this.lines = lines;
    }

    public List<PickDetail> getPickDetails() {
        return pickDetails;
    }

    public void setPickDetails(List<PickDetail> pickDetails) {
        this.pickDetails = pickDetails;
    }

    public List<BackorderInfo> getBackorders() {
        return backorders;
    }

    public void setBackorders(List<BackorderInfo> backorders) {
        this.backorders = backorders;
    }

    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;

        // Getters and Setters
        public UUID getShipmentLineId() {
            return shipmentLineId;
        }

        public void setShipmentLineId(UUID shipmentLineId) {
            this.shipmentLineId = shipmentLineId;
        }

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public Integer getRequestedQty() {
            return requestedQty;
        }

        public void setRequestedQty(Integer requestedQty) {
            this.requestedQty = requestedQty;
        }

        public Integer getPickedQty() {
            return pickedQty;
        }

        public void setPickedQty(Integer pickedQty) {
            this.pickedQty = pickedQty;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class PickDetail {
        private UUID productId;
        private UUID locationId;
        private Integer pickedQty;

        // Getters and Setters
        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public UUID getLocationId() {
            return locationId;
        }

        public void setLocationId(UUID locationId) {
            this.locationId = locationId;
        }

        public Integer getPickedQty() {
            return pickedQty;
        }

        public void setPickedQty(Integer pickedQty) {
            this.pickedQty = pickedQty;
        }
    }

    public static class BackorderInfo {
        private UUID backorderId;
        private UUID productId;
        private Integer shortageQty;

        // Getters and Setters
        public UUID getBackorderId() {
            return backorderId;
        }

        public void setBackorderId(UUID backorderId) {
            this.backorderId = backorderId;
        }

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public Integer getShortageQty() {
            return shortageQty;
        }

        public void setShortageQty(Integer shortageQty) {
            this.shortageQty = shortageQty;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
============================================================
package com.wms.dto;

import java.util.UUID;

public class StockTransferRequest {

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferredBy;

    // Getters and Setters
    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getFromLocationId() {
        return fromLocationId;
    }

    public void setFromLocationId(UUID fromLocationId) {
        this.fromLocationId = fromLocationId;
    }

    public UUID getToLocationId() {
        return toLocationId;
    }

    public void setToLocationId(UUID toLocationId) {
        this.toLocationId = toLocationId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTransferredBy() {
        return transferredBy;
    }

    public void setTransferredBy(String transferredBy) {
        this.transferredBy = transferredBy;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.StockTransfer;

import java.time.OffsetDateTime;
import java.util.UUID;

public class StockTransferResponse {

    private UUID transferId;
    private UUID productId;
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

    public static StockTransferResponse from(StockTransfer transfer) {
        StockTransferResponse response = new StockTransferResponse();
        response.transferId = transfer.getTransferId();
        response.productId = transfer.getProduct().getProductId();
        response.productName = transfer.getProduct().getName();
        response.fromLocationId = transfer.getFromLocation().getLocationId();
        response.fromLocationCode = transfer.getFromLocation().getCode();
        response.toLocationId = transfer.getToLocation().getLocationId();
        response.toLocationCode = transfer.getToLocation().getCode();
        response.quantity = transfer.getQuantity();
        response.lotNumber = transfer.getLotNumber();
        response.reason = transfer.getReason();
        response.transferStatus = transfer.getTransferStatus();
        response.transferredBy = transfer.getTransferredBy();
        response.approvedBy = transfer.getApprovedBy();
        response.transferredAt = transfer.getTransferredAt();
        return response;
    }

    // Getters and Setters
    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public UUID getFromLocationId() {
        return fromLocationId;
    }

    public void setFromLocationId(UUID fromLocationId) {
        this.fromLocationId = fromLocationId;
    }

    public String getFromLocationCode() {
        return fromLocationCode;
    }

    public void setFromLocationCode(String fromLocationCode) {
        this.fromLocationCode = fromLocationCode;
    }

    public UUID getToLocationId() {
        return toLocationId;
    }

    public void setToLocationId(UUID toLocationId) {
        this.toLocationId = toLocationId;
    }

    public String getToLocationCode() {
        return toLocationCode;
    }

    public void setToLocationCode(String toLocationCode) {
        this.toLocationCode = toLocationCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(String transferStatus) {
        this.transferStatus = transferStatus;
    }

    public String getTransferredBy() {
        return transferredBy;
    }

    public void setTransferredBy(String transferredBy) {
        this.transferredBy = transferredBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public OffsetDateTime getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(OffsetDateTime transferredAt) {
        this.transferredAt = transferredAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
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

    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details;

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

    // Getters and Setters
    public UUID getLogId() {
        return logId;
    }

    public void setLogId(UUID logId) {
        this.logId = logId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
public class AutoReorderLog {

    @Id
    @Column(name = "reorder_log_id")
    private UUID reorderLogId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

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

    // Getters and Setters
    public UUID getReorderLogId() {
        return reorderLogId;
    }

    public void setReorderLogId(UUID reorderLogId) {
        this.reorderLogId = reorderLogId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public Integer getMinQty() {
        return minQty;
    }

    public void setMinQty(Integer minQty) {
        this.minQty = minQty;
    }

    public Integer getReorderQty() {
        return reorderQty;
    }

    public void setReorderQty(Integer reorderQty) {
        this.reorderQty = reorderQty;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backorders")
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

    @Column(name = "status", nullable = false, length = 20)
    private String status = "open";

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

    // Getters and Setters
    public UUID getBackorderId() {
        return backorderId;
    }

    public void setBackorderId(UUID backorderId) {
        this.backorderId = backorderId;
    }

    public UUID getShipmentLineId() {
        return shipmentLineId;
    }

    public void setShipmentLineId(UUID shipmentLineId) {
        this.shipmentLineId = shipmentLineId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Integer getShortageQty() {
        return shortageQty;
    }

    public void setShortageQty(Integer shortageQty) {
        this.shortageQty = shortageQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(OffsetDateTime fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "inspecting";

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
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

    // Getters and Setters
    public UUID getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(UUID receiptId) {
        this.receiptId = receiptId;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public void setPurchaseOrder(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<InboundReceiptLine> getLines() {
        return lines;
    }

    public void setLines(List<InboundReceiptLine> lines) {
        this.lines = lines;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
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

    // Getters and Setters
    public UUID getReceiptLineId() {
        return receiptLineId;
    }

    public void setReceiptLineId(UUID receiptLineId) {
        this.receiptLineId = receiptLineId;
    }

    public InboundReceipt getInboundReceipt() {
        return inboundReceipt;
    }

    public void setInboundReceipt(InboundReceipt inboundReceipt) {
        this.inboundReceipt = inboundReceipt;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDate getManufactureDate() {
        return manufactureDate;
    }

    public void setManufactureDate(LocalDate manufactureDate) {
        this.manufactureDate = manufactureDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
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

    // Getters and Setters
    public UUID getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(UUID inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDate getManufactureDate() {
        return manufactureDate;
    }

    public void setManufactureDate(LocalDate manufactureDate) {
        this.manufactureDate = manufactureDate;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Boolean getIsExpired() {
        return isExpired;
    }

    public void setIsExpired(Boolean isExpired) {
        this.isExpired = isExpired;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Column(name = "zone", nullable = false, length = 50)
    private String zone;

    @Column(name = "storage_type", nullable = false, length = 20)
    private String storageType = "AMBIENT";

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

    // Getters and Setters
    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getCurrentQty() {
        return currentQty;
    }

    public void setCurrentQty(Integer currentQty) {
        this.currentQty = currentQty;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getIsFrozen() {
        return isFrozen;
    }

    public void setIsFrozen(Boolean isFrozen) {
        this.isFrozen = isFrozen;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Product.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "sku", unique = true, nullable = false, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", nullable = false, length = 50)
    private String category = "GENERAL";

    @Column(name = "storage_type", nullable = false, length = 20)
    private String storageType = "AMBIENT";

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

    // Getters and Setters
    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Boolean getHasExpiry() {
        return hasExpiry;
    }

    public void setHasExpiry(Boolean hasExpiry) {
        this.hasExpiry = hasExpiry;
    }

    public Integer getMinRemainingShelfLifePct() {
        return minRemainingShelfLifePct;
    }

    public void setMinRemainingShelfLifePct(Integer minRemainingShelfLifePct) {
        this.minRemainingShelfLifePct = minRemainingShelfLifePct;
    }

    public Integer getMaxPickQty() {
        return maxPickQty;
    }

    public void setMaxPickQty(Integer maxPickQty) {
        this.maxPickQty = maxPickQty;
    }

    public Boolean getManufactureDateRequired() {
        return manufactureDateRequired;
    }

    public void setManufactureDateRequired(Boolean manufactureDateRequired) {
        this.manufactureDateRequired = manufactureDateRequired;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "po_type", nullable = false, length = 20)
    private String poType = "NORMAL";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
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

    // Getters and Setters
    public UUID getPoId() {
        return poId;
    }

    public void setPoId(UUID poId) {
        this.poId = poId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public String getPoType() {
        return poType;
    }

    public void setPoType(String poType) {
        this.poType = poType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(OffsetDateTime orderedAt) {
        this.orderedAt = orderedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }

    public void setLines(List<PurchaseOrderLine> lines) {
        this.lines = lines;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"po_id", "product_id"})
})
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

    // Getters and Setters
    public UUID getPoLineId() {
        return poLineId;
    }

    public void setPoLineId(UUID poLineId) {
        this.poLineId = poLineId;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public void setPurchaseOrder(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getOrderedQty() {
        return orderedQty;
    }

    public void setOrderedQty(Integer orderedQty) {
        this.orderedQty = orderedQty;
    }

    public Integer getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(Integer receivedQty) {
        this.receivedQty = receivedQty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
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

    // Getters and Setters
    public UUID getRuleId() {
        return ruleId;
    }

    public void setRuleId(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Integer getMinQty() {
        return minQty;
    }

    public void setMinQty(Integer minQty) {
        this.minQty = minQty;
    }

    public Integer getReorderQty() {
        return reorderQty;
    }

    public void setReorderQty(Integer reorderQty) {
        this.reorderQty = reorderQty;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
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
    private BigDecimal multiplier = BigDecimal.valueOf(1.50);

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

    // Getters and Setters
    public UUID getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(UUID seasonId) {
        this.seasonId = seasonId;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public void setSeasonName(String seasonName) {
        this.seasonName = seasonName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
public class ShipmentOrder {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 30)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

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

    // Getters and Setters
    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(OffsetDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
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

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @PrePersist
    protected void onCreate() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    // Getters and Setters
    public UUID getShipmentLineId() {
        return shipmentLineId;
    }

    public void setShipmentLineId(UUID shipmentLineId) {
        this.shipmentLineId = shipmentLineId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Integer getRequestedQty() {
        return requestedQty;
    }

    public void setRequestedQty(Integer requestedQty) {
        this.requestedQty = requestedQty;
    }

    public Integer getPickedQty() {
        return pickedQty;
    }

    public void setPickedQty(Integer pickedQty) {
        this.pickedQty = pickedQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
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

    @Column(name = "transfer_status", nullable = false, length = 20)
    private String transferStatus = "immediate";

    @Column(name = "transferred_by", nullable = false, length = 100)
    private String transferredBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "transferred_at")
    private OffsetDateTime transferredAt;

    @PrePersist
    protected void onCreate() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (transferredAt == null) {
            transferredAt = OffsetDateTime.now();
        }
    }

    // Getters and Setters
    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Location getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(Location fromLocation) {
        this.fromLocation = fromLocation;
    }

    public Location getToLocation() {
        return toLocation;
    }

    public void setToLocation(Location toLocation) {
        this.toLocation = toLocation;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(String transferStatus) {
        this.transferStatus = transferStatus;
    }

    public String getTransferredBy() {
        return transferredBy;
    }

    public void setTransferredBy(String transferredBy) {
        this.transferredBy = transferredBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public OffsetDateTime getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(OffsetDateTime transferredAt) {
        this.transferredAt = transferredAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

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

    // Getters and Setters
    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
public class SupplierPenalty {

    @Id
    @Column(name = "penalty_id")
    private UUID penaltyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "penalty_type", nullable = false, length = 50)
    private String penaltyType;

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

    // Getters and Setters
    public UUID getPenaltyId() {
        return penaltyId;
    }

    public void setPenaltyId(UUID penaltyId) {
        this.penaltyId = penaltyId;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public String getPenaltyType() {
        return penaltyType;
    }

    public void setPenaltyType(String penaltyType) {
        this.penaltyType = penaltyType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getPoId() {
        return poId;
    }

    public void setPoId(UUID poId) {
        this.poId = poId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId " +
           "AND i.location.locationId = :locationId " +
           "AND (i.lotNumber = :lotNumber OR (i.lotNumber IS NULL AND :lotNumber IS NULL))")
    Optional<Inventory> findByProductAndLocationAndLotNumber(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );

    Optional<Inventory> findByProductAndLocationAndLotNumber(Product product, Location location, String lotNumber);

    List<Inventory> findByLocation(Location location);

    List<Inventory> findByProduct(Product product);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.location " +
           "WHERE i.product.productId = :productId AND i.quantity > 0 AND i.isExpired = false")
    List<Inventory> findAvailableInventoryByProductId(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
           "WHERE i.product.productId = :productId AND i.isExpired = false")
    Integer getTotalAvailableQuantityByProductId(@Param("productId") UUID productId);
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId")
    List<PurchaseOrderLine> findByPurchaseOrderId(@Param("poId") UUID poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol " +
           "WHERE pol.purchaseOrder.poId = :poId AND pol.product.productId = :productId")
    Optional<PurchaseOrderLine> findByPurchaseOrderIdAndProductId(
        @Param("poId") UUID poId,
        @Param("productId") UUID productId
    );
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

    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.supplierId = :supplierId AND po.status = :status")
    List<PurchaseOrder> findBySupplierIdAndStatus(
        @Param("supplierId") UUID supplierId,
        @Param("status") String status
    );
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

    @Query("SELECT sc FROM SeasonalConfig sc " +
           "WHERE sc.isActive = true " +
           "AND sc.startDate <= :date " +
           "AND sc.endDate >= :date")
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

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    List<StockTransfer> findByProductProductIdOrderByTransferredAtDesc(UUID productId);

    List<StockTransfer> findByFromLocationLocationIdOrderByTransferredAtDesc(UUID locationId);

    List<StockTransfer> findByToLocationLocationIdOrderByTransferredAtDesc(UUID locationId);

    List<StockTransfer> findByTransferStatusOrderByTransferredAtDesc(String transferStatus);
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

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp " +
           "WHERE sp.supplier.supplierId = :supplierId " +
           "AND sp.createdAt >= :sinceDate")
    long countBySupplierIdAndCreatedAtAfter(
        @Param("supplierId") UUID supplierId,
        @Param("sinceDate") OffsetDateTime sinceDate
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

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.repository.*;
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
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    public InboundReceiptService(
            InboundReceiptRepository inboundReceiptRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderLineRepository purchaseOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SupplierPenaltyRepository supplierPenaltyRepository,
            SeasonalConfigRepository seasonalConfigRepository) {
        this.inboundReceiptRepository = inboundReceiptRepository;
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
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new IllegalArgumentException("PO not found"));

        if ("hold".equals(po.getStatus())) {
            throw new IllegalStateException("PO is on hold");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = new InboundReceipt();
        receipt.setPurchaseOrder(po);
        receipt.setReceivedBy(request.getReceivedBy());
        receipt.setStatus("inspecting");

        boolean needsApproval = false;
        List<InboundReceiptLine> lines = new ArrayList<>();

        // 3. 라인별 검증
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));

            // 실사 동결 체크
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("Location is frozen for cycle count");
            }

            // 보관 유형 호환성 체크
            validateStorageCompatibility(product, location);

            // 유통기한 관리 상품 체크
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineReq.getExpiryDate() == null || lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("Expiry date and manufacture date are required for expiry-managed products");
                }

                // 잔여 유통기한 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate()
                );

                int minPct = product.getMinRemainingShelfLifePct() != null ? product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minPct) {
                    // 페널티 기록
                    recordPenalty(po.getSupplier(), "SHORT_SHELF_LIFE", po.getPoId(), "Remaining shelf life is below minimum");
                    throw new IllegalArgumentException("Remaining shelf life is below minimum threshold");
                }

                if (remainingPct >= minPct && remainingPct < 50) {
                    needsApproval = true;
                }
            }

            // 초과입고 체크
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderIdAndProductId(po.getPoId(), product.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not in PO"));

            int newTotalReceived = poLine.getReceivedQty() + lineReq.getQuantity();
            double allowedPct = calculateOverReceivePct(product.getCategory(), po.getPoType());

            if (newTotalReceived > poLine.getOrderedQty() * (1 + allowedPct / 100.0)) {
                // 페널티 기록
                recordPenalty(po.getSupplier(), "OVER_DELIVERY", po.getPoId(), "Over-delivery exceeds allowed tolerance");
                throw new IllegalArgumentException("Over-delivery exceeds allowed tolerance");
            }

            // 라인 생성
            InboundReceiptLine line = new InboundReceiptLine();
            line.setInboundReceipt(receipt);
            line.setProduct(product);
            line.setLocation(location);
            line.setQuantity(lineReq.getQuantity());
            line.setLotNumber(lineReq.getLotNumber());
            line.setExpiryDate(lineReq.getExpiryDate());
            line.setManufactureDate(lineReq.getManufactureDate());

            lines.add(line);
        }

        receipt.setLines(lines);

        if (needsApproval) {
            receipt.setStatus("pending_approval");
        }

        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (!"inspecting".equals(receipt.getStatus()) && !"pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt cannot be confirmed in current status");
        }

        if ("pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt requires approval before confirmation");
        }

        // 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line, receipt.getReceivedAt());
            updateLocation(line.getLocation(), line.getQuantity());
            updatePurchaseOrderLine(line.getProduct(), receipt.getPurchaseOrder(), line.getQuantity());
        }

        receipt.setStatus("confirmed");
        receipt.setConfirmedAt(OffsetDateTime.now());

        // PO 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (!"inspecting".equals(receipt.getStatus()) && !"pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt cannot be rejected in current status");
        }

        receipt.setStatus("rejected");
        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (!"pending_approval".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt is not pending approval");
        }

        receipt.setStatus("inspecting");
        inboundReceiptRepository.save(receipt);

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Helper methods
    private void validateStorageCompatibility(Product product, Location location) {
        String productType = product.getStorageType();
        String locationType = location.getStorageType();
        String zone = location.getZone();

        if ("HAZMAT".equals(product.getCategory()) && !"HAZMAT".equals(zone)) {
            throw new IllegalArgumentException("HAZMAT products must be stored in HAZMAT zone");
        }

        if ("FROZEN".equals(productType) && !"FROZEN".equals(locationType)) {
            throw new IllegalArgumentException("FROZEN products require FROZEN storage");
        }

        if ("COLD".equals(productType) && !"COLD".equals(locationType) && !"FROZEN".equals(locationType)) {
            throw new IllegalArgumentException("COLD products require COLD or FROZEN storage");
        }

        if ("AMBIENT".equals(productType) && !"AMBIENT".equals(locationType)) {
            throw new IllegalArgumentException("AMBIENT products require AMBIENT storage");
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private double calculateOverReceivePct(String category, String poType) {
        // 카테고리별 기본 허용률
        double basePct;
        switch (category) {
            case "GENERAL":
                basePct = 10.0;
                break;
            case "FRESH":
                basePct = 5.0;
                break;
            case "HAZMAT":
                return 0.0; // HAZMAT은 항상 0%
            case "HIGH_VALUE":
                basePct = 3.0;
                break;
            default:
                basePct = 10.0;
        }

        // 발주 유형별 가중치
        double multiplier = 1.0;
        switch (poType) {
            case "URGENT":
                multiplier = 2.0;
                break;
            case "IMPORT":
                multiplier = 1.5;
                break;
            default:
                multiplier = 1.0;
        }

        // 성수기 가중치
        SeasonalConfig season = seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now()).orElse(null);
        if (season != null) {
            multiplier *= season.getMultiplier().doubleValue();
        }

        return basePct * multiplier;
    }

    private void recordPenalty(Supplier supplier, String penaltyType, UUID poId, String description) {
        SupplierPenalty penalty = new SupplierPenalty();
        penalty.setSupplier(supplier);
        penalty.setPenaltyType(penaltyType);
        penalty.setPoId(poId);
        penalty.setDescription(description);
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndCreatedAtAfter(supplier.getSupplierId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // PO를 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findBySupplierIdAndStatus(supplier.getSupplierId(), "pending");
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus("hold");
                purchaseOrderRepository.save(po);
            }
        }
    }

    private void updateInventory(InboundReceiptLine line, OffsetDateTime receivedAt) {
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
        ).orElse(null);

        if (inventory != null) {
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
        } else {
            inventory = new Inventory();
            inventory.setProduct(line.getProduct());
            inventory.setLocation(line.getLocation());
            inventory.setQuantity(line.getQuantity());
            inventory.setLotNumber(line.getLotNumber());
            inventory.setExpiryDate(line.getExpiryDate());
            inventory.setManufactureDate(line.getManufactureDate());
            inventory.setReceivedAt(receivedAt);
        }

        inventoryRepository.save(inventory);
    }

    private void updateLocation(Location location, int quantity) {
        location.setCurrentQty(location.getCurrentQty() + quantity);
        locationRepository.save(location);
    }

    private void updatePurchaseOrderLine(Product product, PurchaseOrder po, int quantity) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderIdAndProductId(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("PO line not found"));

        poLine.setReceivedQty(poLine.getReceivedQty() + quantity);
        purchaseOrderLineRepository.save(poLine);
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(po.getPoId());

        boolean allFulfilled = lines.stream().allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus("completed");
        } else if (anyReceived) {
            po.setStatus("partial");
        }

        purchaseOrderRepository.save(po);
    }

    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setReceiptId(receipt.getReceiptId());
        response.setPoId(receipt.getPurchaseOrder().getPoId());
        response.setPoNumber(receipt.getPurchaseOrder().getPoNumber());
        response.setStatus(receipt.getStatus());
        response.setReceivedBy(receipt.getReceivedBy());
        response.setReceivedAt(receipt.getReceivedAt());
        response.setConfirmedAt(receipt.getConfirmedAt());

        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
                .map(line -> {
                    InboundReceiptResponse.InboundReceiptLineResponse lineResp = new InboundReceiptResponse.InboundReceiptLineResponse();
                    lineResp.setReceiptLineId(line.getReceiptLineId());
                    lineResp.setProductId(line.getProduct().getProductId());
                    lineResp.setProductSku(line.getProduct().getSku());
                    lineResp.setProductName(line.getProduct().getName());
                    lineResp.setLocationId(line.getLocation().getLocationId());
                    lineResp.setLocationCode(line.getLocation().getCode());
                    lineResp.setQuantity(line.getQuantity());
                    lineResp.setLotNumber(line.getLotNumber());
                    lineResp.setExpiryDate(line.getExpiryDate());
                    lineResp.setManufactureDate(line.getManufactureDate());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);

        return response;
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShipmentOrderService {

    @Autowired
    private ShipmentOrderRepository shipmentOrderRepository;

    @Autowired
    private ShipmentOrderLineRepository shipmentOrderLineRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BackorderRepository backorderRepository;

    @Autowired
    private SafetyStockRuleRepository safetyStockRuleRepository;

    @Autowired
    private AutoReorderLogRepository autoReorderLogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 출고 지시서 생성
        ShipmentOrder shipmentOrder = new ShipmentOrder();
        shipmentOrder.setShipmentNumber(request.getShipmentNumber());
        shipmentOrder.setCustomerName(request.getCustomerName());
        shipmentOrder.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : OffsetDateTime.now());
        shipmentOrder.setStatus("pending");
        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // HAZMAT과 FRESH 상품 분리 확인
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();
        boolean hasFresh = false;

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineReq.getProductId()));

            if ("HAZMAT".equals(product.getCategory())) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
                if ("FRESH".equals(product.getCategory())) {
                    hasFresh = true;
                }
            }
        }

        // HAZMAT과 FRESH가 함께 있으면 분리 출고
        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 상품만 별도 출고 지시서 생성
            ShipmentOrder hazmatShipment = new ShipmentOrder();
            hazmatShipment.setShipmentNumber(request.getShipmentNumber() + "-HAZMAT");
            hazmatShipment.setCustomerName(request.getCustomerName());
            hazmatShipment.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : OffsetDateTime.now());
            hazmatShipment.setStatus("pending");
            hazmatShipment = shipmentOrderRepository.save(hazmatShipment);

            // HAZMAT 라인 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : hazmatLines) {
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentId(hazmatShipment.getShipmentId());
                line.setProductId(lineReq.getProductId());
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus("pending");
                shipmentOrderLineRepository.save(line);
            }

            // 원래 출고 지시서에는 비-HAZMAT 라인만 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : nonHazmatLines) {
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentId(shipmentOrder.getShipmentId());
                line.setProductId(lineReq.getProductId());
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus("pending");
                shipmentOrderLineRepository.save(line);
            }
        } else {
            // 분리 불필요 - 모든 라인 추가
            for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentId(shipmentOrder.getShipmentId());
                line.setProductId(lineReq.getProductId());
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus("pending");
                shipmentOrderLineRepository.save(line);
            }
        }

        return convertToResponse(shipmentOrder);
    }

    @Transactional
    public ShipmentOrderResponse pickShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found: " + shipmentId));

        if (!"pending".equals(shipmentOrder.getStatus())) {
            throw new IllegalStateException("Shipment order is not in pending status");
        }

        shipmentOrder.setStatus("picking");
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);

        List<ShipmentOrderResponse.PickDetail> pickDetails = new ArrayList<>();
        List<ShipmentOrderResponse.BackorderInfo> backorders = new ArrayList<>();

        for (ShipmentOrderLine line : lines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + line.getProductId()));

            // 가용 재고 조회 및 정렬 (FIFO/FEFO)
            List<Inventory> availableInventories = getAvailableInventorySorted(product, line.getProductId());

            int requestedQty = line.getRequestedQty();
            int totalAvailable = availableInventories.stream()
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            // HAZMAT 상품인 경우 max_pick_qty 제한
            if ("HAZMAT".equals(product.getCategory()) && product.getMaxPickQty() != null) {
                requestedQty = Math.min(requestedQty, product.getMaxPickQty());
            }

            int pickedQty = 0;

            // 피킹 실행
            for (Inventory inventory : availableInventories) {
                if (pickedQty >= requestedQty) {
                    break;
                }

                Location location = inventory.getLocation();

                // 실사 동결 로케이션 제외
                if (location.getIsFrozen()) {
                    continue;
                }

                // HAZMAT 상품은 HAZMAT zone에서만 피킹
                if ("HAZMAT".equals(product.getCategory()) && !"HAZMAT".equals(location.getZone())) {
                    continue;
                }

                int pickQty = Math.min(inventory.getQuantity(), requestedQty - pickedQty);

                // 재고 차감
                inventory.setQuantity(inventory.getQuantity() - pickQty);
                inventoryRepository.save(inventory);

                // 로케이션 수량 차감
                location.setCurrentQty(location.getCurrentQty() - pickQty);
                locationRepository.save(location);

                pickedQty += pickQty;

                // 피킹 상세 기록
                ShipmentOrderResponse.PickDetail pickDetail = new ShipmentOrderResponse.PickDetail();
                pickDetail.setProductId(line.getProductId());
                pickDetail.setLocationId(location.getLocationId());
                pickDetail.setPickedQty(pickQty);
                pickDetails.add(pickDetail);

                // 보관 유형 불일치 경고
                if (!product.getStorageType().equals(location.getStorageType())) {
                    logStorageTypeMismatch(shipmentOrder.getShipmentId(), product, location);
                }
            }

            // 라인 상태 업데이트
            line.setPickedQty(pickedQty);

            double fulfillmentRate = totalAvailable > 0 ? (double) pickedQty / requestedQty : 0;

            if (pickedQty == requestedQty) {
                line.setStatus("picked");
            } else if (pickedQty == 0) {
                // 전량 백오더
                line.setStatus("backordered");
                Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), requestedQty);
                backorders.add(convertToBackorderInfo(backorder));
            } else {
                // 부분출고 의사결정
                if (fulfillmentRate >= 0.7) {
                    // 70% 이상: 부분출고 + 백오더
                    line.setStatus("partial");
                    int shortageQty = requestedQty - pickedQty;
                    Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), shortageQty);
                    backorders.add(convertToBackorderInfo(backorder));
                } else if (fulfillmentRate >= 0.3) {
                    // 30%~70%: 부분출고 + 백오더 + 긴급발주
                    line.setStatus("partial");
                    int shortageQty = requestedQty - pickedQty;
                    Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), shortageQty);
                    backorders.add(convertToBackorderInfo(backorder));
                    triggerUrgentReorder(line.getProductId(), totalAvailable);
                } else {
                    // 30% 미만: 전량 백오더
                    line.setStatus("backordered");
                    line.setPickedQty(0);
                    Backorder backorder = createBackorder(line.getShipmentLineId(), line.getProductId(), requestedQty);
                    backorders.add(convertToBackorderInfo(backorder));

                    // 피킹한 재고를 롤백
                    for (ShipmentOrderResponse.PickDetail detail : pickDetails) {
                        if (detail.getProductId().equals(line.getProductId())) {
                            Inventory inv = inventoryRepository.findByProductAndLocationAndLotNumber(
                                    detail.getProductId(), detail.getLocationId(), null)
                                    .orElse(null);
                            if (inv != null) {
                                inv.setQuantity(inv.getQuantity() + detail.getPickedQty());
                                inventoryRepository.save(inv);

                                Location loc = locationRepository.findById(detail.getLocationId()).orElse(null);
                                if (loc != null) {
                                    loc.setCurrentQty(loc.getCurrentQty() + detail.getPickedQty());
                                    locationRepository.save(loc);
                                }
                            }
                        }
                    }
                    pickDetails.removeIf(d -> d.getProductId().equals(line.getProductId()));
                }
            }

            shipmentOrderLineRepository.save(line);
        }

        // 출고 지시서 상태 업데이트
        boolean allPicked = lines.stream().allMatch(l -> "picked".equals(l.getStatus()));
        boolean anyPicked = lines.stream().anyMatch(l -> "picked".equals(l.getStatus()) || "partial".equals(l.getStatus()));

        if (allPicked) {
            shipmentOrder.setStatus("shipped");
        } else if (anyPicked) {
            shipmentOrder.setStatus("partial");
        } else {
            shipmentOrder.setStatus("pending");
        }

        shipmentOrderRepository.save(shipmentOrder);

        ShipmentOrderResponse response = convertToResponse(shipmentOrder);
        response.setPickDetails(pickDetails);
        response.setBackorders(backorders);

        return response;
    }

    @Transactional
    public ShipmentOrderResponse shipShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found: " + shipmentId));

        if (!"picking".equals(shipmentOrder.getStatus()) && !"partial".equals(shipmentOrder.getStatus())) {
            throw new IllegalStateException("Shipment order is not ready to ship");
        }

        shipmentOrder.setStatus("shipped");
        shipmentOrder.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipmentOrder);

        // 안전재고 체크
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentId);
        for (ShipmentOrderLine line : lines) {
            checkSafetyStockAndReorder(line.getProductId());
        }

        return convertToResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment order not found: " + shipmentId));
        return convertToResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private List<Inventory> getAvailableInventorySorted(Product product, UUID productId) {
        List<Inventory> inventories = inventoryRepository.findAvailableInventoryByProductId(productId);

        LocalDate today = LocalDate.now();

        // 만료 재고 및 is_expired=true 제외
        inventories = inventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        // 잔여 유통기한 < 10% 재고는 expired 처리
        for (Inventory inv : new ArrayList<>(inventories)) {
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate());
                if (remainingPct < 10) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    inventories.remove(inv);
                }
            }
        }

        // FIFO/FEFO 정렬
        if (Boolean.TRUE.equals(product.getHasExpiry())) {
            // FEFO: 유통기한 오름차순, 잔여율 <30% 최우선
            inventories.sort((a, b) -> {
                boolean aLowShelfLife = isLowShelfLife(a);
                boolean bLowShelfLife = isLowShelfLife(b);

                if (aLowShelfLife && !bLowShelfLife) return -1;
                if (!aLowShelfLife && bLowShelfLife) return 1;

                if (a.getExpiryDate() == null && b.getExpiryDate() == null) {
                    return a.getReceivedAt().compareTo(b.getReceivedAt());
                }
                if (a.getExpiryDate() == null) return 1;
                if (b.getExpiryDate() == null) return -1;

                int expiryCmp = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCmp != 0) return expiryCmp;

                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO: 입고일 오름차순
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    private boolean isLowShelfLife(Inventory inventory) {
        if (inventory.getExpiryDate() == null || inventory.getManufactureDate() == null) {
            return false;
        }
        double remainingPct = calculateRemainingShelfLifePct(inventory.getExpiryDate(), inventory.getManufactureDate());
        return remainingPct < 30;
    }

    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) return 0;
        return (double) remainingDays / totalDays * 100;
    }

    private Backorder createBackorder(UUID shipmentLineId, UUID productId, int shortageQty) {
        Backorder backorder = new Backorder();
        backorder.setShipmentLineId(shipmentLineId);
        backorder.setProductId(productId);
        backorder.setShortageQty(shortageQty);
        backorder.setStatus("open");
        return backorderRepository.save(backorder);
    }

    private void triggerUrgentReorder(UUID productId, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);

        AutoReorderLog log = new AutoReorderLog();
        log.setProductId(productId);
        log.setTriggerType("URGENT_REORDER");
        log.setCurrentStock(currentStock);
        log.setMinQty(rule != null ? rule.getMinQty() : 0);
        log.setReorderQty(rule != null ? rule.getReorderQty() : 0);
        log.setTriggeredBy("SYSTEM");
        autoReorderLogRepository.save(log);
    }

    private void checkSafetyStockAndReorder(UUID productId) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(productId).orElse(null);
        if (rule == null) {
            return;
        }

        Integer totalAvailable = inventoryRepository.getTotalAvailableQuantityByProductId(productId);
        if (totalAvailable == null) totalAvailable = 0;

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProductId(productId);
            log.setTriggerType("SAFETY_STOCK_TRIGGER");
            log.setCurrentStock(totalAvailable);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(log);
        }
    }

    private void logStorageTypeMismatch(UUID shipmentId, Product product, Location location) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventType("STORAGE_TYPE_MISMATCH");
        auditLog.setEntityType("SHIPMENT_ORDER");
        auditLog.setEntityId(shipmentId);
        auditLog.setDetails("{\"productId\":\"" + product.getProductId() +
                           "\",\"productStorageType\":\"" + product.getStorageType() +
                           "\",\"locationId\":\"" + location.getLocationId() +
                           "\",\"locationStorageType\":\"" + location.getStorageType() + "\"}");
        auditLog.setPerformedBy("SYSTEM");
        auditLogRepository.save(auditLog);
    }

    private ShipmentOrderResponse convertToResponse(ShipmentOrder shipmentOrder) {
        ShipmentOrderResponse response = new ShipmentOrderResponse();
        response.setShipmentId(shipmentOrder.getShipmentId());
        response.setShipmentNumber(shipmentOrder.getShipmentNumber());
        response.setCustomerName(shipmentOrder.getCustomerName());
        response.setStatus(shipmentOrder.getStatus());
        response.setRequestedAt(shipmentOrder.getRequestedAt());
        response.setShippedAt(shipmentOrder.getShippedAt());

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentId(shipmentOrder.getShipmentId());
        List<ShipmentOrderResponse.ShipmentOrderLineResponse> lineResponses = lines.stream()
                .map(this::convertToLineResponse)
                .collect(Collectors.toList());
        response.setLines(lineResponses);

        return response;
    }

    private ShipmentOrderResponse.ShipmentOrderLineResponse convertToLineResponse(ShipmentOrderLine line) {
        ShipmentOrderResponse.ShipmentOrderLineResponse lineResponse = new ShipmentOrderResponse.ShipmentOrderLineResponse();
        lineResponse.setShipmentLineId(line.getShipmentLineId());
        lineResponse.setProductId(line.getProductId());
        lineResponse.setRequestedQty(line.getRequestedQty());
        lineResponse.setPickedQty(line.getPickedQty());
        lineResponse.setStatus(line.getStatus());
        return lineResponse;
    }

    private ShipmentOrderResponse.BackorderInfo convertToBackorderInfo(Backorder backorder) {
        ShipmentOrderResponse.BackorderInfo info = new ShipmentOrderResponse.BackorderInfo();
        info.setBackorderId(backorder.getBackorderId());
        info.setProductId(backorder.getProductId());
        info.setShortageQty(backorder.getShortageQty());
        return info;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public StockTransferResponse transferStock(StockTransferRequest request) {
        // 1. 기본 데이터 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("To location not found"));

        // 2. 기본 검증
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new IllegalArgumentException("From and to locations cannot be the same");
        }

        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new IllegalStateException("From location is frozen for cycle count");
        }

        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new IllegalStateException("To location is frozen for cycle count");
        }

        // 3. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        product, fromLocation, request.getLotNumber())
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found at from location"));

        // 4. 재고 수량 체크
        if (inventory.getQuantity() < request.getQuantity()) {
            throw new IllegalStateException("Insufficient quantity at from location");
        }

        // 5. 유통기한 체크 (유통기한 관리 상품인 경우)
        if (Boolean.TRUE.equals(product.getHasExpiry()) && inventory.getExpiryDate() != null) {
            LocalDate today = LocalDate.now();

            // 유통기한 만료 체크
            if (inventory.getExpiryDate().isBefore(today)) {
                throw new IllegalStateException("Cannot transfer expired inventory");
            }

            // 잔여 유통기한 비율 계산
            if (inventory.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                        inventory.getManufactureDate(),
                        inventory.getExpiryDate()
                );

                // 잔여 유통기한 < 10%: SHIPPING zone만 허용
                if (remainingPct < 10.0) {
                    if (!"SHIPPING".equals(toLocation.getZone())) {
                        throw new IllegalStateException("Inventory with less than 10% shelf life can only be transferred to SHIPPING zone");
                    }
                }
            }
        }

        // 6. 보관 유형 호환성 체크
        validateStorageCompatibility(product, toLocation);

        // 7. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 8. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new IllegalStateException("To location capacity exceeded");
        }

        // 9. 대량 이동 승인 여부 판단 (80% 이상)
        double transferPercentage = (double) request.getQuantity() / inventory.getQuantity() * 100;
        String transferStatus = transferPercentage >= 80.0 ? "pending_approval" : "immediate";

        // 10. 재고 이동 엔티티 생성
        StockTransfer transfer = new StockTransfer();
        transfer.setProduct(product);
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setQuantity(request.getQuantity());
        transfer.setLotNumber(request.getLotNumber());
        transfer.setReason(request.getReason());
        transfer.setTransferStatus(transferStatus);
        transfer.setTransferredBy(request.getTransferredBy());

        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 이동 실행
        if ("immediate".equals(transferStatus)) {
            executeTransfer(transfer, inventory, fromLocation, toLocation, product);
        }

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (!"pending_approval".equals(transfer.getTransferStatus())) {
            throw new IllegalStateException("Transfer is not pending approval");
        }

        // 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        transfer.getProduct(), transfer.getFromLocation(), transfer.getLotNumber())
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));

        // 재고 이동 실행
        executeTransfer(transfer, inventory, transfer.getFromLocation(), transfer.getToLocation(), transfer.getProduct());

        // 상태 업데이트
        transfer.setTransferStatus("approved");
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (!"pending_approval".equals(transfer.getTransferStatus())) {
            throw new IllegalStateException("Transfer is not pending approval");
        }

        transfer.setTransferStatus("rejected");
        transfer.setApprovedBy(approvedBy);
        stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransferById(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // 재고 이동 실행 (private helper)
    private void executeTransfer(StockTransfer transfer, Inventory fromInventory,
                                  Location fromLocation, Location toLocation, Product product) {
        // 1. 출발지 재고 차감
        fromInventory.setQuantity(fromInventory.getQuantity() - transfer.getQuantity());
        inventoryRepository.save(fromInventory);

        // 2. 출발지 로케이션 수량 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 재고가 있으면 증가, 없으면 신규 생성)
        Inventory toInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                product, toLocation, transfer.getLotNumber()
        ).orElse(null);

        if (toInventory != null) {
            toInventory.setQuantity(toInventory.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(toInventory);
        } else {
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(toLocation);
            newInventory.setQuantity(transfer.getQuantity());
            newInventory.setLotNumber(transfer.getLotNumber());
            newInventory.setExpiryDate(fromInventory.getExpiryDate());
            newInventory.setManufactureDate(fromInventory.getManufactureDate());
            newInventory.setReceivedAt(fromInventory.getReceivedAt()); // 원래 입고일 유지
            newInventory.setIsExpired(fromInventory.getIsExpired());
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 수량 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone)
        if ("STORAGE".equals(fromLocation.getZone())) {
            checkSafetyStock(product);
        }
    }

    // 보관 유형 호환성 검증
    private void validateStorageCompatibility(Product product, Location location) {
        String productStorageType = product.getStorageType();
        String locationStorageType = location.getStorageType();

        // FROZEN 상품 → FROZEN만 허용
        if ("FROZEN".equals(productStorageType) && !"FROZEN".equals(locationStorageType)) {
            throw new IllegalStateException("FROZEN products can only be stored in FROZEN locations");
        }

        // COLD 상품 → COLD 또는 FROZEN 허용
        if ("COLD".equals(productStorageType) &&
            !("COLD".equals(locationStorageType) || "FROZEN".equals(locationStorageType))) {
            throw new IllegalStateException("COLD products can only be stored in COLD or FROZEN locations");
        }

        // AMBIENT 상품 → AMBIENT만 허용
        if ("AMBIENT".equals(productStorageType) && !"AMBIENT".equals(locationStorageType)) {
            throw new IllegalStateException("AMBIENT products can only be stored in AMBIENT locations");
        }

        // HAZMAT 상품 → HAZMAT zone만 허용
        if ("HAZMAT".equals(product.getCategory()) && !"HAZMAT".equals(location.getZone())) {
            throw new IllegalStateException("HAZMAT products can only be stored in HAZMAT zone");
        }
    }

    // HAZMAT 혼적 금지 검증
    private void validateHazmatSegregation(Product product, Location toLocation) {
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        boolean isHazmat = "HAZMAT".equals(product.getCategory());

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = "HAZMAT".equals(inv.getProduct().getCategory());

            if (isHazmat && !existingIsHazmat) {
                throw new IllegalStateException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }

            if (!isHazmat && existingIsHazmat) {
                throw new IllegalStateException("Cannot mix non-HAZMAT and HAZMAT products in the same location");
            }
        }
    }

    // 잔여 유통기한 비율 계산
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (double) remainingDays / totalDays * 100.0;
    }

    // 안전재고 체크
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return;
        }

        // STORAGE zone의 전체 재고 합산
        List<Inventory> storageInventories = inventoryRepository.findByProduct(product).stream()
                .filter(inv -> "STORAGE".equals(inv.getLocation().getZone()))
                .collect(Collectors.toList());

        int totalStock = storageInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalStock <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerType("SAFETY_STOCK_TRIGGER");
            log.setCurrentStock(totalStock);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(log);
        }
    }
}


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- WMS Database Schema - PostgreSQL 15+

-- 1. 상품 마스터
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

-- 2. 로케이션
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

-- 3. 재고
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

-- 4. 공급업체
CREATE TABLE suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 5. 공급업체 페널티
CREATE TABLE supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 6. 발주서
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

-- 7. 입고
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

-- 8. 출고 지시서
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

-- 9. 백오더
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

-- 10. 재고 이동
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

-- 11. 재고 조정
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

-- 12. 감사 로그
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. 안전재고 기준
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. 자동 재발주 이력
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

-- 15. 계절 설정
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. 실사 세션
CREATE TABLE cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

-- 인덱스 생성
CREATE INDEX idx_inventory_product_location ON inventory(product_id, location_id);
CREATE INDEX idx_inventory_expiry ON inventory(expiry_date);
CREATE INDEX idx_inventory_received ON inventory(received_at);
CREATE INDEX idx_supplier_penalties_supplier ON supplier_penalties(supplier_id, created_at);
CREATE INDEX idx_po_lines_po ON purchase_order_lines(po_id);
CREATE INDEX idx_receipt_lines_receipt ON inbound_receipt_lines(receipt_id);
CREATE INDEX idx_shipment_lines_shipment ON shipment_order_lines(shipment_id);
CREATE INDEX idx_locations_zone ON locations(zone);
CREATE INDEX idx_locations_frozen ON locations(is_frozen);

