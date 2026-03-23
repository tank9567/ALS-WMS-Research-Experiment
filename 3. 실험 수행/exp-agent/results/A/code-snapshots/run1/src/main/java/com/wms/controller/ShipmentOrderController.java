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
