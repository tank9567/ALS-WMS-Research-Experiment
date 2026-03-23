package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleCount {

    @Id
    @Column(name = "cycle_count_id")
    private UUID cycleCountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CycleCountStatus status = CycleCountStatus.IN_PROGRESS;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;

    @CreationTimestamp
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
        }
    }

    public enum CycleCountStatus {
        IN_PROGRESS, COMPLETED
    }
}
