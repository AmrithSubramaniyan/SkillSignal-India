package in.skillsignal.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "beacon_received_at")
    private Instant beaconReceivedAt;

    @JsonIgnore
    @Column(name = "beacon_token_hash", nullable = false, length = 128)
    private String beaconTokenHash;

    @Column(name = "checkout_reason", length = 32)
    private String checkoutReason;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Asia/Kolkata";

    @Column(name = "summary_email_status", nullable = false, length = 64)
    private String summaryEmailStatus = "NONE";

    @Column(name = "checkout_event_id", unique = true)
    private UUID checkoutEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (checkedInAt == null) checkedInAt = now;
        if (lastSeenAt == null) lastSeenAt = checkedInAt;
        if (createdAt == null) createdAt = now;
        if (timeZone == null || timeZone.isBlank()) timeZone = "Asia/Kolkata";
        if (summaryEmailStatus == null || summaryEmailStatus.isBlank()) summaryEmailStatus = "NONE";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Instant getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(Instant checkedOutAt) { this.checkedOutAt = checkedOutAt; }

    public Instant getBeaconReceivedAt() { return beaconReceivedAt; }
    public void setBeaconReceivedAt(Instant beaconReceivedAt) { this.beaconReceivedAt = beaconReceivedAt; }

    public String getBeaconTokenHash() { return beaconTokenHash; }
    public void setBeaconTokenHash(String beaconTokenHash) { this.beaconTokenHash = beaconTokenHash; }

    public String getCheckoutReason() { return checkoutReason; }
    public void setCheckoutReason(String checkoutReason) { this.checkoutReason = checkoutReason; }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public String getSummaryEmailStatus() { return summaryEmailStatus; }
    public void setSummaryEmailStatus(String summaryEmailStatus) { this.summaryEmailStatus = summaryEmailStatus; }

    public UUID getCheckoutEventId() { return checkoutEventId; }
    public void setCheckoutEventId(UUID checkoutEventId) { this.checkoutEventId = checkoutEventId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

