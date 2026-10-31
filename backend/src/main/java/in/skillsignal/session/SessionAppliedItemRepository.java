package in.skillsignal.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAppliedItemRepository extends JpaRepository<SessionAppliedItem, UUID> {

    boolean existsByEventId(UUID eventId);

    boolean existsByApplicationId(UUID applicationId);

    Optional<SessionAppliedItem> findByApplicationId(UUID applicationId);

    List<SessionAppliedItem> findBySessionIdOrderByAppliedAtAsc(UUID sessionId);

    List<SessionAppliedItem> findByUserIdAndSessionIdIsNullOrderByAppliedAtAsc(UUID userId);

    List<SessionAppliedItem> findByUserIdOrderByAppliedAtDesc(UUID userId);

    long countBySessionId(UUID sessionId);

    void deleteByUserId(UUID userId);
}

