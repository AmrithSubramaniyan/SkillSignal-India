package in.skillsignal.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {
    Optional<CareerProfile> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
