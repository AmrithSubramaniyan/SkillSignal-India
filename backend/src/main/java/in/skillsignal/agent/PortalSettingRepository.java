package in.skillsignal.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalSettingRepository extends JpaRepository<PortalSetting, UUID> {
    List<PortalSetting> findByUserIdOrderByPortalAsc(UUID userId);
    Optional<PortalSetting> findByUserIdAndPortalIgnoreCase(UUID userId, String portal);
    void deleteByUserId(UUID userId);
}

