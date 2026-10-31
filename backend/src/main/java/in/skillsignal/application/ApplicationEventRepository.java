package in.skillsignal.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, UUID> {
    List<ApplicationEvent> findByApplicationIdAndUserIdOrderByCreatedAtAsc(UUID applicationId, UUID userId);
    List<ApplicationEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteByUserId(UUID userId);
}
