package in.skillsignal.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplyEvidenceRepository extends JpaRepository<ApplyEvidence, UUID> {
    List<ApplyEvidence> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ApplyEvidence> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}

