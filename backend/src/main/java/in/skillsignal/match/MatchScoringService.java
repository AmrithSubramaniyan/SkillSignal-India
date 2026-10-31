package in.skillsignal.match;

import in.skillsignal.skill.SkillCatalogService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class MatchScoringService {

    private final SkillCatalogService skillCatalogService;

    public MatchScoringService(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    public MatchResultDto computeMatch(
            UUID jobId,
            UUID resumeVersionId,
            List<String> verifiedResumeSkills,
            Double resumeYears,
            List<String> jobRequiredSkills,
            List<String> jobNiceToHaveSkills,
            Double jobMinYears,
            String jobLocation,
            String jobWorkMode,
            List<String> userPreferredCities,
            List<String> userPreferredWorkModes
    ) {
        Map<String, String> aliasMap = skillCatalogService.buildAliasToCanonicalMap();

        Set<String> candidateCanonicalLower = new HashSet<>();
        if (verifiedResumeSkills != null) {
            for (String s : verifiedResumeSkills) {
                if (s == null || s.isBlank()) continue;
                String canonical = skillCatalogService.normalizeSkill(s, aliasMap);
                candidateCanonicalLower.add(SkillCatalogService.normalizeRawToken(canonical));
                candidateCanonicalLower.add(SkillCatalogService.normalizeRawToken(s));
            }
        }

        // 1. Required skills (max 60)
        List<String> matchedRequired = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        List<String> deduplicatedReq = deduplicateSkills(jobRequiredSkills, aliasMap);
        for (String req : deduplicatedReq) {
            String norm = SkillCatalogService.normalizeRawToken(req);
            if (candidateCanonicalLower.contains(norm)) {
                matchedRequired.add(req);
            } else {
                missingRequired.add(req);
            }
        }

        int requiredSkillsScore;
        if (deduplicatedReq.isEmpty()) {
            requiredSkillsScore = 60;
        } else {
            requiredSkillsScore = Math.round(60.0f * matchedRequired.size() / deduplicatedReq.size());
        }

        // 2. Nice-to-have skills (max 10; full 10 if none listed)
        List<String> matchedNice = new ArrayList<>();
        List<String> missingNice = new ArrayList<>();
        List<String> deduplicatedNice = deduplicateSkills(jobNiceToHaveSkills, aliasMap);
        deduplicatedNice.removeAll(deduplicatedReq);

        for (String nice : deduplicatedNice) {
            String norm = SkillCatalogService.normalizeRawToken(nice);
            if (candidateCanonicalLower.contains(norm)) {
                matchedNice.add(nice);
            } else {
                missingNice.add(nice);
            }
        }

        int niceToHaveScore;
        if (deduplicatedNice.isEmpty()) {
            niceToHaveScore = 10;
        } else {
            niceToHaveScore = Math.round(10.0f * matchedNice.size() / deduplicatedNice.size());
        }

        // 3. Experience (max 20; full 20 if resumeYears >= required; scaled below; 10 if unstated)
        int experienceScore;
        String experienceFit;
        double candidateYrs = resumeYears != null ? Math.max(0.0, resumeYears) : 0.0;
        if (jobMinYears == null) {
            experienceScore = 10;
            experienceFit = String.format(Locale.US, "Unstated in JD (Resume: %.1f yrs — neutral 10/20 applied)", candidateYrs);
        } else if (jobMinYears <= 0.0 || candidateYrs >= jobMinYears) {
            experienceScore = 20;
            experienceFit = String.format(Locale.US, "Meets requirement (Resume: %.1f yrs vs Required: %.1f+ yrs)", candidateYrs, jobMinYears);
        } else {
            experienceScore = Math.max(0, Math.min(20, Math.round((float) (20.0 * (candidateYrs / jobMinYears)))));
            experienceFit = String.format(Locale.US, "Below requirement (Resume: %.1f yrs vs Required: %.1f+ yrs)", candidateYrs, jobMinYears);
        }

        // 4. Location / Work Mode (max 10)
        int locationScore = computeLocationScore(jobLocation, jobWorkMode, userPreferredCities, userPreferredWorkModes);
        String locationFit = buildLocationFitDescription(locationScore, jobLocation, jobWorkMode);

        int totalScore = Math.max(0, Math.min(100, requiredSkillsScore + niceToHaveScore + experienceScore + locationScore));

        List<String> suggestions = buildConditionalSuggestions(
                matchedRequired, missingRequired, missingNice, jobMinYears, candidateYrs
        );

        return new MatchResultDto(
                null,
                jobId,
                resumeVersionId,
                totalScore,
                requiredSkillsScore,
                niceToHaveScore,
                experienceScore,
                locationScore,
                matchedRequired,
                missingRequired,
                matchedNice,
                missingNice,
                experienceFit,
                locationFit,
                suggestions,
                MatchResultDto.MANDATORY_DISCLAIMER,
                Instant.now()
        );
    }

    private List<String> deduplicateSkills(List<String> rawSkills, Map<String, String> aliasMap) {
        if (rawSkills == null || rawSkills.isEmpty()) return new ArrayList<>();
        Set<String> seenNorm = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : rawSkills) {
            if (raw == null || raw.isBlank()) continue;
            String canonical = skillCatalogService.normalizeSkill(raw, aliasMap);
            String norm = SkillCatalogService.normalizeRawToken(canonical);
            if (!norm.isEmpty() && seenNorm.add(norm)) {
                result.add(canonical);
            }
        }
        return result;
    }

    private int computeLocationScore(
            String jobLocation,
            String jobWorkMode,
            List<String> userCities,
            List<String> userWorkModes
    ) {
        String jLoc = jobLocation != null ? jobLocation.trim().toLowerCase(Locale.ROOT) : "";
        String jMode = jobWorkMode != null ? jobWorkMode.trim().toUpperCase(Locale.ROOT) : "";

        boolean remoteJob = "REMOTE".equals(jMode) || jLoc.contains("remote");
        boolean modeMatched = userWorkModes == null || userWorkModes.isEmpty()
                || userWorkModes.stream().anyMatch(m -> m != null && m.trim().equalsIgnoreCase(jMode));

        if (remoteJob && modeMatched) {
            return 10;
        }

        boolean cityMatched = false;
        if (userCities == null || userCities.isEmpty() || jLoc.isEmpty() || "india".equals(jLoc)) {
            cityMatched = true;
        } else {
            for (String city : userCities) {
                if (city == null || city.isBlank()) continue;
                String c = normalizeCity(city);
                if (normalizeCity(jLoc).contains(c) || c.contains(normalizeCity(jLoc))) {
                    cityMatched = true;
                    break;
                }
            }
        }

        if (cityMatched && modeMatched) return 10;
        if (cityMatched || modeMatched) return 7;
        return 4;
    }

    private String normalizeCity(String c) {
        String lower = c.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("bangalore")) return lower.replace("bangalore", "bengaluru");
        if (lower.contains("gurgaon")) return lower.replace("gurgaon", "gurugram");
        return lower;
    }

    private String buildLocationFitDescription(int score, String jobLocation, String jobWorkMode) {
        String loc = (jobLocation != null && !jobLocation.isBlank()) ? jobLocation : "Unspecified";
        String mode = (jobWorkMode != null && !jobWorkMode.isBlank()) ? jobWorkMode : "ONSITE";
        if (score >= 10) {
            return "Strong alignment with preferred location/work mode (" + loc + " / " + mode + ")";
        } else if (score >= 7) {
            return "Partial alignment with preferred location or work mode (" + loc + " / " + mode + ")";
        }
        return "Different city or work mode from profile preferences (" + loc + " / " + mode + ")";
    }

    private List<String> buildConditionalSuggestions(
            List<String> matchedReq,
            List<String> missingReq,
            List<String> missingNice,
            Double jobMinYears,
            double candidateYrs
    ) {
        List<String> suggestions = new ArrayList<>(3);

        if (!missingReq.isEmpty()) {
            String topMissing = String.join(", ", missingReq.subList(0, Math.min(3, missingReq.size())));
            suggestions.add("If you have genuine prior experience with " + topMissing +
                    ", consider adding specific project examples to your resume and verified skills list (never claim skills you do not have).");
        } else if (!matchedReq.isEmpty()) {
            String topMatched = String.join(", ", matchedReq.subList(0, Math.min(3, matchedReq.size())));
            suggestions.add("Since your verified skills cover the required core (" + topMatched +
                    "), place measurable outcomes from those projects near the top of your resume.");
        } else {
            suggestions.add("If your background aligns with the responsibilities in this description, highlight relevant domain outcomes clearly.");
        }

        if (!missingNice.isEmpty()) {
            String topNice = String.join(", ", missingNice.subList(0, Math.min(2, missingNice.size())));
            suggestions.add("If you have worked with optional skills such as " + topNice +
                    " in academic, freelance, or production work, mention that context in your summary.");
        } else {
            suggestions.add("Review the role's day-to-day responsibilities and mirror accurate terminology from your past work where applicable.");
        }

        if (jobMinYears != null && candidateYrs < jobMinYears) {
            suggestions.add(String.format(Locale.US,
                    "This role requests %.1f+ years while your verified profile lists %.1f years; if you have internship or project ownership that demonstrates readiness, highlight its scope honestly.",
                    jobMinYears, candidateYrs));
        } else {
            suggestions.add("Confirm your notice period, location/work-mode availability, and compensation expectations in your application notes before applying.");
        }

        return suggestions;
    }
}

