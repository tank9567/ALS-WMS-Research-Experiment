# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 완료 항목

### 1. Entity 클래스
- `CycleCount.java` - 실사 세션 관리
- `InventoryAdjustment.java` - 재고 조정 이력

### 2. Repository 인터페이스
- `CycleCountRepository.java` - 실사 데이터 접근
- `InventoryAdjustmentRepository.java` - 조정 이력 조회 (연속 조정 감시용 쿼리 포함)

### 3. DTO 클래스
- `CycleCountStartRequest.java` / `CycleCountResponse.java`
- `InventoryAdjustmentCreateRequest.java` / `InventoryAdjustmentApprovalRequest.java` / `InventoryAdjustmentResponse.java`

### 4. Service 비즈니스 로직 (ALS-WMS-ADJ-002 규칙 준수)
- ✅ **reason 필수** 검증
- ✅ **system_qty=0** 시 무조건 승인 필요
- ✅ **카테고리별 자동승인 임계치**: GENERAL=5%, FRESH=3%, HAZMAT=1%
- ✅ **HIGH_VALUE 전수 검증**: 차이 발생 시 무조건 승인 필요, audit_logs 기록, 재실사 권고 메시지
- ✅ **연속 조정 감시**: 7일 내 2회 이상 시 승인 격상, [연속조정감시] 태그 추가
- ✅ **실사 동결**: cycle_count 시작 시 is_frozen=true, 완료 시 해제
- ✅ **조정 후 안전재고 체크**: 자동 재발주 트리거
- ✅ **이력 불변**: DELETE/UPDATE 로직 없음 (Repository에만 save 존재)

### 5. Controller REST API 엔드포인트
- `POST /api/v1/cycle-counts` - 실사 시작
- `POST /api/v1/cycle-counts/{id}/complete` - 실사 완료
- `POST /api/v1/inventory-adjustments` - 조정 생성
- `POST /api/v1/inventory-adjustments/{id}/approve` - 승인
- `POST /api/v1/inventory-adjustments/{id}/reject` - 거부
- `GET /api/v1/inventory-adjustments/{id}` - 상세 조회
- `GET /api/v1/inventory-adjustments` - 목록 조회

모든 비즈니스 규칙이 ALS-WMS-ADJ-002 문서에 명시된 대로 정확히 구현되었습니다.


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
    <name>WMS Inbound Processing</name>
    <description>Warehouse Management System - Inbound Receipt Processing</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
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
// FILE: src\main\java\com\wms\adjustment\controller\InventoryAdjustmentController.java
============================================================
package com.wms.adjustment.controller;

