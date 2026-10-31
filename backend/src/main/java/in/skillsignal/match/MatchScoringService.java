package in.skillsignal.match;

import in.skillsignal.skill.SkillCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class MatchScoringService {

    private final SkillCatalogService skillCatalogService;
    private final MatchWeightsProperties weights;

    public MatchScoringService(SkillCatalogService skillCatalogService) {
        this(skillCatalogService, new MatchWeightsProperties());
    }

    @Autowired
    public MatchScoringService(SkillCatalogService skillCatalogService, MatchWeightsProperties weights) {
        this.skillCatalogService = skillCatalogService;
        this.weights = weights != null ? weights : new MatchWeightsProperties();
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
        return computeFullMatch(
                jobId,
                resumeVersionId,
                verifiedResumeSkills,
                resumeYears,
                jobRequiredSkills,
                jobNiceToHaveSkills,
                jobMinYears,
                jobLocation,
                jobWorkMode,
                userPreferredCities,
                userPreferredWorkModes,
                null,
                null,
                null,
                null,
                null
        );
    }

    public MatchResultDto computeFullMatch(
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
            List<String> userPreferredWorkModes,
            Double candidateExpectedSalary,
            Double jobSalaryMin,
            Double jobSalaryMax,
            String candidateEducationText,
            String jobRequiredEducation
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

        int maxReqWeight = weights.getRequiredSkills();
        int maxNiceWeight = weights.getNiceToHaveSkills();
        int maxExpWeight = weights.getExperience();
        int maxLocWeight = weights.getLocation();

        // 1. Required skills (configured weight, default 60)
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
            requiredSkillsScore = maxReqWeight;
        } else {
            requiredSkillsScore = Math.round((float) maxReqWeight * matchedRequired.size() / deduplicatedReq.size());
        }

        // 2. Nice-to-have skills (configured weight, default 10; full if none listed)
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
            niceToHaveScore = maxNiceWeight;
        } else {
            niceToHaveScore = Math.round((float) maxNiceWeight * matchedNice.size() / deduplicatedNice.size());
        }

        // 3. Experience (configured weight, default 20; half if unstated)
        int experienceScore;
        String experienceFit;
        double candidateYrs = resumeYears != null ? Math.max(0.0, resumeYears) : 0.0;
        if (jobMinYears == null) {
            experienceScore = maxExpWeight / 2;
            experienceFit = String.format(Locale.US, "Unstated in JD (Resume: %.1f yrs — neutral %d/%d applied)", candidateYrs, experienceScore, maxExpWeight);
        } else if (jobMinYears <= 0.0 || candidateYrs >= jobMinYears) {
            experienceScore = maxExpWeight;
            experienceFit = String.format(Locale.US, "Meets requirement (Resume: %.1f yrs vs Required: %.1f+ yrs)", candidateYrs, jobMinYears);
        } else {
            experienceScore = Math.max(0, Math.min(maxExpWeight, Math.round((float) (maxExpWeight * (candidateYrs / jobMinYears)))));
            experienceFit = String.format(Locale.US, "Below requirement (Resume: %.1f yrs vs Required: %.1f+ yrs)", candidateYrs, jobMinYears);
        }

        // 4. Location / Work Mode (configured weight, default 10)
        int locationScore = computeLocationScore(jobLocation, jobWorkMode, userPreferredCities, userPreferredWorkModes, maxLocWeight);
        String locationFit = buildLocationFitDescription(locationScore, maxLocWeight, jobLocation, jobWorkMode);

        // 5. Salary Fit & Qualification Fit ("not enough data" unless both sides exist)
        String salaryFit = computeSalaryFit(candidateExpectedSalary, jobSalaryMin, jobSalaryMax);
        String qualificationFit = computeQualificationFit(candidateEducationText, jobRequiredEducation);

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
                salaryFit,
                qualificationFit,
                null,
                null,
                suggestions,
                MatchResultDto.MANDATORY_DISCLAIMER,
                Instant.now()
        );
    }

    public String computeSalaryFit(Double candidateExpected, Double jobMin, Double jobMax) {
        if (candidateExpected == null || candidateExpected <= 0.0 || (jobMin == null && jobMax == null)) {
            return MatchResultDto.NOT_ENOUGH_DATA;
        }
        double effectiveJobCeiling = jobMax != null ? jobMax : jobMin;
        double effectiveJobFloor = jobMin != null ? jobMin : jobMax;
        if (effectiveJobCeiling >= candidateExpected) {
            return String.format(Locale.US, "Aligned (Job range %.1f-%.1f vs Expected %.1f)",
                    effectiveJobFloor, effectiveJobCeiling, candidateExpected);
        } else if (effectiveJobCeiling >= candidateExpected * 0.85) {
            return String.format(Locale.US, "Close fit (Job range %.1f-%.1f vs Expected %.1f)",
                    effectiveJobFloor, effectiveJobCeiling, candidateExpected);
        }
        return String.format(Locale.US, "Below expectation (Job range %.1f-%.1f vs Expected %.1f)",
                effectiveJobFloor, effectiveJobCeiling, candidateExpected);
    }

    public String computeQualificationFit(String candidateEducation, String jobRequiredEducation) {
        if (candidateEducation == null || candidateEducation.isBlank()
                || jobRequiredEducation == null || jobRequiredEducation.isBlank()) {
            return MatchResultDto.NOT_ENOUGH_DATA;
        }
        String candLower = candidateEducation.toLowerCase(Locale.ROOT);
        String reqLower = jobRequiredEducation.toLowerCase(Locale.ROOT);

        List<String> tokens = List.of("b.tech", "btech", "b.e", "m.tech", "mtech", "mca", "bca", "b.sc", "m.sc",
                "mba", "ca", "chartered accountant", "mbbs", "b.pharm", "m.pharm", "llb", "bachelor", "master", "computer science", "engineering");
        boolean matchedAny = false;
        for (String t : tokens) {
            if (reqLower.contains(t) && candLower.contains(t)) {
                matchedAny = true;
                break;
            }
        }
        if (!matchedAny && (reqLower.contains("b.tech") || reqLower.contains("b.e") || reqLower.contains("bachelor"))) {
            if (candLower.contains("b.tech") || candLower.contains("b.e") || candLower.contains("bachelor") || candLower.contains("engineering")) {
                matchedAny = true;
            }
        }
        if (matchedAny) {
            return "Meets qualification (" + jobRequiredEducation.trim() + ")";
        }
        return "Verify qualification (" + jobRequiredEducation.trim() + " vs Resume: " + candidateEducation.trim() + ")";
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
            List<String> userWorkModes,
            int maxLocWeight
    ) {
        String jLoc = jobLocation != null ? jobLocation.trim().toLowerCase(Locale.ROOT) : "";
        String jMode = jobWorkMode != null ? jobWorkMode.trim().toUpperCase(Locale.ROOT) : "";

        boolean remoteJob = "REMOTE".equals(jMode) || jLoc.contains("remote");
        boolean modeMatched = userWorkModes == null || userWorkModes.isEmpty()
                || userWorkModes.stream().anyMatch(m -> m != null && m.trim().equalsIgnoreCase(jMode));

        if (remoteJob && modeMatched) {
            return maxLocWeight;
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

        if (cityMatched && modeMatched) return maxLocWeight;
        if (cityMatched || modeMatched) return Math.round(maxLocWeight * 0.7f);
        return Math.round(maxLocWeight * 0.4f);
    }

    private String normalizeCity(String c) {
        String lower = c.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("bangalore")) return lower.replace("bangalore", "bengaluru");
        if (lower.contains("gurgaon")) return lower.replace("gurgaon", "gurugram");
        return lower;
    }

    private String buildLocationFitDescription(int score, int maxLocWeight, String jobLocation, String jobWorkMode) {
        String loc = (jobLocation != null && !jobLocation.isBlank()) ? jobLocation : "Unspecified";
        String mode = (jobWorkMode != null && !jobWorkMode.isBlank()) ? jobWorkMode : "ONSITE";
        if (score >= maxLocWeight) {
            return "Strong alignment with preferred location/work mode (" + loc + " / " + mode + ")";
        } else if (score >= Math.round(maxLocWeight * 0.7f)) {
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
