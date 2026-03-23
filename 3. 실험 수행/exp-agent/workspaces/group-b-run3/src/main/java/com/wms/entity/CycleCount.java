package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
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
    private CycleCountStatus status = CycleCountStatus.IN_PROGRESS;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    public enum CycleCountStatus {
        IN_PROGRESS, COMPLETED
    }
}
