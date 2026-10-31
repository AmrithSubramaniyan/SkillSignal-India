package in.skillsignal.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {
    List<ResumeVersion> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ResumeVersion> findByIdAndUserId(UUID id, UUID userId);
    Optional<ResumeVersion> findFirstByUserIdAndIsDefaultTrue(UUID userId);
    long countByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
