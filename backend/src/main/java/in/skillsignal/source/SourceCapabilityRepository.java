package in.skillsignal.source;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceCapabilityRepository extends JpaRepository<SourceCapability, UUID> {
    Optional<SourceCapability> findBySourceIgnoreCase(String source);
    List<SourceCapability> findAllByOrderByEnabledDescSourceAsc();
}

