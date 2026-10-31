package in.skillsignal.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SkillCatalogService {

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    public SkillCatalogService(SkillRepository skillRepository, ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
    }

    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    public Map<String, String> buildAliasToCanonicalMap() {
        Map<String, String> map = new HashMap<>();
        for (Skill s : skillRepository.findAll()) {
            String canonical = s.getCanonicalName();
            map.put(normalizeRawToken(canonical), canonical);
            try {
                List<String> aliases = objectMapper.readValue(s.getAliasesJson(), new TypeReference<>() {});
                for (String alias : aliases) {
                    if (alias != null && !alias.isBlank()) {
                        map.put(normalizeRawToken(alias), canonical);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    public String normalizeSkill(String rawSkill, Map<String, String> aliasMap) {
        if (rawSkill == null || rawSkill.isBlank()) return "";
        String cleaned = normalizeRawToken(rawSkill);
        if (aliasMap != null && aliasMap.containsKey(cleaned)) {
            return aliasMap.get(cleaned);
        }
        return rawSkill.trim();
    }

    public static String normalizeRawToken(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.&/\\-\\s]", "")
                .replaceAll("[_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
