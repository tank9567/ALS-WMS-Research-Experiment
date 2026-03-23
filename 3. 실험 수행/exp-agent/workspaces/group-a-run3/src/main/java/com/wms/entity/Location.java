package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "storage_type")
    private Product.StorageType storageType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, name = "current_quantity")
    private Integer currentQuantity = 0;

    @Column(nullable = false, name = "is_frozen")
    private Boolean isFrozen = false;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }
}
