package in.skillsignal.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {
    void deleteByUserId(UUID userId);
}
