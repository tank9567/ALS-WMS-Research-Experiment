# CLI Output

Error: Reached max turns (50)

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

