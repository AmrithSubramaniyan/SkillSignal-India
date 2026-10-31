package in.skillsignal.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {
    List<ConsentRecord> findByUserIdOrderByConsentTypeAsc(UUID userId);
    Optional<ConsentRecord> findByUserIdAndConsentType(UUID userId, String consentType);
    void deleteByUserId(UUID userId);
}
