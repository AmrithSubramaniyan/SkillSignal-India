package in.skillsignal.match;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_matches")
public class JobMatch {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "resume_version_id", nullable = false)
    private UUID resumeVersionId;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(name = "required_skills_score", nullable = false)
    private int requiredSkillsScore;

    @Column(name = "nice_to_have_score", nullable = false)
    private int niceToHaveScore;

    @Column(name = "experience_score", nullable = false)
    private int experienceScore;

    @Column(name = "location_score", nullable = false)
    private int locationScore;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

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
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public UUID getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(UUID resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public int getRequiredSkillsScore() { return requiredSkillsScore; }
    public void setRequiredSkillsScore(int requiredSkillsScore) { this.requiredSkillsScore = requiredSkillsScore; }
    public int getNiceToHaveScore() { return niceToHaveScore; }
    public void setNiceToHaveScore(int niceToHaveScore) { this.niceToHaveScore = niceToHaveScore; }
    public int getExperienceScore() { return experienceScore; }
    public void setExperienceScore(int experienceScore) { this.experienceScore = experienceScore; }
    public int getLocationScore() { return locationScore; }
    public void setLocationScore(int locationScore) { this.locationScore = locationScore; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
