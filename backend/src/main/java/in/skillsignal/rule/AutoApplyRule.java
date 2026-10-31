package in.skillsignal.rule;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_apply_rules")
public class AutoApplyRule {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "priority_order", nullable = false)
    private int priorityOrder = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "conditions_json", nullable = false, columnDefinition = "TEXT")
    private String conditionsJson = "[]";

    @Column(name = "tier_t1_policy", nullable = false, length = 32)
    private String tierT1Policy = "REVIEW";

    @Column(name = "tier_t2_policy", nullable = false, length = 32)
    private String tierT2Policy = "ELIGIBLE";

    @Column(name = "tier_t3_policy", nullable = false, length = 32)
    private String tierT3Policy = "ELIGIBLE";

    @Column(name = "time_window_start", nullable = false, length = 16)
    private String timeWindowStart = "09:00";

    @Column(name = "time_window_end", nullable = false, length = 16)
    private String timeWindowEnd = "19:00";

    @Column(name = "company_cooldown_days", nullable = false)
    private int companyCooldownDays = 30;

    @Column(name = "override_company_cooldown_if_approved", nullable = false)
    private boolean overrideCompanyCooldownIfApproved = false;

    @Column(name = "min_spread_minutes", nullable = false)
    private int minSpreadMinutes = 15;

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

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public int getPriorityOrder() { return priorityOrder; }
    public void setPriorityOrder(int priorityOrder) { this.priorityOrder = priorityOrder; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }

    public String getTierT1Policy() { return tierT1Policy; }
    public void setTierT1Policy(String tierT1Policy) { this.tierT1Policy = tierT1Policy; }

    public String getTierT2Policy() { return tierT2Policy; }
    public void setTierT2Policy(String tierT2Policy) { this.tierT2Policy = tierT2Policy; }

    public String getTierT3Policy() { return tierT3Policy; }
    public void setTierT3Policy(String tierT3Policy) { this.tierT3Policy = tierT3Policy; }

    public String getTimeWindowStart() { return timeWindowStart; }
    public void setTimeWindowStart(String timeWindowStart) { this.timeWindowStart = timeWindowStart; }

    public String getTimeWindowEnd() { return timeWindowEnd; }
    public void setTimeWindowEnd(String timeWindowEnd) { this.timeWindowEnd = timeWindowEnd; }

    public int getCompanyCooldownDays() { return companyCooldownDays; }
    public void setCompanyCooldownDays(int companyCooldownDays) { this.companyCooldownDays = companyCooldownDays; }

    public boolean isOverrideCompanyCooldownIfApproved() { return overrideCompanyCooldownIfApproved; }
    public void setOverrideCompanyCooldownIfApproved(boolean overrideCompanyCooldownIfApproved) {
        this.overrideCompanyCooldownIfApproved = overrideCompanyCooldownIfApproved;
    }

    public int getMinSpreadMinutes() { return minSpreadMinutes; }
    public void setMinSpreadMinutes(int minSpreadMinutes) { this.minSpreadMinutes = minSpreadMinutes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
