package in.skillsignal.submission;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_submission_attempts")
public class ApplicationSubmissionAttempt {

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "from_state", length = 64)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 64)
    private String toState;

    @Column(name = "rule_decision_json", nullable = false, columnDefinition = "TEXT")
    private String ruleDecisionJson;

    @Column(name = "resume_hash", length = 128)
    private String resumeHash;

    @Column(name = "answers_used_json", nullable = false, columnDefinition = "TEXT")
    private String answersUsedJson;

    @Column(name = "pack_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String packSnapshotJson;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }

    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }

    public String getRuleDecisionJson() { return ruleDecisionJson; }
    public void setRuleDecisionJson(String ruleDecisionJson) { this.ruleDecisionJson = ruleDecisionJson; }

    public String getResumeHash() { return resumeHash; }
    public void setResumeHash(String resumeHash) { this.resumeHash = resumeHash; }

    public String getAnswersUsedJson() { return answersUsedJson; }
    public void setAnswersUsedJson(String answersUsedJson) { this.answersUsedJson = answersUsedJson; }

    public String getPackSnapshotJson() { return packSnapshotJson; }
    public void setPackSnapshotJson(String packSnapshotJson) { this.packSnapshotJson = packSnapshotJson; }

    public String getProviderResponse() { return providerResponse; }
    public void setProviderResponse(String providerResponse) { this.providerResponse = providerResponse; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
