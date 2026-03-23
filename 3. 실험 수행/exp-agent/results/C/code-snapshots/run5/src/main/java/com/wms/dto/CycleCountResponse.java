package com.wms.dto;

import java.time.Instant;
import java.util.UUID;

public class CycleCountResponse {
    private UUID cycleCountId;
    private UUID locationId;
    private String locationCode;
    private String status;
    private String startedBy;
    private Instant startedAt;
    private Instant completedAt;

    // Getters and Setters
    public UUID getCycleCountId() { return cycleCountId; }
    public void setCycleCountId(UUID cycleCountId) { this.cycleCountId = cycleCountId; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
