package in.skillsignal.learning;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skip_reason_feedback")
public class SkipReasonFeedback {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "skip_reason", nullable = false, length = 255)
    private String skipReason;

    @Column(name = "agree_with_skip", nullable = false)
    private boolean agreeWithSkip;

    @Column(name = "user_comment", columnDefinition = "TEXT")
    private String userComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }

    public boolean isAgreeWithSkip() { return agreeWithSkip; }
    public void setAgreeWithSkip(boolean agreeWithSkip) { this.agreeWithSkip = agreeWithSkip; }

    public String getUserComment() { return userComment; }
    public void setUserComment(String userComment) { this.userComment = userComment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
