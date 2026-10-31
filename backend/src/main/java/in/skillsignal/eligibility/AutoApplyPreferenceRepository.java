package in.skillsignal.eligibility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AutoApplyPreferenceRepository extends JpaRepository<AutoApplyPreference, UUID> {
    void deleteByUserId(UUID userId);
}

