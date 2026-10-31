package in.skillsignal.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    List<OutboxEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedForRelay();

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.publishedAt IS NOT NULL AND o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

    void deleteByUserId(UUID userId);
}

