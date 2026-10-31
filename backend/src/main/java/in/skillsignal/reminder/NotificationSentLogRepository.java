package in.skillsignal.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationSentLogRepository extends JpaRepository<NotificationSentLog, UUID> {
    boolean existsByUserIdAndDedupKeyAndChannel(UUID userId, String dedupKey, String channel);
    List<NotificationSentLog> findByUserIdOrderBySentAtDesc(UUID userId);
    void deleteByUserId(UUID userId);
}
