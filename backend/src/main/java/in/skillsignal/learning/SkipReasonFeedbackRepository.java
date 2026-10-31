package in.skillsignal.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkipReasonFeedbackRepository extends JpaRepository<SkipReasonFeedback, UUID> {
    List<SkipReasonFeedback> findByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteByUserId(UUID userId);
}

