package in.skillsignal.emailapply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectedMailboxRepository extends JpaRepository<ConnectedMailbox, UUID> {
    List<ConnectedMailbox> findByUserIdOrderByConnectedAtDesc(UUID userId);
    Optional<ConnectedMailbox> findByIdAndUserId(UUID id, UUID userId);
    Optional<ConnectedMailbox> findFirstByUserIdAndStatusOrderByConnectedAtDesc(UUID userId, String status);
    void deleteByUserId(UUID userId);
}

