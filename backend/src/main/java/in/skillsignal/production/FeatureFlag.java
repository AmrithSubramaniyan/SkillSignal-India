package in.skillsignal.production;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    private UUID id;

    @Column(name = "flag_key", nullable = false, unique = true, length = 100)
    private String flagKey;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "free_tier_enabled", nullable = false)
    private boolean freeTierEnabled = true;

    @Column(name = "paid_tier_enabled", nullable = false)
    private boolean paidTierEnabled = true;

    @Column(name = "free_tier_limit", nullable = false, length = 100)
    private String freeTierLimit;

    @Column(name = "paid_tier_limit", nullable = false, length = 100)
    private String paidTierLimit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isFreeTierEnabled() { return freeTierEnabled; }
    public void setFreeTierEnabled(boolean freeTierEnabled) { this.freeTierEnabled = freeTierEnabled; }

    public boolean isPaidTierEnabled() { return paidTierEnabled; }
    public void setPaidTierEnabled(boolean paidTierEnabled) { this.paidTierEnabled = paidTierEnabled; }

    public String getFreeTierLimit() { return freeTierLimit; }
    public void setFreeTierLimit(String freeTierLimit) { this.freeTierLimit = freeTierLimit; }

    public String getPaidTierLimit() { return paidTierLimit; }
    public void setPaidTierLimit(String paidTierLimit) { this.paidTierLimit = paidTierLimit; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

