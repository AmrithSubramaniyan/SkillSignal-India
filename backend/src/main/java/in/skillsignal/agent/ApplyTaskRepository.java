package in.skillsignal.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplyTaskRepository extends JpaRepository<ApplyTask, UUID> {
    List<ApplyTask> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<ApplyTask> findByUserIdAndStatusInOrderByCreatedAtAsc(UUID userId, List<String> statuses);
    List<ApplyTask> findByStatusInOrderByCreatedAtAsc(List<String> statuses);
    Optional<ApplyTask> findByIdAndUserId(UUID id, UUID userId);
    Optional<ApplyTask> findFirstByUserIdAndJobIdAndStatusIn(UUID userId, UUID jobId, List<String> statuses);
    void deleteByUserId(UUID userId);
}

