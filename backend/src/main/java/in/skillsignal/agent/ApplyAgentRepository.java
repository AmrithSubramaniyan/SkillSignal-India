package in.skillsignal.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplyAgentRepository extends JpaRepository<ApplyAgent, UUID> {
    List<ApplyAgent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ApplyAgent> findByIdAndUserId(UUID id, UUID userId);
    Optional<ApplyAgent> findByDeviceTokenHashAndStatus(String deviceTokenHash, String status);
    void deleteByUserId(UUID userId);
}

