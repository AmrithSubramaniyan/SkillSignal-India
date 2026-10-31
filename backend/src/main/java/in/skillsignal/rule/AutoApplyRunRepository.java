package in.skillsignal.rule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoApplyRunRepository extends JpaRepository<AutoApplyRun, UUID> {
    List<AutoApplyRun> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<AutoApplyRun> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndDryRunTrue(UUID userId);
    void deleteByUserId(UUID userId);
}
