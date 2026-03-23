# CLI Output

재고 이동 기능 구현이 완료되었습니다.

**구현 내용:**

1. **Entity & Enum** (`src/main/java/com/wms/transfer/entity/`)
   - `TransferStatus.java`: IMMEDIATE, PENDING_APPROVAL, APPROVED, REJECTED
   - `StockTransfer.java`: 재고 이동 이력 테이블

2. **Repository** (`src/main/java/com/wms/transfer/repository/`)
   - `StockTransferRepository.java`

3. **DTO** (`src/main/java/com/wms/transfer/dto/`)
   - `CreateStockTransferRequest.java`: 이동 요청
   - `StockTransferResponse.java`: 이동 응답
   - `ApprovalRequest.java`: 승인/거부 요청

4. **Service** (`src/main/java/com/wms/transfer/service/StockTransferService.java`)
   - 단일 트랜잭션 처리 및 롤백
   - 동일 로케이션 거부
   - 재고 부족 체크
   - 용량 체크
   - 보관유형 호환성 검증 (FROZEN→AMBIENT 거부 등)
   - HAZMAT 혼적 금지
   - 유통기한 <10% → SHIPPING zone만 허용
   - 유통기한 만료 → 이동 불가
   - 대량 이동(≥80%) → 관리자 승인 (pending_approval)
   - 이동 후 STORAGE zone 안전재고 체크
   - 실사 동결 로케이션 이동 불가

5. **Controller** (`src/main/java/com/wms/transfer/controller/StockTransferController.java`)
   - POST `/api/v1/stock-transfers` - 재고 이동 실행
   - POST `/api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
   - POST `/api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
   - GET `/api/v1/stock-transfers/{id}` - 이동 상세 조회
   - GET `/api/v1/stock-transfers` - 이동 이력 조회

모든 비즈니스 규칙이 요구사항 및 설계 문서에 따라 구현되었습니다.


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
    <name>WMS Inbound Service</name>
    <description>Warehouse Management System - Inbound Processing</description>

    <properties>
        <java.version>17</java.version>
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
// FILE: schema.sql
============================================================
-- WMS Database Schema (PostgreSQL 15+)
-- UUID v4, TIMESTAMPTZ (UTC)

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


============================================================
// FILE: src\main\java\com\wms\common\entity\AuditLog.java
============================================================
package com.wms.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.ZonedDateTime;
import java.util.Map;
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

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\common\entity\AutoReorderLog.java
============================================================
package com.wms.common.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\common\entity\CycleCount.java
============================================================
package com.wms.common.entity;

import com.wms.inbound.entity.Location;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "cycle_count_id")
    private UUID cycleCountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CycleCountStatus status;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = CycleCountStatus.IN_PROGRESS;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\common\entity\CycleCountStatus.java
============================================================
package com.wms.common.entity;

public enum CycleCountStatus {
    IN_PROGRESS,
    COMPLETED
}


============================================================
// FILE: src\main\java\com\wms\common\entity\SafetyStockRule.java
============================================================
package com.wms.common.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "rule_id")
    private UUID ruleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\common\entity\TriggerType.java
============================================================
package com.wms.common.entity;

public enum TriggerType {
    SAFETY_STOCK_TRIGGER,
    URGENT_REORDER
}


============================================================
// FILE: src\main\java\com\wms\common\repository\AuditLogRepository.java
============================================================
package com.wms.common.repository;

import com.wms.common.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\common\repository\AutoReorderLogRepository.java
============================================================
package com.wms.common.repository;

import com.wms.common.entity.AutoReorderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AutoReorderLogRepository extends JpaRepository<AutoReorderLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\common\repository\CycleCountRepository.java
============================================================
package com.wms.common.repository;

import com.wms.common.entity.CycleCount;
import com.wms.common.entity.CycleCountStatus;
import com.wms.inbound.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    Optional<CycleCount> findByLocationAndStatus(Location location, CycleCountStatus status);
}


============================================================
// FILE: src\main\java\com\wms\common\repository\SafetyStockRuleRepository.java
============================================================
package com.wms.common.repository;

