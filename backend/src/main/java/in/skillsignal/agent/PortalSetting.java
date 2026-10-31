package in.skillsignal.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portal_settings")
public class PortalSetting {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "portal", nullable = false, length = 64)
    private String portal;

    @Column(name = "consent_enabled", nullable = false)
    private boolean consentEnabled = false;

    @Column(name = "consent_version", length = 32)
    private String consentVersion;

    @Column(name = "typed_confirmation", length = 128)
    private String typedConfirmation;

    @Column(name = "apply_mode", nullable = false, length = 32)
    private String applyMode = "FILL_ONLY";

    @Column(name = "completed_fill_only_count", nullable = false)
    private int completedFillOnlyCount = 0;

    @Column(name = "dry_run_completed", nullable = false)
    private boolean dryRunCompleted = false;

    @Column(name = "login_state", nullable = false, length = 32)
    private String loginState = "UNKNOWN";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getPortal() { return portal; }
    public void setPortal(String portal) { this.portal = portal; }

    public boolean isConsentEnabled() { return consentEnabled; }
    public void setConsentEnabled(boolean consentEnabled) { this.consentEnabled = consentEnabled; }

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }

    public String getTypedConfirmation() { return typedConfirmation; }
    public void setTypedConfirmation(String typedConfirmation) { this.typedConfirmation = typedConfirmation; }

    public String getApplyMode() { return applyMode; }
    public void setApplyMode(String applyMode) { this.applyMode = applyMode; }

    public int getCompletedFillOnlyCount() { return completedFillOnlyCount; }
    public void setCompletedFillOnlyCount(int completedFillOnlyCount) { this.completedFillOnlyCount = completedFillOnlyCount; }

    public boolean isDryRunCompleted() { return dryRunCompleted; }
    public void setDryRunCompleted(boolean dryRunCompleted) { this.dryRunCompleted = dryRunCompleted; }

    public String getLoginState() { return loginState; }
    public void setLoginState(String loginState) { this.loginState = loginState; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

