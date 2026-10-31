package in.skillsignal.learning;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_change_suggestions")
public class RuleChangeSuggestion {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "suggestion_title", nullable = false, length = 255)
    private String suggestionTitle;

    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "proposed_change_json", nullable = false, columnDefinition = "TEXT")
    private String proposedChangeJson = "{}";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING_USER_CONSENT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
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

    public String getSuggestionTitle() { return suggestionTitle; }
    public void setSuggestionTitle(String suggestionTitle) { this.suggestionTitle = suggestionTitle; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getProposedChangeJson() { return proposedChangeJson; }
    public void setProposedChangeJson(String proposedChangeJson) { this.proposedChangeJson = proposedChangeJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
