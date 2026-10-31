package in.skillsignal.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationSubmissionAttemptRepository extends JpaRepository<ApplicationSubmissionAttempt, UUID> {
    List<ApplicationSubmissionAttempt> findBySubmissionIdAndUserIdOrderByCreatedAtAsc(UUID submissionId, UUID userId);
    void deleteByUserId(UUID userId);
}

