package in.skillsignal.rule;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_apply_runs")
public class AutoApplyRun {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @Column(name = "total_jobs_evaluated", nullable = false)
    private int totalJobsEvaluated = 0;

    @Column(name = "matched_jobs_count", nullable = false)
    private int matchedJobsCount = 0;

    @Column(name = "submissions_created_count", nullable = false)
    private int submissionsCreatedCount = 0;

    @Column(name = "summary_text", nullable = false, length = 512)
    private String summaryText;

    @Column(name = "results_json", nullable = false, columnDefinition = "TEXT")
    private String resultsJson = "[]";

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

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public int getTotalJobsEvaluated() { return totalJobsEvaluated; }
    public void setTotalJobsEvaluated(int totalJobsEvaluated) { this.totalJobsEvaluated = totalJobsEvaluated; }

    public int getMatchedJobsCount() { return matchedJobsCount; }
    public void setMatchedJobsCount(int matchedJobsCount) { this.matchedJobsCount = matchedJobsCount; }

    public int getSubmissionsCreatedCount() { return submissionsCreatedCount; }
    public void setSubmissionsCreatedCount(int submissionsCreatedCount) { this.submissionsCreatedCount = submissionsCreatedCount; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public String getResultsJson() { return resultsJson; }
    public void setResultsJson(String resultsJson) { this.resultsJson = resultsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
