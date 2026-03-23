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
