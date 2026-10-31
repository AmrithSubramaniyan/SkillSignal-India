package in.skillsignal.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationSubmissionRepository extends JpaRepository<ApplicationSubmission, UUID> {
    List<ApplicationSubmission> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<ApplicationSubmission> findByIdAndUserId(UUID id, UUID userId);
    Optional<ApplicationSubmission> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
    Optional<ApplicationSubmission> findFirstByUserIdAndJobIdOrderByUpdatedAtDesc(UUID userId, UUID jobId);
    Optional<ApplicationSubmission> findByUserIdAndApprovalTokenHash(UUID userId, String approvalTokenHash);
    void deleteByUserId(UUID userId);
}

