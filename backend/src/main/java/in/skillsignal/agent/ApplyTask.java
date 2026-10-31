package in.skillsignal.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "apply_tasks")
public class ApplyTask {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "portal", nullable = false, length = 64)
    private String portal;

    @Column(name = "apply_mode", nullable = false, length = 32)
    private String applyMode = "FILL_ONLY";

    @Column(name = "status", nullable = false, length = 64)
    private String status = "QUEUED";

    @Column(name = "blocker_type", length = 64)
    private String blockerType;

    @Column(name = "blocker_message", columnDefinition = "TEXT")
    private String blockerMessage;

    @Column(name = "confirmation_signal", length = 64)
    private String confirmationSignal;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson = "{}";

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

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

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID agentId) { this.agentId = agentId; }

    public String getPortal() { return portal; }
    public void setPortal(String portal) { this.portal = portal; }

    public String getApplyMode() { return applyMode; }
    public void setApplyMode(String applyMode) { this.applyMode = applyMode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlockerType() { return blockerType; }
    public void setBlockerType(String blockerType) { this.blockerType = blockerType; }

    public String getBlockerMessage() { return blockerMessage; }
    public void setBlockerMessage(String blockerMessage) { this.blockerMessage = blockerMessage; }

    public String getConfirmationSignal() { return confirmationSignal; }
    public void setConfirmationSignal(String confirmationSignal) { this.confirmationSignal = confirmationSignal; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

