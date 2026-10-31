package in.skillsignal.job;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String company;

    @Column(length = 255)
    private String location;

    @Column(name = "work_mode", length = 64)
    private String workMode;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "raw_description", nullable = false, columnDefinition = "TEXT")
    private String rawDescription;

    @Column(name = "parsed_json", nullable = false, columnDefinition = "TEXT")
    private String parsedJson;

    @Column(name = "required_skills_json", nullable = false, columnDefinition = "TEXT")
    private String requiredSkillsJson;

    @Column(name = "nice_to_have_skills_json", nullable = false, columnDefinition = "TEXT")
    private String niceToHaveSkillsJson;

    @Column(name = "min_years_experience")
    private Double minYearsExperience;

    @Column(name = "source", nullable = false, length = 100)
    private String source = "manual";

    @Column(name = "external_job_id", length = 255)
    private String externalJobId;

    @Column(name = "employment_type", length = 64)
    private String employmentType = "FULL_TIME";

    @Column(name = "salary_min")
    private Double salaryMin;

    @Column(name = "salary_max")
    private Double salaryMax;

    @Column(name = "salary_currency", length = 16)
    private String salaryCurrency;

    @Column(name = "salary_period", length = 32)
    private String salaryPeriod;

    @Column(name = "required_education", length = 255)
    private String requiredEducation;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "dedup_key", length = 255)
    private String dedupKey;

    @Column(name = "source_records_json", columnDefinition = "TEXT")
    private String sourceRecordsJson;

    @Column(name = "is_stale", nullable = false)
    private boolean isStale = false;

    @Column(name = "stale_reason", length = 255)
    private String staleReason;

    @Column(name = "is_saved", nullable = false)
    private boolean isSaved = true;

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
        if (source == null) source = "manual";
        if (employmentType == null) employmentType = "FULL_TIME";
        if (postedAt == null) postedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getRawDescription() { return rawDescription; }
    public void setRawDescription(String rawDescription) { this.rawDescription = rawDescription; }
    public String getParsedJson() { return parsedJson; }
    public void setParsedJson(String parsedJson) { this.parsedJson = parsedJson; }
    public String getRequiredSkillsJson() { return requiredSkillsJson; }
    public void setRequiredSkillsJson(String requiredSkillsJson) { this.requiredSkillsJson = requiredSkillsJson; }
    public String getNiceToHaveSkillsJson() { return niceToHaveSkillsJson; }
    public void setNiceToHaveSkillsJson(String niceToHaveSkillsJson) { this.niceToHaveSkillsJson = niceToHaveSkillsJson; }
    public Double getMinYearsExperience() { return minYearsExperience; }
    public void setMinYearsExperience(Double minYearsExperience) { this.minYearsExperience = minYearsExperience; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getExternalJobId() { return externalJobId; }
    public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public Double getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Double salaryMin) { this.salaryMin = salaryMin; }
    public Double getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Double salaryMax) { this.salaryMax = salaryMax; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }
    public String getSalaryPeriod() { return salaryPeriod; }
    public void setSalaryPeriod(String salaryPeriod) { this.salaryPeriod = salaryPeriod; }
    public String getRequiredEducation() { return requiredEducation; }
    public void setRequiredEducation(String requiredEducation) { this.requiredEducation = requiredEducation; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public String getSourceRecordsJson() { return sourceRecordsJson; }
    public void setSourceRecordsJson(String sourceRecordsJson) { this.sourceRecordsJson = sourceRecordsJson; }
    public boolean isStale() { return isStale; }
    public void setStale(boolean stale) { isStale = stale; }
    public String getStaleReason() { return staleReason; }
    public void setStaleReason(String staleReason) { this.staleReason = staleReason; }
    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) { isSaved = saved; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
