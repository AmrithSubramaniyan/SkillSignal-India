package in.skillsignal.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleChangeSuggestionRepository extends JpaRepository<RuleChangeSuggestion, UUID> {
    List<RuleChangeSuggestion> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<RuleChangeSuggestion> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}
