package in.skillsignal.eligibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_apply_preferences")
public class AutoApplyPreference {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "min_matched_skills", nullable = false)
    private int minMatchedSkills = 3;

    @Column(name = "require_50_percent_skill_coverage", nullable = false)
    private boolean require50PercentSkillCoverage = true;

    @Column(name = "over_qualified_policy", nullable = false, length = 32)
    private String overQualifiedPolicy = "BLOCK";

    @Column(name = "no_experience_stated_policy", nullable = false, length = 32)
    private String noExperienceStatedPolicy = "REVIEW";

    @Column(name = "readiness_threshold", nullable = false)
    private int readinessThreshold = 70;

    @Column(name = "daily_cap", nullable = false)
    private int dailyCap = 5;

    @Column(name = "weekly_cap", nullable = false)
    private int weeklyCap = 20;

    @Column(name = "duplicate_cooldown_days", nullable = false)
    private int duplicateCooldownDays = 30;

    @Column(name = "global_state", nullable = false, length = 32)
    private String globalState = "ACTIVE";

    @Column(name = "enabled_sources_json", nullable = false, columnDefinition = "TEXT")
    private String enabledSourcesJson = "[\"manual\",\"company_ats\",\"email_apply\"]";

    @Column(name = "exclusions_json", nullable = false, columnDefinition = "TEXT")
    private String exclusionsJson = "{\"companies\":[],\"roles\":[],\"sources\":[],\"keywords\":[],\"countries\":[]}";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public int getMinMatchedSkills() { return minMatchedSkills; }
    public void setMinMatchedSkills(int minMatchedSkills) { this.minMatchedSkills = minMatchedSkills; }

    public boolean isRequire50PercentSkillCoverage() { return require50PercentSkillCoverage; }
    public void setRequire50PercentSkillCoverage(boolean require50PercentSkillCoverage) { this.require50PercentSkillCoverage = require50PercentSkillCoverage; }

    public String getOverQualifiedPolicy() { return overQualifiedPolicy; }
    public void setOverQualifiedPolicy(String overQualifiedPolicy) { this.overQualifiedPolicy = overQualifiedPolicy; }

    public String getNoExperienceStatedPolicy() { return noExperienceStatedPolicy; }
    public void setNoExperienceStatedPolicy(String noExperienceStatedPolicy) { this.noExperienceStatedPolicy = noExperienceStatedPolicy; }

    public int getReadinessThreshold() { return readinessThreshold; }
    public void setReadinessThreshold(int readinessThreshold) { this.readinessThreshold = readinessThreshold; }

    public int getDailyCap() { return dailyCap; }
    public void setDailyCap(int dailyCap) { this.dailyCap = dailyCap; }

    public int getWeeklyCap() { return weeklyCap; }
    public void setWeeklyCap(int weeklyCap) { this.weeklyCap = weeklyCap; }

    public int getDuplicateCooldownDays() { return duplicateCooldownDays; }
    public void setDuplicateCooldownDays(int duplicateCooldownDays) { this.duplicateCooldownDays = duplicateCooldownDays; }

    public String getGlobalState() { return globalState; }
    public void setGlobalState(String globalState) { this.globalState = globalState; }

    public String getEnabledSourcesJson() { return enabledSourcesJson; }
    public void setEnabledSourcesJson(String enabledSourcesJson) { this.enabledSourcesJson = enabledSourcesJson; }

    public String getExclusionsJson() { return exclusionsJson; }
    public void setExclusionsJson(String exclusionsJson) { this.exclusionsJson = exclusionsJson; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

