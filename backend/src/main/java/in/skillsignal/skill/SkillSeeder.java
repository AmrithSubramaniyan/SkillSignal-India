package in.skillsignal.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
@Order(1)
public class SkillSeeder implements ApplicationRunner {

    public record SkillSeedEntry(String canonicalName, List<String> aliases, String category) {}

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    public SkillSeeder(SkillRepository skillRepository, ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (skillRepository.count() >= 250) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("seed/skills.json");
        try (InputStream is = resource.getInputStream()) {
            List<SkillSeedEntry> entries = objectMapper.readValue(is, new TypeReference<>() {});
            Set<String> existing = new HashSet<>();
            skillRepository.findAll().forEach(s -> existing.add(s.getCanonicalName().toLowerCase(Locale.ROOT)));

            List<Skill> toSave = new ArrayList<>();
            for (SkillSeedEntry entry : entries) {
                if (entry.canonicalName() == null || entry.canonicalName().isBlank()) continue;
                String key = entry.canonicalName().trim().toLowerCase(Locale.ROOT);
                if (existing.add(key)) {
                    Skill s = new Skill();
                    s.setCanonicalName(entry.canonicalName().trim());
                    s.setAliasesJson(objectMapper.writeValueAsString(
                            entry.aliases() != null ? entry.aliases() : List.of()
                    ));
                    s.setCategory(entry.category() != null ? entry.category().trim() : "General");
                    toSave.add(s);
                }
            }
            if (!toSave.isEmpty()) {
                skillRepository.saveAll(toSave);
            }
        }
    }
}

