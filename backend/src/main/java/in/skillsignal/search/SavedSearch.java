package in.skillsignal.search;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_searches")
public class SavedSearch {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "raw_query", length = 1000)
    private String rawQuery;

    @Column(name = "filters_json", nullable = false, columnDefinition = "TEXT")
    private String filtersJson;

    @Column(name = "daily_refresh_enabled", nullable = false)
    private boolean dailyRefreshEnabled = true;

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "last_match_count", nullable = false)
    private int lastMatchCount = 0;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRawQuery() { return rawQuery; }
    public void setRawQuery(String rawQuery) { this.rawQuery = rawQuery; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public boolean isDailyRefreshEnabled() { return dailyRefreshEnabled; }
    public void setDailyRefreshEnabled(boolean dailyRefreshEnabled) { this.dailyRefreshEnabled = dailyRefreshEnabled; }
    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
    public void setLastRefreshedAt(Instant lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }
    public int getLastMatchCount() { return lastMatchCount; }
    public void setLastMatchCount(int lastMatchCount) { this.lastMatchCount = lastMatchCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
