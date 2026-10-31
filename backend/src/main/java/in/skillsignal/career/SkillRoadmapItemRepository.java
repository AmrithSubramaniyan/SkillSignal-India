package in.skillsignal.career;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRoadmapItemRepository extends JpaRepository<SkillRoadmapItem, UUID> {
    List<SkillRoadmapItem> findByUserIdOrderByWeekNumberAscSkillNameAsc(UUID userId);
    Optional<SkillRoadmapItem> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserId(UUID userId);
}

