package in.skillsignal.answer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerLibraryRepository extends JpaRepository<AnswerLibraryEntry, UUID> {
    List<AnswerLibraryEntry> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<AnswerLibraryEntry> findByIdAndUserId(UUID id, UUID userId);
    Optional<AnswerLibraryEntry> findByUserIdAndNormalizedQuestion(UUID userId, String normalizedQuestion);
    void deleteByUserId(UUID userId);
}

