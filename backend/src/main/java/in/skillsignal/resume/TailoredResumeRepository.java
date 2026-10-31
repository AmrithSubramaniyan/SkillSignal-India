package in.skillsignal.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TailoredResumeRepository extends JpaRepository<TailoredResume, UUID> {
    List<TailoredResume> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<TailoredResume> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}
