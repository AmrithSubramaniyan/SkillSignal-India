package in.skillsignal.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {
    Optional<UserNotificationPreference> findByUnsubscribeTokenHash(String unsubscribeTokenHash);
    void deleteByUserId(UUID userId);
}
