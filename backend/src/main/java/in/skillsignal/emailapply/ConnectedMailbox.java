package in.skillsignal.emailapply;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connected_mailboxes")
public class ConnectedMailbox {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    @Column(name = "scopes", nullable = false, length = 512)
    private String scopes;

    @JsonIgnore
    @Column(name = "encrypted_token_ref", nullable = false, columnDefinition = "TEXT")
    private String encryptedTokenRef;

    @Column(name = "ack_detection_enabled", nullable = false)
    private boolean ackDetectionEnabled = false;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "CONNECTED";

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (connectedAt == null) connectedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }

    public String getEncryptedTokenRef() { return encryptedTokenRef; }
    public void setEncryptedTokenRef(String encryptedTokenRef) { this.encryptedTokenRef = encryptedTokenRef; }

    public boolean isAckDetectionEnabled() { return ackDetectionEnabled; }
    public void setAckDetectionEnabled(boolean ackDetectionEnabled) { this.ackDetectionEnabled = ackDetectionEnabled; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    @Override
    public String toString() {
        // Golden Rule: Tokens are NEVER logged in toString()
        return "ConnectedMailbox{id=" + id + ", provider='" + provider + "', emailAddress='" + emailAddress + "', status='" + status + "'}";
    }
}
