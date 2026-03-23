package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.PickingResult;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentService;
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
            @RequestBody ShipmentOrderRequest request) {
        ShipmentOrderResponse response = shipmentService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<PickingResult>> executePicking(@PathVariable UUID id) {
        PickingResult result = shipmentService.executePicking(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> confirmShipment(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentService.confirmShipment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentService.getShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        List<ShipmentOrderResponse> responses = shipmentService.getAllShipmentOrders();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
