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
