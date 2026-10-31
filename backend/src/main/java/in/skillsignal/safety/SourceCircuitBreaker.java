package in.skillsignal.safety;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_circuit_breakers")
public class SourceCircuitBreaker {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "state", nullable = false, length = 32)
    private String state = "CLOSED"; // CLOSED, OPEN, HALF_OPEN

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "window_request_count", nullable = false)
    private int windowRequestCount = 0;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "opened_reason", columnDefinition = "TEXT")
    private String openedReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (windowStartedAt == null) windowStartedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public int getWindowRequestCount() { return windowRequestCount; }
    public void setWindowRequestCount(int windowRequestCount) { this.windowRequestCount = windowRequestCount; }

    public Instant getWindowStartedAt() { return windowStartedAt; }
    public void setWindowStartedAt(Instant windowStartedAt) { this.windowStartedAt = windowStartedAt; }

    public String getOpenedReason() { return openedReason; }
    public void setOpenedReason(String openedReason) { this.openedReason = openedReason; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

