package in.skillsignal.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_applied_items")
public class SessionAppliedItem {

    @Id
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "company", nullable = false, length = 255)
    private String company;

    @Column(name = "job_title", nullable = false, length = 255)
    private String jobTitle;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "method", nullable = false, length = 128)
    private String method;

    @Column(name = "confirmation_type", nullable = false, length = 64)
    private String confirmationType;

    @Column(name = "while_away", nullable = false)
    private boolean whileAway = false;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getConfirmationType() { return confirmationType; }
    public void setConfirmationType(String confirmationType) { this.confirmationType = confirmationType; }

    public boolean isWhileAway() { return whileAway; }
    public void setWhileAway(boolean whileAway) { this.whileAway = whileAway; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

