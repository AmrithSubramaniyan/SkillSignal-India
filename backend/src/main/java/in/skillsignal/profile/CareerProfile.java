package in.skillsignal.profile;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_profiles")
public class CareerProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "target_titles", columnDefinition = "TEXT")
    private String targetTitles;

    @Column(name = "years_of_experience", nullable = false)
    private double yearsOfExperience = 0.0;

    @Column(name = "cities", columnDefinition = "TEXT")
    private String cities;

    @Column(name = "work_modes", columnDefinition = "TEXT")
    private String workModes;

    @Column(name = "expected_salary")
    private Double expectedSalary;

    @Column(name = "salary_currency", length = 16)
    private String salaryCurrency = "INR";

    @Column(name = "salary_period", length = 32)
    private String salaryPeriod = "LPA";

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "employment_types", columnDefinition = "TEXT")
    private String employmentTypes;

    @Column(name = "country", length = 100)
    private String country = "India";

    @Column(name = "languages", columnDefinition = "TEXT")
    private String languages;

    @Column(name = "time_zone", length = 100)
    private String timeZone = "Asia/Kolkata";

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
    public String getTargetTitles() { return targetTitles; }
    public void setTargetTitles(String targetTitles) { this.targetTitles = targetTitles; }
    public double getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(double yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }
    public String getCities() { return cities; }
    public void setCities(String cities) { this.cities = cities; }
    public String getWorkModes() { return workModes; }
    public void setWorkModes(String workModes) { this.workModes = workModes; }
    public Double getExpectedSalary() { return expectedSalary; }
    public void setExpectedSalary(Double expectedSalary) { this.expectedSalary = expectedSalary; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }
    public String getSalaryPeriod() { return salaryPeriod; }
    public void setSalaryPeriod(String salaryPeriod) { this.salaryPeriod = salaryPeriod; }
    public Integer getNoticePeriodDays() { return noticePeriodDays; }
    public void setNoticePeriodDays(Integer noticePeriodDays) { this.noticePeriodDays = noticePeriodDays; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getEmploymentTypes() { return employmentTypes; }
    public void setEmploymentTypes(String employmentTypes) { this.employmentTypes = employmentTypes; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