import com.wms.adjustment.dto.*;
import com.wms.adjustment.service.InventoryAdjustmentService;
import com.wms.inbound.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    /**
     * 실사 시작
     * POST /api/v1/cycle-counts
     */
    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @RequestBody CycleCountStartRequest request) {
        try {
            CycleCountResponse response = adjustmentService.startCycleCount(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("실사 시작 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 실사 완료
     * POST /api/v1/cycle-counts/{id}/complete
     */
    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable("id") UUID cycleCountId) {
        try {
            CycleCountResponse response = adjustmentService.completeCycleCount(cycleCountId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("실사 완료 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 생성
     * POST /api/v1/inventory-adjustments
     */
    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createAdjustment(
            @RequestBody InventoryAdjustmentCreateRequest request) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.createAdjustment(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 생성 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 승인
     * POST /api/v1/inventory-adjustments/{id}/approve
     */
    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @RequestBody InventoryAdjustmentApprovalRequest request) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.approveAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 승인 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 거부
     * POST /api/v1/inventory-adjustments/{id}/reject
     */
    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @RequestBody InventoryAdjustmentApprovalRequest request) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.rejectAdjustment(adjustmentId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 거부 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 상세 조회
     * GET /api/v1/inventory-adjustments/{id}
     */
    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getAdjustment(
            @PathVariable("id") UUID adjustmentId) {
        try {
            InventoryAdjustmentResponse response = adjustmentService.getAdjustment(adjustmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 재고 조정 목록 조회
     * GET /api/v1/inventory-adjustments
     */
    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<List<InventoryAdjustmentResponse>>> getAdjustments(
            @RequestParam(required = false) String status) {
        try {
            List<InventoryAdjustmentResponse> response = adjustmentService.getAdjustments(status);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("재고 조정 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\adjustment\dto\CycleCountResponse.java
============================================================
package com.wms.adjustment.dto;

import com.wms.adjustment.entity.CycleCount;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {

    private UUID cycleCountId;
    private UUID locationId;
    private String locationCode;
    private CycleCount.CycleCountStatus status;
    private String startedBy;
    private Instant startedAt;
    private Instant completedAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .cycleCountId(cycleCount.getCycleCountId())
                .locationId(cycleCount.getLocation().getLocationId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus())
                .startedBy(cycleCount.getStartedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\adjustment\dto\CycleCountStartRequest.java
============================================================
package com.wms.adjustment.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountStartRequest {

    private UUID locationId;
    private String startedBy;
}


============================================================
// FILE: src\main\java\com\wms\adjustment\dto\InventoryAdjustmentApprovalRequest.java
============================================================
package com.wms.adjustment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentApprovalRequest {

    private String approvedBy;
}


============================================================
// FILE: src\main\java\com\wms\adjustment\dto\InventoryAdjustmentCreateRequest.java
============================================================
package com.wms.adjustment.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentCreateRequest {

    private UUID productId;
    private UUID locationId;
    private Integer actualQty;
    private String reason;
    private String adjustedBy;
}


============================================================
// FILE: src\main\java\com\wms\adjustment\dto\InventoryAdjustmentResponse.java
============================================================
package com.wms.adjustment.dto;

import com.wms.adjustment.entity.InventoryAdjustment;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {

    private UUID adjustmentId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private Boolean requiresApproval;
    private InventoryAdjustment.ApprovalStatus approvalStatus;
    private String approvedBy;
    private String adjustedBy;
    private Instant createdAt;
    private Instant approvedAt;
    private String warning;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .adjustmentId(adjustment.getAdjustmentId())
                .productId(adjustment.getProduct().getProductId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getLocationId())
                .locationCode(adjustment.getLocation().getCode())
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .difference(adjustment.getDifference())
                .reason(adjustment.getReason())
                .requiresApproval(adjustment.getRequiresApproval())
                .approvalStatus(adjustment.getApprovalStatus())
                .approvedBy(adjustment.getApprovedBy())
                .adjustedBy(adjustment.getAdjustedBy())
                .createdAt(adjustment.getCreatedAt())
                .approvedAt(adjustment.getApprovedAt())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\adjustment\entity\CycleCount.java
============================================================
package com.wms.adjustment.entity;

import com.wms.inbound.entity.Location;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCount {

    @Id
    @Column(name = "cycle_count_id", nullable = false)
    private UUID cycleCountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CycleCountStatus status;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum CycleCountStatus {
        IN_PROGRESS, COMPLETED
    }
}


============================================================
// FILE: src\main\java\com\wms\adjustment\entity\InventoryAdjustment.java
============================================================
package com.wms.adjustment.entity;

import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustment {

    @Id
    @Column(name = "adjustment_id", nullable = false)
    private UUID adjustmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(name = "difference", nullable = false)
    private Integer difference;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "adjusted_by", nullable = false, length = 100)
    private String adjustedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public enum ApprovalStatus {
        AUTO_APPROVED, PENDING, APPROVED, REJECTED
    }
}


============================================================
// FILE: src\main\java\com\wms\adjustment\repository\CycleCountRepository.java
============================================================
package com.wms.adjustment.repository;

import com.wms.adjustment.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    Optional<CycleCount> findByLocationLocationIdAndStatus(UUID locationId, CycleCount.CycleCountStatus status);

    List<CycleCount> findByStatus(CycleCount.CycleCountStatus status);
}


============================================================
// FILE: src\main\java\com\wms\adjustment\repository\InventoryAdjustmentRepository.java
============================================================
package com.wms.adjustment.repository;

import com.wms.adjustment.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus approvalStatus);

    @Query("SELECT ia FROM InventoryAdjustment ia " +
           "WHERE ia.product.productId = :productId " +
           "AND ia.location.locationId = :locationId " +
           "AND ia.createdAt >= :since")
    List<InventoryAdjustment> findRecentAdjustmentsByProductAndLocation(
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("since") Instant since
    );
}


============================================================
// FILE: src\main\java\com\wms\adjustment\service\InventoryAdjustmentService.java
============================================================
package com.wms.adjustment.service;

import com.wms.adjustment.dto.*;
import com.wms.adjustment.entity.CycleCount;
import com.wms.adjustment.entity.InventoryAdjustment;
import com.wms.adjustment.repository.CycleCountRepository;
import com.wms.adjustment.repository.InventoryAdjustmentRepository;
import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.outbound.entity.AuditLog;
import com.wms.outbound.entity.AutoReorderLog;
import com.wms.outbound.entity.SafetyStockRule;
import com.wms.outbound.repository.AuditLogRepository;
import com.wms.outbound.repository.AutoReorderLogRepository;
import com.wms.outbound.repository.SafetyStockRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 실사 시작
     * ALS-WMS-ADJ-002: 실사 시작 시 로케이션 동결 (is_frozen=true)
     */
    @Transactional
    public CycleCountResponse startCycleCount(CycleCountStartRequest request) {
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 이미 실사 진행 중인지 체크
        cycleCountRepository.findByLocationLocationIdAndStatus(location.getLocationId(), CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new IllegalStateException("이미 실사가 진행 중인 로케이션입니다: " + location.getCode());
                });

        // 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
                .cycleCountId(UUID.randomUUID())
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    /**
     * 실사 완료
     * ALS-WMS-ADJ-002: 실사 완료 시 로케이션 동결 해제 (is_frozen=false)
     */
    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new IllegalArgumentException("실사 세션을 찾을 수 없습니다: " + cycleCountId));

        if (cycleCount.getStatus() == CycleCount.CycleCountStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 실사입니다.");
        }

        // 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(Instant.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    /**
     * 재고 조정 생성
     * ALS-WMS-ADJ-002: 모든 조정 규칙 적용
     */
    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentCreateRequest request) {
        // 사유 필수 체크
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("조정 사유는 필수입니다.");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + request.getProductId()));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 시스템 재고 조회
        Integer systemQty = inventoryRepository.findByProductProductIdAndLocationLocationId(
                        product.getProductId(), location.getLocationId())
                .map(Inventory::getQuantity)
                .orElse(0);

        Integer actualQty = request.getActualQty();
        Integer difference = actualQty - systemQty;

        // 조정으로 재고가 음수가 되는 것 방지
        if (actualQty < 0) {
            throw new IllegalArgumentException("실제 수량은 음수가 될 수 없습니다.");
        }

        // 승인 필요 여부 및 자동승인 결정
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        String reason = request.getReason();

        // 1. system_qty = 0인 경우 무조건 승인 필요
        if (systemQty == 0 && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 2. HIGH_VALUE 카테고리: 차이가 0이 아니면 무조건 승인 필요 (자동승인 없음)
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 3. 연속 조정 감시: 최근 7일 내 동일 상품+로케이션 조정이 2회 이상이면 승인 필요
        else {
            Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
            List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustmentsByProductAndLocation(
                    product.getProductId(), location.getLocationId(), sevenDaysAgo);

            if (recentAdjustments.size() >= 2) {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                reason = "[연속조정감시] " + reason;
            }
            // 4. 카테고리별 자동승인 임계치 체크
            else if (systemQty > 0) {
                double diffPct = Math.abs(difference) * 100.0 / systemQty;
                double threshold = getCategoryThreshold(product.getCategory());

                if (diffPct > threshold) {
                    requiresApproval = true;
                    approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                }
            }
        }

        // 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .adjustmentId(UUID.randomUUID())
                .product(product)
                .location(location)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(reason)
                .requiresApproval(requiresApproval)
                .approvalStatus(approvalStatus)
                .adjustedBy(request.getAdjustedBy())
                .build();

        adjustment = adjustmentRepository.save(adjustment);

        // 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustmentToInventory(adjustment);
            checkSafetyStockAfterAdjustment(product);
        }

        InventoryAdjustmentResponse response = InventoryAdjustmentResponse.from(adjustment);

        // HIGH_VALUE 경고 메시지
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            response.setWarning("고가품 조정입니다. 해당 로케이션 전체 재실사를 권고합니다.");
        }

        return response;
    }

    /**
     * 재고 조정 승인
     * ALS-WMS-ADJ-002: 승인 시 재고 반영, HIGH_VALUE는 audit_logs 기록
     */
    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, InventoryAdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다.");
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        adjustment = adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustmentToInventory(adjustment);

        // HIGH_VALUE 카테고리면 감사 로그 기록
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            recordHighValueAuditLog(adjustment);
        }

        // 안전재고 체크
        checkSafetyStockAfterAdjustment(adjustment.getProduct());

        InventoryAdjustmentResponse response = InventoryAdjustmentResponse.from(adjustment);

        // HIGH_VALUE 경고 메시지
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE && adjustment.getDifference() != 0) {
            response.setWarning("고가품 조정이 승인되었습니다. 해당 로케이션 전체 재실사를 권고합니다.");
        }

        return response;
    }

    /**
     * 재고 조정 거부
     * ALS-WMS-ADJ-002: 거부 시 재고 변동 없음
     */
    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, InventoryAdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다.");
        }

        // 거부 처리 (재고 변동 없음)
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        adjustment = adjustmentRepository.save(adjustment);

        return InventoryAdjustmentResponse.from(adjustment);
    }

    /**
     * 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        return InventoryAdjustmentResponse.from(adjustment);
    }

    /**
     * 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustments(String status) {
        List<InventoryAdjustment> adjustments;

        if (status != null && !status.isEmpty()) {
            InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.valueOf(status.toUpperCase());
            adjustments = adjustmentRepository.findByApprovalStatus(approvalStatus);
        } else {
            adjustments = adjustmentRepository.findAll();
        }

        return adjustments.stream()
                .map(InventoryAdjustmentResponse::from)
                .toList();
    }

    // === Private Helper Methods ===

    /**
     * 카테고리별 자동승인 임계치 반환
     * ALS-WMS-ADJ-002: GENERAL=5%, FRESH=3%, HAZMAT=1%, HIGH_VALUE=자동승인 없음
     */
    private double getCategoryThreshold(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 0.0; // 실제로는 이 메서드 호출 전에 필터링됨
        };
    }

    /**
     * 재고에 조정 반영
     * ALS-WMS-ADJ-002: 승인된 조정만 재고에 반영
     */
    private void applyAdjustmentToInventory(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        Integer newQty = adjustment.getActualQty();

        Inventory inventory = inventoryRepository.findByProductProductIdAndLocationLocationId(
                        product.getProductId(), location.getLocationId())
                .orElse(null);

        if (inventory == null) {
            // 신규 재고 생성
            inventory = Inventory.builder()
                    .inventoryId(UUID.randomUUID())
                    .product(product)
                    .location(location)
                    .quantity(newQty)
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
        } else {
            // 기존 재고 수정
            int oldQty = inventory.getQuantity();
            inventory.setQuantity(newQty);

            // 로케이션 현재 적재량도 갱신
            int qtyChange = newQty - oldQty;
            location.setCurrentQty(location.getCurrentQty() + qtyChange);
            locationRepository.save(location);
        }

        inventoryRepository.save(inventory);
    }

    /**
     * HIGH_VALUE 조정 시 감사 로그 기록
     * ALS-WMS-ADJ-002: HIGH_VALUE 조정 반영 시 audit_logs 기록
     */
    private void recordHighValueAuditLog(InventoryAdjustment adjustment) {
        AuditLog auditLog = AuditLog.builder()
                .logId(UUID.randomUUID())
                .eventType(AuditLog.EventType.HIGH_VALUE_ADJUSTMENT)
                .product(adjustment.getProduct())
                .location(adjustment.getLocation())
                .referenceId(adjustment.getAdjustmentId())
                .referenceType("inventory_adjustment")
                .message(String.format("HIGH_VALUE 조정 승인: system_qty=%d, actual_qty=%d, difference=%d, approved_by=%s",
                        adjustment.getSystemQty(), adjustment.getActualQty(),
                        adjustment.getDifference(), adjustment.getApprovedBy()))
                .severity("HIGH")
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * 안전재고 체크 (조정 후)
     * ALS-WMS-ADJ-002: 조정 반영 후 안전재고 미달 시 자동 재발주
     */
    private void checkSafetyStockAfterAdjustment(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductIdAndIsActive(
                        product.getProductId(), true)
                .orElse(null);

        if (rule == null) {
            return;
        }

        // 전체 가용 재고 합산 (is_expired=false)
        Integer totalStock = inventoryRepository.findByProductProductIdAndIsExpired(
                        product.getProductId(), false)
                .stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주 로그 기록
        if (totalStock < rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .reorderLogId(UUID.randomUUID())
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();

            autoReorderLogRepository.save(reorderLog);
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\dto\ApiResponse.java
============================================================
package com.wms.inbound.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorInfo error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(new ErrorInfo(code, message))
            .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\dto\InboundReceiptCreateRequest.java
============================================================
package com.wms.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptCreateRequest {

    @NotNull(message = "PO ID는 필수입니다")
    private UUID poId;

    @NotBlank(message = "수령자는 필수입니다")
    private String receivedBy;

    @NotEmpty(message = "입고 품목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "상품 ID는 필수입니다")
        private UUID productId;

        @NotNull(message = "로케이션 ID는 필수입니다")
        private UUID locationId;

        @NotNull(message = "수량은 필수입니다")
        private Integer quantity;

        private String lotNumber;

        private LocalDate expiryDate;

        private LocalDate manufactureDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\dto\InboundReceiptResponse.java
============================================================
package com.wms.inbound.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {

    private UUID receiptId;
    private UUID poId;
    private String poNumber;
    private String status;
    private String receivedBy;
    private Instant receivedAt;
    private Instant confirmedAt;
    private List<InboundReceiptLineResponse> lines;

    @Getter
    @Setter
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
// FILE: src\main\java\com\wms\inbound\entity\InboundReceipt.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id", nullable = false)
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
    private Instant receivedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum ReceiptStatus {
        inspecting, pending_approval, confirmed, rejected
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\InboundReceiptLine.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptLine {

    @Id
    @Column(name = "receipt_line_id", nullable = false)
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
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Inventory.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @Column(name = "inventory_id", nullable = false)
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
    private Instant receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Location.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Product.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
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
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ProductCategory {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrder.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "po_number", nullable = false, unique = true, length = 30)
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
    private Instant orderedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        pending, partial, completed, cancelled, hold
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrderLine.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"po_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {

    @Id
    @Column(name = "po_line_id", nullable = false)
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
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SafetyStockRule.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyStockRule {

    @Id
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SeasonalConfig.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonalConfig {

    @Id
    @Column(name = "season_id", nullable = false)
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
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Supplier.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum SupplierStatus {
        active, hold, inactive
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SupplierPenalty.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierPenalty {

    @Id
    @Column(name = "penalty_id", nullable = false)
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
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {

    @Query("SELECT irl FROM InboundReceiptLine irl WHERE irl.inboundReceipt.receiptId = :receiptId")
    List<InboundReceiptLine> findByReceiptId(@Param("receiptId") UUID receiptId);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\InboundReceiptRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\InventoryRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId " +
           "AND i.location.locationId = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductAndLocationAndLot(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\LocationRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\ProductRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\PurchaseOrderLineRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId " +
           "AND pol.product.productId = :productId")
    Optional<PurchaseOrderLine> findByPoAndProduct(@Param("poId") UUID poId, @Param("productId") UUID productId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId")
    List<PurchaseOrderLine> findByPoId(@Param("poId") UUID poId);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\PurchaseOrderRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Modifying
    @Query("UPDATE PurchaseOrder p SET p.status = 'hold' WHERE p.supplier.supplierId = :supplierId AND p.status = 'pending'")
    void holdPendingOrdersBySupplier(@Param("supplierId") UUID supplierId);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\SeasonalConfigRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.SeasonalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonalConfigRepository extends JpaRepository<SeasonalConfig, UUID> {

    @Query("SELECT sc FROM SeasonalConfig sc WHERE sc.isActive = true " +
           "AND :date BETWEEN sc.startDate AND sc.endDate")
    Optional<SeasonalConfig> findActiveSeason(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\SupplierPenaltyRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(p) FROM SupplierPenalty p WHERE p.supplier.supplierId = :supplierId " +
           "AND p.createdAt >= :since")
    long countBySupplierId30Days(@Param("supplierId") UUID supplierId, @Param("since") Instant since);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\SupplierRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\service\InboundReceiptService.java
============================================================
package com.wms.inbound.service;

import com.wms.inbound.dto.InboundReceiptCreateRequest;
import com.wms.inbound.dto.InboundReceiptResponse;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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

    /**
     * 입고 등록 (검수 상태)
     */
    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptCreateRequest request) {
        // 1. PO 검증
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
            .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다"));

        // 2. 입고 Receipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
            .receiptId(UUID.randomUUID())
            .purchaseOrder(po)
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .receivedAt(Instant.now())
            .build();

        InboundReceipt.ReceiptStatus finalStatus = InboundReceipt.ReceiptStatus.inspecting;

        // 3. 각 품목별 검증 및 입고 라인 생성
        for (InboundReceiptCreateRequest.InboundReceiptLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineRequest.getProductId()));

            Location location = locationRepository.findById(lineRequest.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineRequest.getLocationId()));

            // 3-1. 실사 동결 체크
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("실사 동결된 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineRequest.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (lineRequest.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3-4. 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                    lineRequest.getManufactureDate(),
                    lineRequest.getExpiryDate(),
                    LocalDate.now()
                );

                int minPct = product.getMinRemainingShelfLifePct() != null ?
                    product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minPct) {
                    // 잔여율 < 30% → 입고 거부 + 페널티
                    recordSupplierPenalty(po, SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                        String.format("유통기한 부족: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, minPct));
                    throw new IllegalStateException(
                        String.format("유통기한 잔여율 부족: %s (%.1f%% < %d%%)",
                            product.getSku(), remainingPct, minPct));
                } else if (remainingPct >= minPct && remainingPct <= 50) {
                    // 잔여율 30~50% → 승인 대기
                    finalStatus = InboundReceipt.ReceiptStatus.pending_approval;
                }
            }

            // 3-5. 초과입고 검증
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoAndProduct(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "발주서에 해당 상품이 없습니다: " + product.getSku()));

            validateOverReceive(po, product, poLine, lineRequest.getQuantity());

            // 3-6. 입고 라인 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                .receiptLineId(UUID.randomUUID())
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineRequest.getQuantity())
                .lotNumber(lineRequest.getLotNumber())
                .expiryDate(lineRequest.getExpiryDate())
                .manufactureDate(lineRequest.getManufactureDate())
                .build();

            inboundReceiptLineRepository.save(receiptLine);
        }

        // 4. 최종 상태 설정 및 저장
        receipt.setStatus(finalStatus);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 확정
     */
    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중 또는 승인 대기 상태에서만 확정할 수 있습니다");
        }

        // 1. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
            ).orElse(null);

            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            } else {
                inventory = Inventory.builder()
                    .inventoryId(UUID.randomUUID())
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            }
            inventoryRepository.save(inventory);

            // 로케이션 적재량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // PO 라인 received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoAndProduct(
                receipt.getPurchaseOrder().getPoId(),
                line.getProduct().getProductId()
            ).orElseThrow();

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 2. PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getPoId());

        // 3. 입고 전표 상태 갱신
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(Instant.now());
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중 또는 승인 대기 상태에서만 거부할 수 있습니다");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 유통기한 경고 승인
     */
    @Transactional
    public InboundReceiptResponse approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태에서만 승인할 수 있습니다");
        }

        // 승인 시 검수 중 상태로 변경 (이후 confirm으로 확정)
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 상세 조회
     */
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        return mapToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    public Page<InboundReceiptResponse> getInboundReceipts(Pageable pageable) {
        return inboundReceiptRepository.findAll(pageable)
            .map(this::mapToResponse);
    }

    // ===== 내부 유틸리티 메서드 =====

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                    "위험물은 HAZMAT zone 로케이션에만 입고할 수 있습니다");
            }
        }

        if (productType == Product.StorageType.FROZEN) {
            if (locationType != Product.StorageType.FROZEN) {
                throw new IllegalStateException(
                    "FROZEN 상품은 FROZEN 로케이션에만 입고할 수 있습니다");
            }
        } else if (productType == Product.StorageType.COLD) {
            if (locationType != Product.StorageType.COLD &&
                locationType != Product.StorageType.FROZEN) {
                throw new IllegalStateException(
                    "COLD 상품은 COLD 또는 FROZEN 로케이션에만 입고할 수 있습니다");
            }
        } else if (productType == Product.StorageType.AMBIENT) {
            if (locationType != Product.StorageType.AMBIENT) {
                throw new IllegalStateException(
                    "AMBIENT 상품은 AMBIENT 로케이션에만 입고할 수 있습니다");
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
     * 초과입고 검증
     */
    private void validateOverReceive(PurchaseOrder po, Product product,
                                      PurchaseOrderLine poLine, int incomingQty) {
        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalReceiving = receivedQty + incomingQty;

        // 카테고리별 기본 허용률
        double baseTolerance = getCategoryTolerance(product.getCategory());

        // HAZMAT은 무조건 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            baseTolerance = 0.0;
        } else {
            // PO 유형별 가중치
            double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());
            baseTolerance *= poTypeMultiplier;

            // 성수기 가중치
            BigDecimal seasonMultiplier = getSeasonalMultiplier(LocalDate.now());
            baseTolerance *= seasonMultiplier.doubleValue();
        }

        int maxAllowed = (int) Math.floor(orderedQty * (1.0 + baseTolerance));

        if (totalReceiving > maxAllowed) {
            // 초과입고 → 페널티 기록 후 거부
            recordSupplierPenalty(po, SupplierPenalty.PenaltyType.OVER_DELIVERY,
                String.format("초과입고: %s (입고 %d > 허용 %d)",
                    product.getSku(), totalReceiving, maxAllowed));

            throw new IllegalStateException(
                String.format("초과입고 거부: %s (입고 %d > 허용 %d, 허용률 %.1f%%)",
                    product.getSku(), totalReceiving, maxAllowed, baseTolerance * 100));
        }
    }

    /**
     * 카테고리별 초과입고 허용률
     */
    private double getCategoryTolerance(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 0.10;  // 10%
            case FRESH -> 0.05;    // 5%
            case HAZMAT -> 0.0;    // 0%
            case HIGH_VALUE -> 0.03; // 3%
        };
    }

    /**
     * PO 유형별 가중치
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    /**
     * 성수기 가중치 조회
     */
    private BigDecimal getSeasonalMultiplier(LocalDate date) {
        return seasonalConfigRepository.findActiveSeason(date)
            .map(SeasonalConfig::getMultiplier)
            .orElse(BigDecimal.ONE);
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(PurchaseOrder po, SupplierPenalty.PenaltyType type, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
            .penaltyId(UUID.randomUUID())
            .supplier(po.getSupplier())
            .penaltyType(type)
            .description(description)
            .poId(po.getPoId())
            .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 체크
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId30Days(
            po.getSupplier().getSupplierId(), since);

        if (penaltyCount >= 3) {
            // pending PO를 hold로 변경
            purchaseOrderRepository.holdPendingOrdersBySupplier(po.getSupplier().getSupplierId());
        }
    }

    /**
     * PO 상태 갱신 (모든 라인 완납 여부 체크)
     */
    private void updatePurchaseOrderStatus(UUID poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId).orElseThrow();
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPoId(poId);

        boolean allFulfilled = lines.stream()
            .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream()
            .anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity → Response 매핑
     */
    private InboundReceiptResponse mapToResponse(InboundReceipt receipt) {
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receipt.getReceiptId());

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPurchaseOrder().getPoId())
            .poNumber(receipt.getPurchaseOrder().getPoNumber())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .lines(lines.stream().map(this::mapLineToResponse).collect(Collectors.toList()))
            .build();
    }

    private InboundReceiptResponse.InboundReceiptLineResponse mapLineToResponse(InboundReceiptLine line) {
        return InboundReceiptResponse.InboundReceiptLineResponse.builder()
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
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\controller\ShipmentOrderController.java
============================================================
package com.wms.outbound.controller;

import com.wms.inbound.dto.ApiResponse;
import com.wms.outbound.dto.ShipmentOrderCreateRequest;
import com.wms.outbound.dto.ShipmentOrderResponse;
import com.wms.outbound.service.ShipmentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    /**
     * 출고 지시서 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
        @RequestBody ShipmentOrderCreateRequest request
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 피킹 실행 + 출고 확정 (통합)
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> executePickingAndShip(
        @PathVariable("id") UUID shipmentOrderId
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.executePickingAndShip(shipmentOrderId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 출고 지시서 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
        @PathVariable("id") UUID shipmentOrderId
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentOrderId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 출고 지시서 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        try {
            List<ShipmentOrderResponse> response = shipmentOrderService.getAllShipmentOrders();
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\ShipmentOrderCreateRequest.java
============================================================
package com.wms.outbound.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderCreateRequest {
    private String orderNumber;
    private String customerName;
    private List<ShipmentLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineRequest {
        private UUID productId;
        private Integer lineNumber;
        private Integer requestedQty;
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\ShipmentOrderResponse.java
============================================================
package com.wms.outbound.dto;

import com.wms.outbound.entity.ShipmentOrder;
import com.wms.outbound.entity.ShipmentOrderLine;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID shipmentOrderId;
    private String orderNumber;
    private String customerName;
    private String status;
    private Instant requestedAt;
    private Instant shippedAt;
    private List<ShipmentLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    public static ShipmentOrderResponse from(ShipmentOrder order) {
        return ShipmentOrderResponse.builder()
            .shipmentOrderId(order.getShipmentOrderId())
            .orderNumber(order.getOrderNumber())
            .customerName(order.getCustomerName())
            .status(order.getStatus().name())
            .requestedAt(order.getRequestedAt())
            .shippedAt(order.getShippedAt())
            .lines(order.getLines().stream()
                .map(ShipmentLineResponse::from)
                .collect(Collectors.toList()))
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineResponse {
        private UUID lineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer lineNumber;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;

        public static ShipmentLineResponse from(ShipmentOrderLine line) {
            return ShipmentLineResponse.builder()
                .lineId(line.getLineId())
                .productId(line.getProduct().getProductId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .lineNumber(line.getLineNumber())
                .requestedQty(line.getRequestedQty())
                .pickedQty(line.getPickedQty())
                .status(line.getStatus().name())
                .createdAt(line.getCreatedAt())
                .updatedAt(line.getUpdatedAt())
                .build();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\AuditLog.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @Column(name = "log_id", nullable = false)
    private UUID logId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum EventType {
        STORAGE_TYPE_MISMATCH, HAZMAT_VIOLATION, HIGH_VALUE_ADJUSTMENT, PICKING_WARNING
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\AutoReorderLog.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoReorderLog {

    @Id
    @Column(name = "log_id", nullable = false)
    private UUID logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private TriggerReason triggerReason;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum TriggerReason {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\Backorder.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backorder {

    @Id
    @Column(name = "backorder_id", nullable = false)
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_line_id", nullable = false)
    private ShipmentOrderLine shipmentLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private BackorderStatus status = BackorderStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\SafetyStockRule.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyStockRule {

    @Id
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\ShipmentOrder.java
============================================================
package com.wms.outbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrder {

    @Id
    @Column(name = "shipment_order_id", nullable = false)
    private UUID shipmentOrderId;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ShipmentStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ShipmentStatus {
        PENDING, PICKING, PARTIAL, SHIPPED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\ShipmentOrderLine.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderLine {

    @Id
    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    @Builder.Default
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private LineStatus status = LineStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum LineStatus {
        PENDING, PICKED, PARTIAL, BACKORDERED
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\repository\AuditLogRepository.java
============================================================
package com.wms.outbound.repository;

import com.wms.outbound.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\outbound\repository\AutoReorderLogRepository.java
============================================================
package com.wms.outbound.repository;

import com.wms.outbound.entity.AutoReorderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AutoReorderLogRepository extends JpaRepository<AutoReorderLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\outbound\repository\BackorderRepository.java
============================================================
package com.wms.outbound.repository;

import com.wms.outbound.entity.Backorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackorderRepository extends JpaRepository<Backorder, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\outbound\repository\SafetyStockRuleRepository.java
============================================================
package com.wms.outbound.repository;

import com.wms.outbound.entity.SafetyStockRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, UUID> {
    Optional<SafetyStockRule> findByProduct_ProductIdAndIsActive(UUID productId, Boolean isActive);
}


============================================================
// FILE: src\main\java\com\wms\outbound\repository\ShipmentOrderLineRepository.java
============================================================
package com.wms.outbound.repository;

import com.wms.outbound.entity.ShipmentOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShipmentOrderLineRepository extends JpaRepository<ShipmentOrderLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\outbound\repository\ShipmentOrderRepository.java
============================================================
package com.wms.outbound.repository;

import com.wms.outbound.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {
    Optional<ShipmentOrder> findByOrderNumber(String orderNumber);
}


============================================================
// FILE: src\main\java\com\wms\outbound\service\ShipmentOrderService.java
============================================================
package com.wms.outbound.service;

import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.outbound.dto.ShipmentOrderCreateRequest;
import com.wms.outbound.dto.ShipmentOrderResponse;
import com.wms.outbound.entity.*;
import com.wms.outbound.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderCreateRequest request) {
        // 출고 지시서 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
            .shipmentOrderId(UUID.randomUUID())
            .orderNumber(request.getOrderNumber())
            .customerName(request.getCustomerName())
            .status(ShipmentOrder.ShipmentStatus.PENDING)
            .requestedAt(Instant.now())
            .build();

        // 라인 생성
        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderCreateRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineReq.getProductId()));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                .lineId(UUID.randomUUID())
                .shipmentOrder(shipmentOrder)
                .product(product)
                .lineNumber(lineReq.getLineNumber())
                .requestedQty(lineReq.getRequestedQty())
                .pickedQty(0)
                .status(ShipmentOrderLine.LineStatus.PENDING)
                .build();
            lines.add(line);
        }

        shipmentOrder.setLines(lines);

        // HAZMAT + FRESH 분리 출고 체크 (ALS-WMS-OUT-002 Constraint)
        shipmentOrder = handleHazmatFreshSeparation(shipmentOrder);

        ShipmentOrder saved = shipmentOrderRepository.save(shipmentOrder);
        return ShipmentOrderResponse.from(saved);
    }

    /**
     * HAZMAT + FRESH 분리 출고 처리 (ALS-WMS-OUT-002 Constraint)
     * 동일 출고 지시서에 HAZMAT + FRESH 상품이 공존하면 분리 출고
     */
    private ShipmentOrder handleHazmatFreshSeparation(ShipmentOrder order) {
        boolean hasHazmat = order.getLines().stream()
            .anyMatch(line -> line.getProduct().getCategory() == Product.ProductCategory.HAZMAT);
        boolean hasFresh = order.getLines().stream()
            .anyMatch(line -> line.getProduct().getCategory() == Product.ProductCategory.FRESH);

        if (hasHazmat && hasFresh) {
            // HAZMAT 상품만 별도 shipment_order로 분할
            List<ShipmentOrderLine> hazmatLines = order.getLines().stream()
                .filter(line -> line.getProduct().getCategory() == Product.ProductCategory.HAZMAT)
                .collect(Collectors.toList());

            List<ShipmentOrderLine> nonHazmatLines = order.getLines().stream()
                .filter(line -> line.getProduct().getCategory() != Product.ProductCategory.HAZMAT)
                .collect(Collectors.toList());

            // 원래 주문에는 비-HAZMAT만 남김
            order.setLines(nonHazmatLines);

            // HAZMAT 전용 주문 생성
            ShipmentOrder hazmatOrder = ShipmentOrder.builder()
                .shipmentOrderId(UUID.randomUUID())
                .orderNumber(order.getOrderNumber() + "-HAZMAT")
                .customerName(order.getCustomerName())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .requestedAt(order.getRequestedAt())
                .lines(hazmatLines)
                .build();

            // HAZMAT 라인들의 shipmentOrder 참조 갱신
            hazmatLines.forEach(line -> line.setShipmentOrder(hazmatOrder));

            shipmentOrderRepository.save(hazmatOrder);
            log.info("HAZMAT + FRESH separation: Created separate HAZMAT order {}", hazmatOrder.getOrderNumber());
        }

        return order;
    }

    @Transactional
    public ShipmentOrderResponse executePickingAndShip(UUID shipmentOrderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
            .orElseThrow(() -> new IllegalArgumentException("ShipmentOrder not found: " + shipmentOrderId));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new IllegalStateException("ShipmentOrder is not in PENDING status");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        // 각 라인별 피킹 실행
        for (ShipmentOrderLine line : order.getLines()) {
            executePicking(line);
        }

        // 모든 라인이 picked이면 shipped로 변경
        boolean allPicked = order.getLines().stream()
            .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED);

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            order.setShippedAt(Instant.now());
        } else {
            order.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }

        ShipmentOrder saved = shipmentOrderRepository.save(order);

        // 출고 후 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
        for (ShipmentOrderLine line : saved.getLines()) {
            checkSafetyStockAfterShipment(line.getProduct());
        }

        return ShipmentOrderResponse.from(saved);
    }

    /**
     * 피킹 실행 (ALS-WMS-OUT-002 Rule 및 Constraint 준수)
     */
    private void executePicking(ShipmentOrderLine line) {
        Product product = line.getProduct();
        Integer requestedQty = line.getRequestedQty();

        // 피킹 가능한 재고 조회 (FIFO/FEFO 적용)
        List<Inventory> pickableInventory = getPickableInventory(product);

        // 전체 가용 재고 계산
        int totalAvailable = pickableInventory.stream()
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 부분출고 의사결정 트리 (ALS-WMS-OUT-002 Constraint)
        if (totalAvailable == 0) {
            // 가용 재고 = 0: 전량 백오더
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
        } else {
            double ratio = (double) totalAvailable / requestedQty;

            if (ratio < 0.30) {
                // 가용 < 30%: 전량 백오더 (부분출고 안 함)
                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
                createBackorder(line, requestedQty);
            } else if (ratio >= 0.30 && ratio < 0.70) {
                // 30% ≤ 가용 < 70%: 부분출고 + 백오더 + 긴급발주 트리거
                int pickedQty = pickFromInventory(line, pickableInventory, totalAvailable);
                line.setPickedQty(pickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
                createBackorder(line, requestedQty - pickedQty);
                triggerUrgentReorder(product, totalAvailable);
            } else if (ratio >= 0.70 && ratio < 1.0) {
                // 70% ≤ 가용 < 100%: 부분출고 + 백오더
                int pickedQty = pickFromInventory(line, pickableInventory, totalAvailable);
                line.setPickedQty(pickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
                createBackorder(line, requestedQty - pickedQty);
            } else {
                // 가용 ≥ 100%: 전량 피킹
                int pickedQty = pickFromInventory(line, pickableInventory, requestedQty);
                line.setPickedQty(pickedQty);
                line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
            }
        }
    }

    /**
     * 피킹 가능한 재고 조회 (FIFO/FEFO, ALS-WMS-OUT-002 Constraint)
     */
    private List<Inventory> getPickableInventory(Product product) {
        LocalDate today = LocalDate.now();

        // 기본 피킹 제외 조건: is_expired=true, is_frozen=true, expiry_date < today
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> !inv.getIsExpired())
            .filter(inv -> !inv.getLocation().getIsFrozen())
            .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(today))
            .filter(inv -> inv.getQuantity() > 0)
            .collect(Collectors.toList());

        // HAZMAT 상품은 HAZMAT zone에서만 피킹 (ALS-WMS-OUT-002 Constraint)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            allInventory = allInventory.stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                .collect(Collectors.toList());
        }

        // 잔여 유통기한 < 10% 재고는 is_expired=true로 설정하고 제외 (ALS-WMS-OUT-002 Constraint)
        for (Inventory inv : allInventory) {
            if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(
                    inv.getExpiryDate(),
                    inv.getManufactureDate(),
                    today
                );
                if (remainingPct < 10.0) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                }
            }
        }

        // is_expired=true로 변경된 재고 제외
        allInventory = allInventory.stream()
            .filter(inv -> !inv.getIsExpired())
            .collect(Collectors.toList());

        // FIFO/FEFO 정렬 (ALS-WMS-OUT-002 Constraint)
        List<Inventory> sorted;
        if (product.getHasExpiry()) {
            // 유통기한 관리 상품: FEFO 우선, 잔여율 <30% 최우선
            sorted = allInventory.stream()
                .sorted(Comparator
                    .comparing((Inventory inv) -> {
                        // 잔여율 <30%를 최우선으로
                        if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                            double pct = calculateRemainingShelfLifePct(inv.getExpiryDate(), inv.getManufactureDate(), today);
                            return pct < 30.0 ? 0 : 1;
                        }
                        return 1;
                    })
                    .thenComparing(inv -> inv.getExpiryDate() != null ? inv.getExpiryDate() : LocalDate.MAX)
                    .thenComparing(Inventory::getReceivedAt))
                .collect(Collectors.toList());
        } else {
            // 비-유통기한 관리 상품: FIFO만
            sorted = allInventory.stream()
                .sorted(Comparator.comparing(Inventory::getReceivedAt))
                .collect(Collectors.toList());
        }

        return sorted;
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 재고에서 실제 피킹 수행
     */
    private int pickFromInventory(ShipmentOrderLine line, List<Inventory> pickableInventory, int qtyToPick) {
        int remaining = qtyToPick;
        Product product = line.getProduct();

        for (Inventory inv : pickableInventory) {
            if (remaining <= 0) break;

            // HAZMAT max_pick_qty 체크 (ALS-WMS-OUT-002 Constraint)
            int maxAllowed = remaining;
            if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
                maxAllowed = Math.min(remaining, product.getMaxPickQty());
            }

            int pickQty = Math.min(inv.getQuantity(), maxAllowed);

            // inventory.quantity 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inventoryRepository.save(inv);

            // locations.current_qty 차감
            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고 (ALS-WMS-OUT-002 Constraint)
            if (location.getStorageType() != product.getStorageType()) {
                AuditLog auditLog = AuditLog.builder()
                    .logId(UUID.randomUUID())
                    .eventType(AuditLog.EventType.STORAGE_TYPE_MISMATCH)
                    .product(product)
                    .location(location)
                    .referenceId(line.getShipmentOrder().getShipmentOrderId())
                    .referenceType("SHIPMENT_ORDER")
                    .message(String.format("Storage type mismatch: Product %s (%s) picked from Location %s (%s)",
                        product.getSku(), product.getStorageType(), location.getCode(), location.getStorageType()))
                    .severity("WARNING")
                    .build();
                auditLogRepository.save(auditLog);
            }

            remaining -= pickQty;
        }

        return qtyToPick - remaining;
    }

    /**
     * 백오더 생성
     */
    private void createBackorder(ShipmentOrderLine line, int shortageQty) {
        if (shortageQty <= 0) return;

        Backorder backorder = Backorder.builder()
            .backorderId(UUID.randomUUID())
            .shipmentOrder(line.getShipmentOrder())
            .shipmentLine(line)
            .product(line.getProduct())
            .shortageQty(shortageQty)
            .status(Backorder.BackorderStatus.OPEN)
            .build();
        backorderRepository.save(backorder);
    }

    /**
     * 긴급발주 트리거 (ALS-WMS-OUT-002 Constraint)
     */
    private void triggerUrgentReorder(Product product, int currentQty) {
        AutoReorderLog log = AutoReorderLog.builder()
            .logId(UUID.randomUUID())
            .product(product)
            .triggerReason(AutoReorderLog.TriggerReason.URGENT_REORDER)
            .currentQty(currentQty)
            .reorderQty(0) // 긴급발주는 수량 미지정
            .referenceType("URGENT_SHIPMENT")
            .build();
        autoReorderLogRepository.save(log);
        log.info("Urgent reorder triggered for product {}: current qty {}", product.getSku(), currentQty);
    }

    /**
     * 출고 후 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
     */
    private void checkSafetyStockAfterShipment(Product product) {
        // 전체 가용 재고 합산 (is_expired=true 제외)
        int totalAvailable = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository
            .findByProduct_ProductIdAndIsActive(product.getProductId(), true);

        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();
            if (totalAvailable < rule.getMinQty()) {
                // 안전재고 미달 -> 자동 재발주
                AutoReorderLog log = AutoReorderLog.builder()
                    .logId(UUID.randomUUID())
                    .product(product)
                    .triggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentQty(totalAvailable)
                    .reorderQty(rule.getReorderQty())
                    .referenceType("SAFETY_STOCK")
                    .build();
                autoReorderLogRepository.save(log);
                log.info("Safety stock trigger for product {}: current {} < min {}, reorder qty {}",
                    product.getSku(), totalAvailable, rule.getMinQty(), rule.getReorderQty());
            }
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
            .orElseThrow(() -> new IllegalArgumentException("ShipmentOrder not found: " + shipmentOrderId));
        return ShipmentOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
            .map(ShipmentOrderResponse::from)
            .collect(Collectors.toList());
    }
}


