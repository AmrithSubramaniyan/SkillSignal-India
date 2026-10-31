package in.skillsignal.resume;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tailored_resumes")
public class TailoredResume {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resume_version_id", nullable = false)
    private UUID resumeVersionId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "keyword_gaps_json", nullable = false, columnDefinition = "TEXT")
    private String keywordGapsJson;

    @Column(name = "tips_json", nullable = false, columnDefinition = "TEXT")
    private String tipsJson;

    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "tailored_text", nullable = false, columnDefinition = "TEXT")
    private String tailoredText;

    @Column(name = "diff_json", nullable = false, columnDefinition = "TEXT")
    private String diffJson;

    @Column(name = "truthfulness_passed", nullable = false)
    private boolean truthfulnessPassed = false;

    @Column(name = "unsupported_claims_json", nullable = false, columnDefinition = "TEXT")
    private String unsupportedClaimsJson;

    @Column(name = "claim_traces_json", nullable = false, columnDefinition = "TEXT")
    private String claimTracesJson;

    @Column(name = "user_approved", nullable = false)
    private boolean userApproved = false;

    @Column(name = "approved_at")
    private Instant approvedAt;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(UUID resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public String getKeywordGapsJson() { return keywordGapsJson; }
    public void setKeywordGapsJson(String keywordGapsJson) { this.keywordGapsJson = keywordGapsJson; }
    public String getTipsJson() { return tipsJson; }
    public void setTipsJson(String tipsJson) { this.tipsJson = tipsJson; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public String getTailoredText() { return tailoredText; }
    public void setTailoredText(String tailoredText) { this.tailoredText = tailoredText; }
    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }
    public boolean isTruthfulnessPassed() { return truthfulnessPassed; }
    public void setTruthfulnessPassed(boolean truthfulnessPassed) { this.truthfulnessPassed = truthfulnessPassed; }
    public String getUnsupportedClaimsJson() { return unsupportedClaimsJson; }
    public void setUnsupportedClaimsJson(String unsupportedClaimsJson) { this.unsupportedClaimsJson = unsupportedClaimsJson; }
    public String getClaimTracesJson() { return claimTracesJson; }
    public void setClaimTracesJson(String claimTracesJson) { this.claimTracesJson = claimTracesJson; }
    public boolean isUserApproved() { return userApproved; }
    public void setUserApproved(boolean userApproved) { this.userApproved = userApproved; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
