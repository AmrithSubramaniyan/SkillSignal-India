package in.skillsignal.emailapply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailApplyMessageRepository extends JpaRepository<EmailApplyMessage, UUID> {
    List<EmailApplyMessage> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<EmailApplyMessage> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}

