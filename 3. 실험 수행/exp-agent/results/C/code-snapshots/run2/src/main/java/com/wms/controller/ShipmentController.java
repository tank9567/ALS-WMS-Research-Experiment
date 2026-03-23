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
