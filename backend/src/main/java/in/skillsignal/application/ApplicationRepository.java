package in.skillsignal.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    List<Application> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);
    Optional<Application> findByUserIdAndJobId(UUID userId, UUID jobId);
    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);
    List<Application> findByUserIdAndFollowUpDateLessThanEqualOrderByFollowUpDateAsc(UUID userId, LocalDate date);
    void deleteByUserId(UUID userId);
}

