package in.skillsignal.match;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobMatchRepository extends JpaRepository<JobMatch, UUID> {
    List<JobMatch> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<JobMatch> findFirstByJobIdAndUserIdOrderByCreatedAtDesc(UUID jobId, UUID userId);
    Optional<JobMatch> findFirstByJobIdAndResumeVersionIdAndUserId(UUID jobId, UUID resumeVersionId, UUID userId);
    void deleteByUserId(UUID userId);
}

