package in.skillsignal.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    Optional<Skill> findByCanonicalNameIgnoreCase(String canonicalName);
    List<Skill> findByCategoryIgnoreCaseOrderByCanonicalNameAsc(String category);
}
