package in.skillsignal.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionSummaryEmailRepository extends JpaRepository<SessionSummaryEmail, UUID> {

    Optional<SessionSummaryEmail> findBySessionId(UUID sessionId);

    boolean existsBySessionId(UUID sessionId);

    List<SessionSummaryEmail> findByUserIdOrderBySentAtDesc(UUID userId);

    long countByUserIdAndSentAtAfter(UUID userId, Instant since);

    void deleteByUserId(UUID userId);
}

