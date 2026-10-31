package in.skillsignal.source;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_capabilities")
public class SourceCapability {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String source;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "access_method", nullable = false, length = 150)
    private String accessMethod;

    @Column(name = "allowed_actions", nullable = false, length = 255)
    private String allowedActions = "ASSISTED_APPLY";

    @Column(name = "rate_limit", nullable = false, length = 100)
    private String rateLimit;

    @Column(name = "terms_reviewed_at")
    private Instant termsReviewedAt;

    @Column(name = "confirmation_signals", length = 255)
    private String confirmationSignals;

    @Column(name = "status_note", length = 255)
    private String statusNote;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (allowedActions == null) allowedActions = "ASSISTED_APPLY";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAccessMethod() { return accessMethod; }
    public void setAccessMethod(String accessMethod) { this.accessMethod = accessMethod; }
    public String getAllowedActions() { return allowedActions; }
    public void setAllowedActions(String allowedActions) { this.allowedActions = allowedActions; }
    public String getRateLimit() { return rateLimit; }
    public void setRateLimit(String rateLimit) { this.rateLimit = rateLimit; }
    public Instant getTermsReviewedAt() { return termsReviewedAt; }
    public void setTermsReviewedAt(Instant termsReviewedAt) { this.termsReviewedAt = termsReviewedAt; }
    public String getConfirmationSignals() { return confirmationSignals; }
    public void setConfirmationSignals(String confirmationSignals) { this.confirmationSignals = confirmationSignals; }
    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

