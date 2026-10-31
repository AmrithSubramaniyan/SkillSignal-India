package in.skillsignal.emailapply;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_apply_messages")
public class EmailApplyMessage {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "subject", nullable = false, length = 512)
    private String subject;

    @Column(name = "body_text", nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Column(name = "approved_by_user", nullable = false)
    private boolean approvedByUser = false;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "DRAFT"; // DRAFT, SENT, EXTERNALLY_CONFIRMED, NEEDS_VERIFICATION, FAILED

    @Column(name = "ack_detected_at")
    private Instant ackDetectedAt;

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

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getmailboxId() { return mailboxId; }
    public void setMailboxId(UUID mailboxId) { this.mailboxId = mailboxId; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    public UUID getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(UUID resumeVersionId) { this.resumeVersionId = resumeVersionId; }

    public boolean isApprovedByUser() { return approvedByUser; }
    public void setApprovedByUser(boolean approvedByUser) { this.approvedByUser = approvedByUser; }

    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getAckDetectedAt() { return ackDetectedAt; }
    public void setAckDetectedAt(Instant ackDetectedAt) { this.ackDetectedAt = ackDetectedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

