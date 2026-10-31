package in.skillsignal.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Job> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}

