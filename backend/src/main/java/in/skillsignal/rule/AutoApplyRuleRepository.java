package in.skillsignal.rule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoApplyRuleRepository extends JpaRepository<AutoApplyRule, UUID> {
    List<AutoApplyRule> findByUserIdOrderByPriorityOrderAscCreatedAtAsc(UUID userId);
    Optional<AutoApplyRule> findByIdAndUserId(UUID id, UUID userId);
    long countByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