============================================================
// FILE: src\main\java\com\wms\transfer\controller\StockTransferController.java
============================================================
package com.wms.transfer.controller;

import com.wms.inbound.dto.ApiResponse;
import com.wms.transfer.dto.StockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    /**
     * 재고 이동 실행
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
        @RequestBody StockTransferRequest request
    ) {
        try {
            StockTransferResponse response = stockTransferService.executeTransfer(request);
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
        @PathVariable("id") UUID transferId,
        @RequestParam("approvedBy") String approvedBy
    ) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(transferId, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
        @PathVariable("id") UUID transferId,
        @RequestParam("approvedBy") String approvedBy
    ) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, approvedBy);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
        @PathVariable("id") UUID transferId
    ) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(transferId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * 이동 이력 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(Pageable pageable) {
        try {
            Page<StockTransferResponse> response = stockTransferService.getTransfers(pageable);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\transfer\dto\StockTransferRequest.java
============================================================
package com.wms.transfer.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String requestedBy;
}


============================================================
// FILE: src\main\java\com\wms\transfer\dto\StockTransferResponse.java
============================================================
package com.wms.transfer.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
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
    private String transferStatus;
    private String requestedBy;
    private String approvedBy;
    private Instant approvedAt;
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\transfer\entity\StockTransfer.java
============================================================
package com.wms.transfer.entity;

import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    @Id
    @Column(name = "transfer_id", nullable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 50)
    private TransferStatus transferStatus;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum TransferStatus {
        immediate,          // 즉시 이동 (소량)
        pending_approval,   // 승인 대기 (대량)
        approved,           // 승인 완료
        rejected            // 거부
    }
}


============================================================
// FILE: src\main\java\com\wms\transfer\repository\StockTransferRepository.java
============================================================
package com.wms.transfer.repository;

import com.wms.transfer.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @Query("SELECT st FROM StockTransfer st WHERE st.product.productId = :productId ORDER BY st.createdAt DESC")
    List<StockTransfer> findByProductId(@Param("productId") UUID productId);

    @Query("SELECT st FROM StockTransfer st WHERE st.fromLocation.locationId = :locationId OR st.toLocation.locationId = :locationId ORDER BY st.createdAt DESC")
    List<StockTransfer> findByLocationId(@Param("locationId") UUID locationId);

    @Query("SELECT st FROM StockTransfer st WHERE st.transferStatus = :status ORDER BY st.createdAt DESC")
    List<StockTransfer> findByStatus(@Param("status") StockTransfer.TransferStatus status);
}


============================================================
// FILE: src\main\java\com\wms\transfer\service\StockTransferService.java
============================================================
package com.wms.transfer.service;

import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.outbound.entity.AutoReorderLog;
import com.wms.outbound.entity.SafetyStockRule;
import com.wms.outbound.repository.AutoReorderLogRepository;
import com.wms.outbound.repository.SafetyStockRuleRepository;
import com.wms.transfer.dto.StockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.entity.StockTransfer;
import com.wms.transfer.repository.StockTransferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // 1. 기본 검증
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
            .orElseThrow(() -> new IllegalArgumentException("출발지 로케이션을 찾을 수 없습니다"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
            .orElseThrow(() -> new IllegalArgumentException("도착지 로케이션을 찾을 수 없습니다"));

        // 2. 출발지와 도착지가 동일한지 체크
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new IllegalStateException("출발지와 도착지가 동일합니다");
        }

        // 3. 실사 동결 체크
        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결된 로케이션에서는 이동할 수 없습니다: " + fromLocation.getCode());
        }
        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결된 로케이션으로는 이동할 수 없습니다: " + toLocation.getCode());
        }

        // 4. 출발지 재고 확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                product.getProductId(),
                fromLocation.getLocationId(),
                request.getLotNumber()
            ).orElseThrow(() -> new IllegalArgumentException("출발지에 해당 재고가 없습니다"));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new IllegalStateException(
                String.format("재고 부족: 요청 %d > 재고 %d", request.getQuantity(), sourceInventory.getQuantity()));
        }

        // 5. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 6. 위험물 혼적 금지 체크
        validateHazmatCompatibility(product, toLocation);

        // 7. 유통기한 이동 제한 체크
        if (Boolean.TRUE.equals(product.getHasExpiry()) && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestriction(sourceInventory, toLocation);
        }

        // 8. 도착지 용량 체크
        int availableCapacity = toLocation.getCapacity() - toLocation.getCurrentQty();
        if (availableCapacity < request.getQuantity()) {
            throw new IllegalStateException(
                String.format("도착지 용량 부족: 요청 %d > 가용 %d", request.getQuantity(), availableCapacity));
        }

        // 9. 대량 이동 여부 체크 (≥80%)
        double transferRatio = (double) request.getQuantity() / sourceInventory.getQuantity();
        boolean isLargeTransfer = transferRatio >= 0.8;

        StockTransfer.TransferStatus transferStatus;
        if (isLargeTransfer) {
            transferStatus = StockTransfer.TransferStatus.pending_approval;
        } else {
            transferStatus = StockTransfer.TransferStatus.immediate;
        }

        // 10. 이동 이력 기록
        StockTransfer transfer = StockTransfer.builder()
            .transferId(UUID.randomUUID())
            .product(product)
            .fromLocation(fromLocation)
            .toLocation(toLocation)
            .quantity(request.getQuantity())
            .lotNumber(request.getLotNumber())
            .transferStatus(transferStatus)
            .requestedBy(request.getRequestedBy())
            .build();

        stockTransferRepository.save(transfer);

        // 11. 즉시 이동일 경우 실제 재고 이동 실행
        if (transferStatus == StockTransfer.TransferStatus.immediate) {
            executeInventoryTransfer(sourceInventory, product, fromLocation, toLocation,
                request.getQuantity(), request.getLotNumber());
        }

        return mapToResponse(transfer);
    }

    /**
     * 대량 이동 승인
     */
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("이동 내역을 찾을 수 없습니다"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다");
        }

        // 1. 재고 이동 실행
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                transfer.getProduct().getProductId(),
                transfer.getFromLocation().getLocationId(),
                transfer.getLotNumber()
            ).orElseThrow(() -> new IllegalArgumentException("출발지 재고를 찾을 수 없습니다"));

        executeInventoryTransfer(
            sourceInventory,
            transfer.getProduct(),
            transfer.getFromLocation(),
            transfer.getToLocation(),
            transfer.getQuantity(),
            transfer.getLotNumber()
        );

        // 2. 이동 상태 갱신
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());
        stockTransferRepository.save(transfer);

        return mapToResponse(transfer);
    }

    /**
     * 대량 이동 거부
     */
    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("이동 내역을 찾을 수 없습니다"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());
        stockTransferRepository.save(transfer);

        return mapToResponse(transfer);
    }

    /**
     * 이동 상세 조회
     */
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("이동 내역을 찾을 수 없습니다"));

        return mapToResponse(transfer);
    }

    /**
     * 이동 이력 조회
     */
    public Page<StockTransferResponse> getTransfers(Pageable pageable) {
        return stockTransferRepository.findAll(pageable)
            .map(this::mapToResponse);
    }

    // ===== 내부 유틸리티 메서드 =====

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // HAZMAT 상품은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (toLocation.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException("위험물은 HAZMAT zone 로케이션으로만 이동할 수 있습니다");
            }
        }

        // FROZEN → AMBIENT 거부
        if (productType == Product.StorageType.FROZEN) {
            if (locationType == Product.StorageType.AMBIENT) {
                throw new IllegalStateException("FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
            }
        }

        // COLD → AMBIENT 거부
        if (productType == Product.StorageType.COLD) {
            if (locationType == Product.StorageType.AMBIENT) {
                throw new IllegalStateException("COLD 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
            }
        }
    }

    /**
     * 위험물 혼적 금지 검증
     */
    private void validateHazmatCompatibility(Product product, Location toLocation) {
        // 도착지에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getLocation().getLocationId().equals(toLocation.getLocationId()))
            .toList();

        if (existingInventories.isEmpty()) {
            return; // 도착지가 비어있으면 OK
        }

        boolean isProductHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;
        boolean hasHazmatInDestination = existingInventories.stream()
            .anyMatch(inv -> inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT);
        boolean hasNonHazmatInDestination = existingInventories.stream()
            .anyMatch(inv -> inv.getProduct().getCategory() != Product.ProductCategory.HAZMAT);

        // HAZMAT 상품을 비-HAZMAT 상품이 있는 로케이션으로 이동 시도
        if (isProductHazmat && hasNonHazmatInDestination) {
            throw new IllegalStateException("비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다");
        }

        // 비-HAZMAT 상품을 HAZMAT 상품이 있는 로케이션으로 이동 시도
        if (!isProductHazmat && hasHazmatInDestination) {
            throw new IllegalStateException("비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다");
        }
    }

    /**
     * 유통기한 이동 제한 검증
     */
    private void validateExpiryDateRestriction(Inventory inventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new IllegalStateException("유통기한이 만료된 재고는 이동할 수 없습니다");
        }

        // 잔여 유통기한 비율 계산
        LocalDate manufactureDate = inventory.getManufactureDate();
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

            if (totalDays > 0) {
                double remainingPct = (remainingDays * 100.0) / totalDays;

                // 잔여 유통기한 < 10% → SHIPPING zone만 허용
                if (remainingPct < 10.0) {
                    if (toLocation.getZone() != Location.Zone.SHIPPING) {
                        throw new IllegalStateException(
                            String.format("잔여 유통기한이 %.1f%%로 10%% 미만인 재고는 SHIPPING zone으로만 이동할 수 있습니다",
                                remainingPct));
                    }
                }
            }
        }
    }

    /**
     * 실제 재고 이동 실행
     */
    private void executeInventoryTransfer(Inventory sourceInventory, Product product,
                                           Location fromLocation, Location toLocation,
                                           int quantity, String lotNumber) {
        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        if (sourceInventory.getQuantity() == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }

        // 2. 출발지 로케이션 적재량 감소
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (동일 상품+lot 있으면 증가, 없으면 생성)
        Inventory targetInventory = inventoryRepository.findByProductAndLocationAndLot(
                product.getProductId(),
                toLocation.getLocationId(),
                lotNumber
            ).orElse(null);

        if (targetInventory != null) {
            targetInventory.setQuantity(targetInventory.getQuantity() + quantity);
            inventoryRepository.save(targetInventory);
        } else {
            // 새 재고 레코드 생성 (received_at, expiry_date, manufacture_date는 원본 유지)
            Inventory newInventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .product(product)
                .location(toLocation)
                .quantity(quantity)
                .lotNumber(lotNumber)
                .expiryDate(sourceInventory.getExpiryDate())
                .manufactureDate(sourceInventory.getManufactureDate())
                .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지
                .isExpired(false)
                .build();
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 적재량 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone 전체 재고 확인)
        checkSafetyStock(product);
    }

    /**
     * 안전재고 체크 및 자동 재발주 트리거
     */
    private void checkSafetyStock(Product product) {
        // STORAGE zone 내 해당 상품의 전체 재고 합산
        int totalStorageQty = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
            .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository
            .findByProduct_ProductIdAndIsActive(product.getProductId(), true)
            .orElse(null);

        if (rule != null && totalStorageQty < rule.getMinQty()) {
            // 안전재고 미달 → 자동 재발주 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                .reorderId(UUID.randomUUID())
                .product(product)
                .triggeredBy("SAFETY_STOCK_TRIGGER")
                .currentQty(totalStorageQty)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .build();

            autoReorderLogRepository.save(reorderLog);
        }
    }

    /**
     * Entity → Response 매핑
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
            .transferStatus(transfer.getTransferStatus().name())
            .requestedBy(transfer.getRequestedBy())
            .approvedBy(transfer.getApprovedBy())
            .approvedAt(transfer.getApprovedAt())
            .createdAt(transfer.getCreatedAt())
            .build();
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms-inbound

  datasource:
    url: jdbc:postgresql://localhost:5432/wms
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC

  sql:
    init:
      mode: never

server:
  port: 8080

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- ========================================
-- WMS Database Schema (Level 2)
-- ========================================

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

-- 2. 로케이션 (창고 내 적재 위치)
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

-- 3. 재고 (로케이션별 상품 재고)
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

-- 4. 공급업체 마스터
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

-- 6. 발주서 (Purchase Order)
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

-- 7. 입고 (Inbound Receipt)
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

-- 8. 출고 지시서 (Shipment Order)
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

-- 9. 백오더 (Backorder)
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

-- 10. 재고 이동 (Stock Transfer)
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

-- 11. 재고 조정 (Inventory Adjustment)
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

-- 12. 감사 로그 (Audit Log)
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. 안전재고 기준 (Safety Stock Rules)
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. 자동 재발주 이력 (Auto Reorder Logs)
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

-- 15. 계절 설정 (Seasonal Config)
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. 실사 세션 (Cycle Count)
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
CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_inventory_location ON inventory(location_id);
CREATE INDEX idx_po_lines_po ON purchase_order_lines(po_id);
CREATE INDEX idx_receipt_lines_receipt ON inbound_receipt_lines(receipt_id);
CREATE INDEX idx_supplier_penalties_supplier ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_created ON supplier_penalties(created_at);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date, is_active);