import com.wms.common.entity.SafetyStockRule;
import com.wms.inbound.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, UUID> {
    Optional<SafetyStockRule> findByProduct(Product product);
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\InboundReceipt.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InboundReceiptStatus status;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private ZonedDateTime receivedAt;

    @Column(name = "confirmed_at")
    private ZonedDateTime confirmedAt;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (receivedAt == null) {
            receivedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = InboundReceiptStatus.INSPECTING;
        }
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
// FILE: src\main\java\com\wms\inbound\entity\InboundReceiptStatus.java
============================================================
package com.wms.inbound.entity;

public enum InboundReceiptStatus {
    INSPECTING,
    PENDING_APPROVAL,
    CONFIRMED,
    REJECTED
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Inventory.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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
    private ZonedDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
        }
        if (quantity == null) {
            quantity = 0;
        }
        if (isExpired == null) {
            isExpired = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Location.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 50)
    private LocationZone zone;

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

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
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
        if (storageType == null) {
            storageType = StorageType.AMBIENT;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\LocationZone.java
============================================================
package com.wms.inbound.entity;

public enum LocationZone {
    RECEIVING,
    STORAGE,
    SHIPPING,
    HAZMAT
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PenaltyType.java
============================================================
package com.wms.inbound.entity;

public enum PenaltyType {
    OVER_DELIVERY,
    SHORT_SHELF_LIFE
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Product.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
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

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\ProductCategory.java
============================================================
package com.wms.inbound.entity;

public enum ProductCategory {
    GENERAL,
    FRESH,
    HAZMAT,
    HIGH_VALUE
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrder.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PurchaseOrderType poType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseOrderStatus status;

    @Column(name = "ordered_at", nullable = false)
    private ZonedDateTime orderedAt;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
        }
        if (poType == null) {
            poType = PurchaseOrderType.NORMAL;
        }
        if (status == null) {
            status = PurchaseOrderStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
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
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrderStatus.java
============================================================
package com.wms.inbound.entity;

public enum PurchaseOrderStatus {
    PENDING,
    PARTIAL,
    COMPLETED,
    CANCELLED,
    HOLD
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrderType.java
============================================================
package com.wms.inbound.entity;

public enum PurchaseOrderType {
    NORMAL,
    URGENT,
    IMPORT
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SeasonalConfig.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
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
// FILE: src\main\java\com\wms\inbound\entity\StorageType.java
============================================================
package com.wms.inbound.entity;

public enum StorageType {
    AMBIENT,
    COLD,
    FROZEN
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Supplier.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = SupplierStatus.ACTIVE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SupplierPenalty.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SupplierStatus.java
============================================================
package com.wms.inbound.entity;

public enum SupplierStatus {
    ACTIVE,
    HOLD,
    INACTIVE
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
           "AND i.location.locationId = :locationId " +
           "AND (i.lotNumber = :lotNumber OR (i.lotNumber IS NULL AND :lotNumber IS NULL))")
    Optional<Inventory> findByProductAndLocationAndLotNumber(
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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByCode(String code);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\ProductRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);
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

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId")
    List<PurchaseOrderLine> findByPurchaseOrderId(@Param("poId") UUID poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId " +
           "AND pol.product.productId = :productId")
    Optional<PurchaseOrderLine> findByPurchaseOrderIdAndProductId(
        @Param("poId") UUID poId,
        @Param("productId") UUID productId
    );
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\PurchaseOrderRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrder;
import com.wms.inbound.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Modifying
    @Query("UPDATE PurchaseOrder po SET po.status = :newStatus WHERE po.supplier.supplierId = :supplierId " +
           "AND po.status = :currentStatus")
    int updateStatusBySupplierIdAndCurrentStatus(
        @Param("supplierId") UUID supplierId,
        @Param("currentStatus") PurchaseOrderStatus currentStatus,
        @Param("newStatus") PurchaseOrderStatus newStatus
    );
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
    Optional<SeasonalConfig> findActiveSeasonByDate(@Param("date") LocalDate date);
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

import java.time.ZonedDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.supplierId = :supplierId " +
           "AND sp.createdAt >= :since")
    long countBySupplie rIdAndCreatedAtAfter(
        @Param("supplierId") UUID supplierId,
        @Param("since") ZonedDateTime since
    );
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
// FILE: src\main\java\com\wms\inbound\WmsInboundApplication.java
============================================================
package com.wms.inbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WmsInboundApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsInboundApplication.class, args);
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\BackorderDto.java
============================================================
package com.wms.outbound.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackorderDto {
    private UUID backorderId;
    private Integer shortageQty;
    private String status;
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\CreateShipmentOrderRequest.java
============================================================
package com.wms.outbound.dto;

import lombok.*;
import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShipmentOrderRequest {
    private String shipmentNumber;
    private String customerName;
    private ZonedDateTime requestedAt;
    private List<ShipmentLineRequest> lines;
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\PickDetail.java
============================================================
package com.wms.outbound.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickDetail {
    private UUID locationId;
    private String locationCode;
    private UUID inventoryId;
    private String lotNumber;
    private Integer pickedQty;
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\PickResponse.java
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
public class PickResponse {
    private UUID shipmentId;
    private String shipmentNumber;
    private String status;
    private List<PickResultDto> pickResults;
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\PickResultDto.java
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
public class PickResultDto {
    private UUID shipmentLineId;
    private UUID productId;
    private String productSku;
    private Integer requestedQty;
    private Integer pickedQty;
    private String status;
    private List<PickDetail> pickDetails;
    private BackorderDto backorder;
}


============================================================
// FILE: src\main\java\com\wms\outbound\dto\ShipmentLineRequest.java
============================================================
package com.wms.outbound.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentLineRequest {
    private UUID productId;
    private Integer requestedQty;
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\Backorder.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "backorder_id")
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_line_id", nullable = false)
    private ShipmentOrderLine shipmentLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackorderStatus status;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "fulfilled_at")
    private ZonedDateTime fulfilledAt;

    @PrePersist
    public void prePersist() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = BackorderStatus.OPEN;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\BackorderStatus.java
============================================================
package com.wms.outbound.entity;

public enum BackorderStatus {
    OPEN,
    FULFILLED,
    CANCELLED
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\ShipmentLineStatus.java
============================================================
package com.wms.outbound.entity;

public enum ShipmentLineStatus {
    PENDING,
    PICKED,
    PARTIAL,
    BACKORDERED
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\ShipmentOrder.java
============================================================
package com.wms.outbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 30)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentOrderStatus status;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "shipped_at")
    private ZonedDateTime shippedAt;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = ShipmentOrderStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\ShipmentOrderLine.java
============================================================
package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
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
    private ShipmentLineStatus status;

    @PrePersist
    public void prePersist() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
        if (pickedQty == null) {
            pickedQty = 0;
        }
        if (status == null) {
            status = ShipmentLineStatus.PENDING;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\outbound\entity\ShipmentOrderStatus.java
============================================================
package com.wms.outbound.entity;

public enum ShipmentOrderStatus {
    PENDING,
    PICKING,
    PARTIAL,
    SHIPPED,
    CANCELLED
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
    Optional<ShipmentOrder> findByShipmentNumber(String shipmentNumber);
}


============================================================
// FILE: src\main\java\com\wms\outbound\service\ShipmentOrderService.java
============================================================
package com.wms.outbound.service;

import com.wms.common.entity.*;
import com.wms.common.repository.*;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.*;
import com.wms.outbound.dto.*;
import com.wms.outbound.entity.*;
import com.wms.outbound.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShipmentOrderService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final BackorderRepository backorderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrder createShipmentOrder(CreateShipmentOrderRequest request) {
        // 출고 지시서 생성
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .requestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : ZonedDateTime.now())
                .status(ShipmentOrderStatus.PENDING)
                .build();

        // HAZMAT과 FRESH 분리 체크
        List<ShipmentLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentLineRequest> nonHazmatLines = new ArrayList<>();
        boolean hasFresh = false;

        for (ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + lineReq.getProductId()));

            if (product.getCategory() == ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
                if (product.getCategory() == ProductCategory.FRESH) {
                    hasFresh = true;
                }
            }
        }

        // HAZMAT과 FRESH가 함께 있으면 HAZMAT을 별도 출고로 분리
        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 별도 출고 생성
            ShipmentOrder hazmatShipment = ShipmentOrder.builder()
                    .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .requestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : ZonedDateTime.now())
                    .status(ShipmentOrderStatus.PENDING)
                    .build();

            for (ShipmentLineRequest hazmatLine : hazmatLines) {
                Product product = productRepository.findById(hazmatLine.getProductId()).orElseThrow();
                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(hazmatShipment)
                        .product(product)
                        .requestedQty(hazmatLine.getRequestedQty())
                        .pickedQty(0)
                        .status(ShipmentLineStatus.PENDING)
                        .build();
                hazmatShipment.getLines().add(line);
            }
            shipmentOrderRepository.save(hazmatShipment);

            // 원래 출고에는 비-HAZMAT만 포함
            for (ShipmentLineRequest nonHazmatLine : nonHazmatLines) {
                Product product = productRepository.findById(nonHazmatLine.getProductId()).orElseThrow();
                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(shipmentOrder)
                        .product(product)
                        .requestedQty(nonHazmatLine.getRequestedQty())
                        .pickedQty(0)
                        .status(ShipmentLineStatus.PENDING)
                        .build();
                shipmentOrder.getLines().add(line);
            }
        } else {
            // 분리 불필요, 모든 라인 추가
            for (ShipmentLineRequest lineReq : request.getLines()) {
                Product product = productRepository.findById(lineReq.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + lineReq.getProductId()));

                ShipmentOrderLine line = ShipmentOrderLine.builder()
                        .shipmentOrder(shipmentOrder)
                        .product(product)
                        .requestedQty(lineReq.getRequestedQty())
                        .pickedQty(0)
                        .status(ShipmentLineStatus.PENDING)
                        .build();
                shipmentOrder.getLines().add(line);
            }
        }

        return shipmentOrderRepository.save(shipmentOrder);
    }

    @Transactional
    public PickResponse executePicking(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment order not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentOrderStatus.PENDING) {
            throw new RuntimeException("Shipment order is not in PENDING status");
        }

        shipment.setStatus(ShipmentOrderStatus.PICKING);
        List<PickResultDto> pickResults = new ArrayList<>();

        for (ShipmentOrderLine line : shipment.getLines()) {
            PickResultDto pickResult = pickLineItem(line);
            pickResults.add(pickResult);
        }

        // 모든 라인 상태 체크하여 전체 출고 상태 결정
        boolean allPicked = shipment.getLines().stream()
                .allMatch(l -> l.getStatus() == ShipmentLineStatus.PICKED);
        boolean anyPicked = shipment.getLines().stream()
                .anyMatch(l -> l.getStatus() == ShipmentLineStatus.PICKED || l.getStatus() == ShipmentLineStatus.PARTIAL);

        if (allPicked) {
            shipment.setStatus(ShipmentOrderStatus.SHIPPED);
        } else if (anyPicked) {
            shipment.setStatus(ShipmentOrderStatus.PARTIAL);
        } else {
            // 전량 백오더
            shipment.setStatus(ShipmentOrderStatus.PARTIAL);
        }

        shipmentOrderRepository.save(shipment);

        return PickResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .shipmentNumber(shipment.getShipmentNumber())
                .status(shipment.getStatus().name())
                .pickResults(pickResults)
                .build();
    }

    private PickResultDto pickLineItem(ShipmentOrderLine line) {
        Product product = line.getProduct();
        Integer requestedQty = line.getRequestedQty();

        // 피킹 가능한 재고 조회 (FIFO/FEFO 우선순위)
        List<Inventory> availableInventory = getAvailableInventory(product);

        // HAZMAT 제약 체크
        if (product.getCategory() == ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                requestedQty = product.getMaxPickQty();
            }
        }

        List<PickDetail> pickDetails = new ArrayList<>();
        int totalPicked = 0;

        // 재고에서 피킹
        for (Inventory inv : availableInventory) {
            if (totalPicked >= requestedQty) {
                break;
            }

            // 실사 동결 로케이션 체크
            if (inv.getLocation().getIsFrozen()) {
                continue;
            }

            int neededQty = requestedQty - totalPicked;
            int pickQty = Math.min(neededQty, inv.getQuantity());

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inv.getLocation().setCurrentQty(inv.getLocation().getCurrentQty() - pickQty);

            inventoryRepository.save(inv);
            locationRepository.save(inv.getLocation());

            // 피킹 상세 기록
            PickDetail detail = PickDetail.builder()
                    .locationId(inv.getLocation().getLocationId())
                    .locationCode(inv.getLocation().getCode())
                    .inventoryId(inv.getInventoryId())
                    .lotNumber(inv.getLotNumber())
                    .pickedQty(pickQty)
                    .build();
            pickDetails.add(detail);

            totalPicked += pickQty;

            // 보관유형 불일치 경고
            if (inv.getLocation().getStorageType() != product.getStorageType()) {
                logStorageTypeMismatch(inv, product);
            }
        }

        // 피킹 결과 처리
        line.setPickedQty(totalPicked);

        BackorderDto backorderDto = null;
        int availableTotal = availableInventory.stream()
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .mapToInt(Inventory::getQuantity)
                .sum();

        double fulfillmentRate = (double) availableTotal / line.getRequestedQty();

        if (totalPicked >= line.getRequestedQty()) {
            // 전량 피킹
            line.setStatus(ShipmentLineStatus.PICKED);
        } else if (fulfillmentRate >= 0.7) {
            // 70% 이상: 부분출고 + 백오더
            line.setStatus(ShipmentLineStatus.PARTIAL);
            backorderDto = createBackorder(line, line.getRequestedQty() - totalPicked);
        } else if (fulfillmentRate >= 0.3) {
            // 30~70%: 부분출고 + 백오더 + 긴급발주
            line.setStatus(ShipmentLineStatus.PARTIAL);
            backorderDto = createBackorder(line, line.getRequestedQty() - totalPicked);
            createUrgentReorder(product, availableTotal);
        } else {
            // 30% 미만: 전량 백오더
            line.setStatus(ShipmentLineStatus.BACKORDERED);
            backorderDto = createBackorder(line, line.getRequestedQty());
        }

        shipmentOrderLineRepository.save(line);

        // 출고 후 안전재고 체크
        checkSafetyStockAfterShipment(product);

        return PickResultDto.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(product.getProductId())
                .productSku(product.getSku())
                .requestedQty(line.getRequestedQty())
                .pickedQty(totalPicked)
                .status(line.getStatus().name())
                .pickDetails(pickDetails)
                .backorder(backorderDto)
                .build();
    }

    private List<Inventory> getAvailableInventory(Product product) {
        // 전체 재고 조회
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getIsExpired())
                .collect(Collectors.toList());

        // 유통기한 체크 및 만료 처리
        LocalDate today = LocalDate.now();
        for (Inventory inv : allInventory) {
            if (product.getHasExpiry() && inv.getExpiryDate() != null) {
                // 유통기한 지남
                if (inv.getExpiryDate().isBefore(today)) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    continue;
                }

                // 잔여율 계산
                if (inv.getManufactureDate() != null) {
                    long totalDays = inv.getExpiryDate().toEpochDay() - inv.getManufactureDate().toEpochDay();
                    long remainingDays = inv.getExpiryDate().toEpochDay() - today.toEpochDay();
                    double remainingPct = (double) remainingDays / totalDays * 100;

                    // 잔여율 < 10%: 출고 불가
                    if (remainingPct < 10) {
                        inv.setIsExpired(true);
                        inventoryRepository.save(inv);
                    }
                }
            }
        }

        // 만료되지 않은 재고만 필터링
        List<Inventory> available = allInventory.stream()
                .filter(inv -> !inv.getIsExpired())
                .collect(Collectors.toList());

        // HAZMAT zone 제약
        if (product.getCategory() == ProductCategory.HAZMAT) {
            available = available.stream()
                    .filter(inv -> inv.getLocation().getZone() == LocationZone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // FIFO/FEFO 정렬
        if (product.getHasExpiry()) {
            // FEFO: 유통기한 오름차순, 잔여율 30% 미만 최우선
            available.sort((a, b) -> {
                LocalDate today2 = LocalDate.now();
                Double aPct = calculateRemainingShelfLifePct(a, today2);
                Double bPct = calculateRemainingShelfLifePct(b, today2);

                boolean aUrgent = aPct != null && aPct < 30;
                boolean bUrgent = bPct != null && bPct < 30;

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;

                // 유통기한 오름차순
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;

                // 유통기한 같으면 입고일 오름차순 (FIFO)
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO: 입고일 오름차순
            available.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return available;
    }

    private Double calculateRemainingShelfLifePct(Inventory inv, LocalDate today) {
        if (inv.getExpiryDate() == null || inv.getManufactureDate() == null) {
            return null;
        }
        long totalDays = inv.getExpiryDate().toEpochDay() - inv.getManufactureDate().toEpochDay();
        long remainingDays = inv.getExpiryDate().toEpochDay() - today.toEpochDay();
        return (double) remainingDays / totalDays * 100;
    }

    private BackorderDto createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);

        return BackorderDto.builder()
                .backorderId(backorder.getBackorderId())
                .shortageQty(shortageQty)
                .status(BackorderStatus.OPEN.name())
                .build();
    }

    private void createUrgentReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(TriggerType.URGENT_REORDER)
                .currentStock(currentStock)
                .minQty(rule != null ? rule.getMinQty() : 0)
                .reorderQty(rule != null ? rule.getReorderQty() : 100)
                .triggeredBy("SYSTEM")
                .build();
        autoReorderLogRepository.save(log);
    }

    private void checkSafetyStockAfterShipment(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return;
        }

        // 전체 가용 재고 계산 (is_expired=false만)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailable)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    private void logStorageTypeMismatch(Inventory inv, Product product) {
        Map<String, Object> details = new HashMap<>();
        details.put("productId", product.getProductId().toString());
        details.put("productSku", product.getSku());
        details.put("productStorageType", product.getStorageType().name());
        details.put("locationId", inv.getLocation().getLocationId().toString());
        details.put("locationCode", inv.getLocation().getCode());
        details.put("locationStorageType", inv.getLocation().getStorageType().name());

        AuditLog log = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(inv.getInventoryId())
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(log);
    }

    public ShipmentOrder getShipmentOrder(UUID shipmentId) {
        return shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment order not found: " + shipmentId));
    }

    public List<ShipmentOrder> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll();
    }

    @Transactional
    public ShipmentOrder confirmShipment(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment order not found: " + shipmentId));

        if (shipment.getStatus() == ShipmentOrderStatus.PICKING ||
            shipment.getStatus() == ShipmentOrderStatus.PARTIAL) {
            shipment.setStatus(ShipmentOrderStatus.SHIPPED);
            shipment.setShippedAt(ZonedDateTime.now());
        }

        return shipmentOrderRepository.save(shipment);
    }
}


