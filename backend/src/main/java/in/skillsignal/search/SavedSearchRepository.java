package in.skillsignal.search;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {
    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<SavedSearch> findByIdAndUserId(UUID id, UUID userId);
    List<SavedSearch> findByDailyRefreshEnabledTrue();
    void deleteByUserId(UUID userId);
}
