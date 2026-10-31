package in.skillsignal.pack;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_packs")
public class ApplicationPack {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Column(name = "resume_hash", length = 128)
    private String resumeHash;

    @Column(name = "job_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String jobSnapshotJson;

    @Column(name = "job_snapshot_hash", nullable = false, length = 128)
    private String jobSnapshotHash;

    @Column(name = "notes_three_tones_json", nullable = false, columnDefinition = "TEXT")
    private String notesThreeTonesJson;

    @Column(name = "answers_json", nullable = false, columnDefinition = "TEXT")
    private String answersJson;

    @Column(name = "truthfulness_report_json", nullable = false, columnDefinition = "TEXT")
    private String truthfulnessReportJson;

    @Column(name = "truthfulness_passed", nullable = false)
    private boolean truthfulnessPassed = true;

    @Column(name = "readiness_score", nullable = false)
    private int readinessScore = 0;

    @Column(name = "readiness_breakdown_json", nullable = false, columnDefinition = "TEXT")
    private String readinessBreakdownJson;

    @Column(name = "routes_to_review_queue", nullable = false)
    private boolean routesToReviewQueue = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
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

    public String getResumeHash() { return resumeHash; }
    public void setResumeHash(String resumeHash) { this.resumeHash = resumeHash; }

    public String getJobSnapshotJson() { return jobSnapshotJson; }
    public void setJobSnapshotJson(String jobSnapshotJson) { this.jobSnapshotJson = jobSnapshotJson; }

    public String getJobSnapshotHash() { return jobSnapshotHash; }
    public void setJobSnapshotHash(String jobSnapshotHash) { this.jobSnapshotHash = jobSnapshotHash; }

    public String getNotesThreeTonesJson() { return notesThreeTonesJson; }
    public void setNotesThreeTonesJson(String notesThreeTonesJson) { this.notesThreeTonesJson = notesThreeTonesJson; }

    public String getAnswersJson() { return answersJson; }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }

    public String getTruthfulnessReportJson() { return truthfulnessReportJson; }
    public void setTruthfulnessReportJson(String truthfulnessReportJson) { this.truthfulnessReportJson = truthfulnessReportJson; }

    public boolean isTruthfulnessPassed() { return truthfulnessPassed; }
    public void setTruthfulnessPassed(boolean truthfulnessPassed) { this.truthfulnessPassed = truthfulnessPassed; }

    public int getReadinessScore() { return readinessScore; }
    public void setReadinessScore(int readinessScore) { this.readinessScore = readinessScore; }

    public String getReadinessBreakdownJson() { return readinessBreakdownJson; }
    public void setReadinessBreakdownJson(String readinessBreakdownJson) { this.readinessBreakdownJson = readinessBreakdownJson; }

    public boolean isRoutesToReviewQueue() { return routesToReviewQueue; }
    public void setRoutesToReviewQueue(boolean routesToReviewQueue) { this.routesToReviewQueue = routesToReviewQueue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