============================================================
// FILE: src\main\java\com\wms\transfer\controller\StockTransferController.java
============================================================
package com.wms.transfer.controller;

import com.wms.transfer.dto.ApprovalRequest;
import com.wms.transfer.dto.CreateStockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTransfer(@RequestBody CreateStockTransferRequest request) {
        try {
            StockTransferResponse response = stockTransferService.createTransfer(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "TRANSFER_VALIDATION_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveTransfer(
        @PathVariable("id") UUID transferId,
        @RequestBody ApprovalRequest request
    ) {
        try {
            StockTransferResponse response = stockTransferService.approveTransfer(transferId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "APPROVAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectTransfer(
        @PathVariable("id") UUID transferId,
        @RequestBody ApprovalRequest request
    ) {
        try {
            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "REJECTION_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTransfer(@PathVariable("id") UUID transferId) {
        try {
            StockTransferResponse response = stockTransferService.getTransfer(transferId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "NOT_FOUND");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTransfers() {
        try {
            List<StockTransferResponse> responses = stockTransferService.getAllTransfers();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", responses);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, String> errorDetail = new HashMap<>();
            errorDetail.put("code", "INTERNAL_ERROR");
            errorDetail.put("message", e.getMessage());
            error.put("error", errorDetail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\transfer\dto\ApprovalRequest.java
============================================================
package com.wms.transfer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {
    private String approvedBy;
}


============================================================
// FILE: src\main\java\com\wms\transfer\dto\CreateStockTransferRequest.java
============================================================
package com.wms.transfer.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStockTransferRequest {
    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferredBy;
}


============================================================
// FILE: src\main\java\com\wms\transfer\dto\StockTransferResponse.java
============================================================
package com.wms.transfer.dto;

import com.wms.transfer.entity.TransferStatus;
import lombok.*;

import java.time.ZonedDateTime;
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
    private String reason;
    private TransferStatus transferStatus;
    private String transferredBy;
    private String approvedBy;
    private ZonedDateTime transferredAt;
}


============================================================
// FILE: src\main\java\com\wms\transfer\entity\StockTransfer.java
============================================================
package com.wms.transfer.entity;

import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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

    @Column(name = "transferred_at")
    private ZonedDateTime transferredAt;

    @PrePersist
    public void prePersist() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (transferredAt == null) {
            transferredAt = ZonedDateTime.now();
        }
        if (transferStatus == null) {
            transferStatus = TransferStatus.IMMEDIATE;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\transfer\entity\TransferStatus.java
============================================================
package com.wms.transfer.entity;

public enum TransferStatus {
    IMMEDIATE,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}


============================================================
// FILE: src\main\java\com\wms\transfer\repository\StockTransferRepository.java
============================================================
package com.wms.transfer.repository;

import com.wms.transfer.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\transfer\service\StockTransferService.java
============================================================
package com.wms.transfer.service;

import com.wms.common.entity.AutoReorderLog;
import com.wms.common.entity.SafetyStockRule;
import com.wms.common.entity.TriggerType;
import com.wms.common.repository.AutoReorderLogRepository;
import com.wms.common.repository.SafetyStockRuleRepository;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.transfer.dto.ApprovalRequest;
import com.wms.transfer.dto.CreateStockTransferRequest;
import com.wms.transfer.dto.StockTransferResponse;
import com.wms.transfer.entity.StockTransfer;
import com.wms.transfer.entity.TransferStatus;
import com.wms.transfer.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse createTransfer(CreateStockTransferRequest request) {
        // 1. 기본 검증
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
            .orElseThrow(() -> new IllegalArgumentException("From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
            .orElseThrow(() -> new IllegalArgumentException("To location not found"));

        // 2. 동일 로케이션 체크
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new IllegalArgumentException("Cannot transfer to the same location");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new IllegalArgumentException("From location is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new IllegalArgumentException("To location is frozen for cycle count");
        }

        // 4. 출발지 재고 확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
            product.getProductId(),
            fromLocation.getLocationId(),
            request.getLotNumber()
        ).orElseThrow(() -> new IllegalArgumentException("Source inventory not found"));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Insufficient quantity at source location");
        }

        // 5. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 6. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 7. 유통기한 임박 상품 이동 제한
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryConstraints(sourceInventory, toLocation);
        }

        // 8. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new IllegalArgumentException("Destination location capacity exceeded");
        }

        // 9. 대량 이동 승인 체크 (80% 이상)
        TransferStatus transferStatus = TransferStatus.IMMEDIATE;
        double transferRatio = (double) request.getQuantity() / sourceInventory.getQuantity();
        if (transferRatio >= 0.8) {
            transferStatus = TransferStatus.PENDING_APPROVAL;
        }

        // 10. 이동 이력 기록
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

        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 반영
        if (transferStatus == TransferStatus.IMMEDIATE) {
            executeTransfer(transfer, sourceInventory);
        }

        return buildResponse(transfer);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, ApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        // 재고 확인 및 이동 실행
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
            transfer.getProduct().getProductId(),
            transfer.getFromLocation().getLocationId(),
            transfer.getLotNumber()
        ).orElseThrow(() -> new IllegalArgumentException("Source inventory not found"));

        if (sourceInventory.getQuantity() < transfer.getQuantity()) {
            throw new IllegalArgumentException("Insufficient quantity at source location");
        }

        // 이동 실행
        executeTransfer(transfer, sourceInventory);

        // 승인 상태 업데이트
        transfer.setTransferStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        return buildResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, ApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(TransferStatus.REJECTED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        return buildResponse(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return buildResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
            .map(this::buildResponse)
            .collect(Collectors.toList());
    }

    private void executeTransfer(StockTransfer transfer, Inventory sourceInventory) {
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        Product product = transfer.getProduct();

        // 출발지 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - transfer.getQuantity());
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());

        if (sourceInventory.getQuantity() == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }
        locationRepository.save(fromLocation);

        // 도착지 증가
        Optional<Inventory> destInventoryOpt = inventoryRepository.findByProductAndLocationAndLotNumber(
            product.getProductId(),
            toLocation.getLocationId(),
            transfer.getLotNumber()
        );

        if (destInventoryOpt.isPresent()) {
            // 기존 재고에 수량 추가
            Inventory destInventory = destInventoryOpt.get();
            destInventory.setQuantity(destInventory.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(destInventory);
        } else {
            // 새 재고 레코드 생성
            Inventory newInventory = Inventory.builder()
                .product(product)
                .location(toLocation)
                .quantity(transfer.getQuantity())
                .lotNumber(transfer.getLotNumber())
                .expiryDate(sourceInventory.getExpiryDate())
                .manufactureDate(sourceInventory.getManufactureDate())
                .receivedAt(sourceInventory.getReceivedAt()) // FIFO 유지
                .isExpired(sourceInventory.getIsExpired())
                .build();
            inventoryRepository.save(newInventory);
        }

        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 안전재고 체크 (STORAGE zone만)
        checkSafetyStockForStorageZone(product, transfer.getTransferredBy());
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        StorageType productType = product.getStorageType();
        StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 거부
        if (productType == StorageType.FROZEN && locationType == StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move FROZEN product to AMBIENT location");
        }

        // COLD 상품 → AMBIENT 거부
        if (productType == StorageType.COLD && locationType == StorageType.AMBIENT) {
            throw new IllegalArgumentException("Cannot move COLD product to AMBIENT location");
        }

        // HAZMAT 상품 → 비-HAZMAT zone 거부
        if (product.getCategory() == ProductCategory.HAZMAT && toLocation.getZone() != LocationZone.HAZMAT) {
            throw new IllegalArgumentException("HAZMAT product can only be moved to HAZMAT zone");
        }
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 적재된 상품 확인
        List<Inventory> existingInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getLocation().getLocationId().equals(toLocation.getLocationId()))
            .filter(inv -> inv.getQuantity() > 0)
            .collect(Collectors.toList());

        if (existingInventories.isEmpty()) {
            return;
        }

        boolean isHazmat = product.getCategory() == ProductCategory.HAZMAT;

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new IllegalArgumentException("Cannot mix HAZMAT and non-HAZMAT products in same location");
            }
            if (!isHazmat && existingIsHazmat) {
                throw new IllegalArgumentException("Cannot mix non-HAZMAT and HAZMAT products in same location");
            }
        }
    }

    private void validateExpiryConstraints(Inventory sourceInventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new IllegalArgumentException("Cannot transfer expired inventory");
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalShelfLife = java.time.temporal.ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingShelfLife = java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

            // 잔여 유통기한 < 10% → SHIPPING zone만 허용
            if (remainingPct < 10 && toLocation.getZone() != LocationZone.SHIPPING) {
                throw new IllegalArgumentException("Products with less than 10% shelf life can only be moved to SHIPPING zone");
            }
        }
    }

    private void checkSafetyStockForStorageZone(Product product, String triggeredBy) {
        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
            .filter(inv -> inv.getLocation().getZone() == LocationZone.STORAGE)
            .filter(inv -> !inv.getIsExpired())
            .collect(Collectors.toList());

        int totalStorageQty = storageInventories.stream()
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 기준 확인
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct(product);
        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();
            if (totalStorageQty <= rule.getMinQty()) {
                // 자동 재발주 로그 기록
                AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStorageQty)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy(triggeredBy)
                    .build();
                autoReorderLogRepository.save(log);
            }
        }
    }

    private StockTransferResponse buildResponse(StockTransfer transfer) {
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
            .transferStatus(transfer.getTransferStatus())
            .transferredBy(transfer.getTransferredBy())
            .approvedBy(transfer.getApprovedBy())
            .transferredAt(transfer.getTransferredAt())
            .build();
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms-inbound-service

  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: wms_user
    password: wms_password
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        default_schema: public

  jackson:
    time-zone: UTC
    serialization:
      write-dates-as-timestamps: false

server:
  port: 8080
  servlet:
    context-path: /api/v1

logging:
  level:
    com.wms.inbound: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

