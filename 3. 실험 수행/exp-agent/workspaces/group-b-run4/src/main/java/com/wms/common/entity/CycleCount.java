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
