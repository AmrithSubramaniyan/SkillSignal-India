package in.skillsignal.application;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "applications",
    uniqueConstraints = @UniqueConstraint(name = "uq_applications_user_job", columnNames = {"user_id", "job_id"})
)
public class Application {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Column(nullable = false, length = 64)
    private String status = "SAVED";

    @Column(name = "sub_state", length = 64)
    private String subState;

    @Column(name = "apply_mode", nullable = false, length = 64)
    private String applyMode = "ASSISTED";

    @Column(name = "submission_method", nullable = false, length = 64)
    private String submissionMethod = "ASSISTED";

    @Column(name = "confirmation_type", nullable = false, length = 64)
    private String confirmationType = "UNCONFIRMED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recruiter_contacts", columnDefinition = "TEXT")
    private String recruiterContacts;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "interview_date")
    private LocalDate interviewDate;

    @Column(name = "next_action", length = 255)
    private String nextAction;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "application_url", length = 1024)
    private String applicationUrl;

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
        if (status == null) status = "SAVED";
        if (applyMode == null) applyMode = "ASSISTED";
        if (submissionMethod == null) submissionMethod = applyMode;
        if (confirmationType == null) confirmationType = "UNCONFIRMED";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public UUID getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(UUID resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubState() { return subState; }
    public void setSubState(String subState) { this.subState = subState; }
    public String getApplyMode() { return applyMode; }
    public void setApplyMode(String applyMode) { this.applyMode = applyMode; }
    public String getSubmissionMethod() { return submissionMethod; }
    public void setSubmissionMethod(String submissionMethod) { this.submissionMethod = submissionMethod; }
    public String getConfirmationType() { return confirmationType; }
    public void setConfirmationType(String confirmationType) { this.confirmationType = confirmationType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRecruiterContacts() { return recruiterContacts; }
    public void setRecruiterContacts(String recruiterContacts) { this.recruiterContacts = recruiterContacts; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }
    public LocalDate getInterviewDate() { return interviewDate; }
    public void setInterviewDate(LocalDate interviewDate) { this.interviewDate = interviewDate; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getApplicationUrl() { return applicationUrl; }
    public void setApplicationUrl(String applicationUrl) { this.applicationUrl = applicationUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
