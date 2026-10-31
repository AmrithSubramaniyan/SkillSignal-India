package in.skillsignal.resume;

import in.skillsignal.skill.SkillCatalogService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TruthfulnessVerifier {

    private final SkillCatalogService skillCatalogService;

    private static final List<String> DEGREE_PATTERNS = List.of(
            "ph.d", "phd", "m.tech", "mtech", "b.tech", "btech", "b.e.", "m.b.a", "mba",
            "mbbs", "m.d.", "b.pharm", "m.pharm", "chartered accountant", "llb", "ll.m",
            "m.sc", "b.sc", "mca", "bca"
    );

    public TruthfulnessVerifier(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    public record ClaimTrace(
            String claim,
            String category, // SKILL, DEGREE, EXPERIENCE
            String status,   // SUPPORTED, BLOCKED
            String sourceEvidence
    ) {}

    public record VerificationReport(
            boolean passed,
            List<String> unsupportedClaims,
            List<ClaimTrace> claimTraces
    ) {}

    public VerificationReport verify(
            String originalResumeText,
            List<String> verifiedResumeSkills,
            List<String> profileSkills,
            Double candidateYearsOfExperience,
            List<String> jobSkillsToWatch,
            String tailoredText
    ) {
        String origLower = originalResumeText != null ? originalResumeText.toLowerCase(Locale.ROOT) : "";
        String draftLower = tailoredText != null ? tailoredText.toLowerCase(Locale.ROOT) : "";

        Map<String, String> aliasMap = skillCatalogService.buildAliasToCanonicalMap();

        Set<String> supportedSkillNormals = new HashSet<>();
        Map<String, String> skillEvidenceSource = new HashMap<>();

        if (verifiedResumeSkills != null) {
            for (String s : verifiedResumeSkills) {
                if (s == null || s.isBlank()) continue;
                String canonical = skillCatalogService.normalizeSkill(s, aliasMap);
                String norm = SkillCatalogService.normalizeRawToken(canonical);
                supportedSkillNormals.add(norm);
                supportedSkillNormals.add(SkillCatalogService.normalizeRawToken(s));
                skillEvidenceSource.put(norm, "Verified Resume Skill: " + canonical);
            }
        }
        if (profileSkills != null) {
            for (String s : profileSkills) {
                if (s == null || s.isBlank()) continue;
                String canonical = skillCatalogService.normalizeSkill(s, aliasMap);
                String norm = SkillCatalogService.normalizeRawToken(canonical);
                supportedSkillNormals.add(norm);
                supportedSkillNormals.add(SkillCatalogService.normalizeRawToken(s));
                skillEvidenceSource.putIfAbsent(norm, "Career Profile Skill: " + canonical);
            }
        }

        // Candidate pool of skills to check in draft: all catalog canonical skills + job skills
        Set<String> skillsToInspect = new LinkedHashSet<>(aliasMap.values());
        if (jobSkillsToWatch != null) {
            skillsToInspect.addAll(jobSkillsToWatch);
        }

        List<String> unsupportedClaims = new ArrayList<>();
        List<ClaimTrace> traces = new ArrayList<>();

        for (String skill : skillsToInspect) {
            if (skill == null || skill.length() < 2) continue;
            if (containsToken(draftLower, skill.toLowerCase(Locale.ROOT))) {
                String norm = SkillCatalogService.normalizeRawToken(skill);
                boolean inVerifiedOrProfile = supportedSkillNormals.contains(norm);
                boolean inOriginalText = containsToken(origLower, skill.toLowerCase(Locale.ROOT));

                if (inVerifiedOrProfile) {
                    traces.add(new ClaimTrace(
                            skill,
                            "SKILL",
                            "SUPPORTED",
                            skillEvidenceSource.getOrDefault(norm, "Verified Resume/Profile")
                    ));
                } else if (inOriginalText) {
                    traces.add(new ClaimTrace(
                            skill,
                            "SKILL",
                            "SUPPORTED",
                            "Original Resume Text"
                    ));
                } else {
                    unsupportedClaims.add("Unsupported skill claim: " + skill + " (not in resume or profile)");
                    traces.add(new ClaimTrace(
                            skill,
                            "SKILL",
                            "BLOCKED",
                            "Missing from both original resume and career profile"
                    ));
                }
            }
        }

        // 2. Degree / Qualification verification
        for (String deg : DEGREE_PATTERNS) {
            if (containsToken(draftLower, deg)) {
                if (containsToken(origLower, deg)) {
                    traces.add(new ClaimTrace(deg.toUpperCase(Locale.ROOT), "DEGREE", "SUPPORTED", "Original Resume Text"));
                } else {
                    unsupportedClaims.add("Unsupported degree/qualification claim: " + deg.toUpperCase(Locale.ROOT));
                    traces.add(new ClaimTrace(
                            deg.toUpperCase(Locale.ROOT),
                            "DEGREE",
                            "BLOCKED",
                            "Degree not present in original resume"
                    ));
                }
            }
        }

        // 3. Experience years verification
        if (candidateYearsOfExperience != null) {
            Matcher expMatcher = Pattern.compile("\\b(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*(?:years|year|yrs)\\s+(?:of\\s+)?experience\\b")
                    .matcher(draftLower);
            while (expMatcher.find()) {
                double claimedYears = Double.parseDouble(expMatcher.group(1));
                if (claimedYears > candidateYearsOfExperience + 0.5) {
                    String msg = String.format(Locale.US,
                            "Unsupported experience claim: %.1f years exceeds verified %.1f years",
                            claimedYears, candidateYearsOfExperience);
                    unsupportedClaims.add(msg);
                    traces.add(new ClaimTrace(
                            claimedYears + " years experience",
                            "EXPERIENCE",
                            "BLOCKED",
                            "Exceeds verified " + candidateYearsOfExperience + " years"
                    ));
                } else {
                    traces.add(new ClaimTrace(
                            claimedYears + " years experience",
                            "EXPERIENCE",
                            "SUPPORTED",
                            "Within verified " + candidateYearsOfExperience + " years"
                    ));
                }
            }
        }

        return new VerificationReport(unsupportedClaims.isEmpty(), unsupportedClaims, traces);
    }

    private static boolean containsToken(String text, String token) {
        String regex = "(?<![a-z0-9+#])" + Pattern.quote(token) + "(?![a-z0-9+#])";
        return Pattern.compile(regex).matcher(text).find();
    }
}
