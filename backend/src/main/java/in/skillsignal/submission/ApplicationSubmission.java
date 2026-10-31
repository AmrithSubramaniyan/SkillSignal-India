package in.skillsignal.submission;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_submissions")
public class ApplicationSubmission {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "pack_id")
    private UUID packId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "state", nullable = false, length = 64)
    private String state;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "rule_decision_json", nullable = false, columnDefinition = "TEXT")
    private String ruleDecisionJson;

    @Column(name = "why_applied_or_skipped", nullable = false, columnDefinition = "TEXT")
    private String whyAppliedOrSkipped;

    @Column(name = "resume_hash", length = 128)
    private String resumeHash;

    @Column(name = "answers_used_json", nullable = false, columnDefinition = "TEXT")
    private String answersUsedJson;

    @Column(name = "pack_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String packSnapshotJson;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "approval_token_hash", length = 128)
    private String approvalTokenHash;

    @Column(name = "approval_token_expires_at")
    private Instant approvalTokenExpiresAt;

    @Column(name = "approval_token_used_at")
    private Instant approvalTokenUsedAt;

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

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public UUID getPackId() { return packId; }
    public void setPackId(UUID packId) { this.packId = packId; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getRuleDecisionJson() { return ruleDecisionJson; }
    public void setRuleDecisionJson(String ruleDecisionJson) { this.ruleDecisionJson = ruleDecisionJson; }

    public String getWhyAppliedOrSkipped() { return whyAppliedOrSkipped; }
    public void setWhyAppliedOrSkipped(String whyAppliedOrSkipped) { this.whyAppliedOrSkipped = whyAppliedOrSkipped; }

    public String getResumeHash() { return resumeHash; }
    public void setResumeHash(String resumeHash) { this.resumeHash = resumeHash; }

    public String getAnswersUsedJson() { return answersUsedJson; }
    public void setAnswersUsedJson(String answersUsedJson) { this.answersUsedJson = answersUsedJson; }

    public String getPackSnapshotJson() { return packSnapshotJson; }
    public void setPackSnapshotJson(String packSnapshotJson) { this.packSnapshotJson = packSnapshotJson; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getApprovalTokenHash() { return approvalTokenHash; }
    public void setApprovalTokenHash(String approvalTokenHash) { this.approvalTokenHash = approvalTokenHash; }

    public Instant getApprovalTokenExpiresAt() { return approvalTokenExpiresAt; }
    public void setApprovalTokenExpiresAt(Instant approvalTokenExpiresAt) { this.approvalTokenExpiresAt = approvalTokenExpiresAt; }

    public Instant getApprovalTokenUsedAt() { return approvalTokenUsedAt; }
    public void setApprovalTokenUsedAt(Instant approvalTokenUsedAt) { this.approvalTokenUsedAt = approvalTokenUsedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
