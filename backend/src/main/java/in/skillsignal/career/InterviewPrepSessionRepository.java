package in.skillsignal.career;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewPrepSessionRepository extends JpaRepository<InterviewPrepSession, UUID> {
    List<InterviewPrepSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteByUserId(UUID userId);
}

