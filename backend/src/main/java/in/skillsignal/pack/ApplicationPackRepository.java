package in.skillsignal.pack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationPackRepository extends JpaRepository<ApplicationPack, UUID> {
    Optional<ApplicationPack> findByUserIdAndJobId(UUID userId, UUID jobId);
    Optional<ApplicationPack> findByIdAndUserId(UUID id, UUID userId);
    List<ApplicationPack> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    void deleteByUserId(UUID userId);
}

