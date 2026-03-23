package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.PickingResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request
    ) {
        ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{shipmentId}/pick")
    public ResponseEntity<ApiResponse<PickingResponse>> pickShipmentOrder(
            @PathVariable UUID shipmentId
    ) {
        PickingResponse response = shipmentOrderService.pickShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{shipmentId}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipmentOrder(
            @PathVariable UUID shipmentId
    ) {
        ShipmentOrderResponse response = shipmentOrderService.shipShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{shipmentId}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable UUID shipmentId
    ) {
        ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ShipmentOrderResponse>>> getShipmentOrders(
            Pageable pageable
    ) {
        Page<ShipmentOrderResponse> response = shipmentOrderService.getShipmentOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
