package in.skillsignal.eligibility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.pack.ApplicationPackService;
import in.skillsignal.reminder.UserNotificationPreference;
import in.skillsignal.reminder.UserNotificationPreferenceRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.skill.SkillCatalogService;
import in.skillsignal.source.SourceCapability;
import in.skillsignal.source.SourceCapabilityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class StrictEligibilityService {

    public record ExclusionListsDto(
            List<String> companies,
            List<String> roles,
            List<String> sources,
            List<String> keywords,
            List<String> countries
    ) {
        public static ExclusionListsDto empty() {
            return new ExclusionListsDto(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record CheckItemDto(
            int checkNumber,
            String name,
            boolean passed,
            String status,
            String detail
    ) {}

    public record StrictEligibilityResultDto(
            boolean eligible,
            String decision, // ELIGIBLE, ROUTE_TO_REVIEW_QUEUE, INELIGIBLE_BLOCKED
            boolean routesToReviewQueue,
            List<String> matchedSkills,
            List<String> unmatchedSkills,
            int matchedSkillsCount,
            int totalJobRequiredSkillsCount,
            double requiredSkillCoveragePercent,
            int minMatchedSkillsRequired,
            Double candidateYearsExperience,
            Double jobMinYearsExperience,
            Double jobMaxYearsExperience,
            String skillRuleOutcome,
            String experienceRuleOutcome,
            List<CheckItemDto> nineChecks,
            String whyAppliedOrSkipped
    ) {}

    public record EvaluationInput(
            List<String> candidateVerifiedSkills,
            Double candidateYearsExperience,
            boolean hasApprovedResume,
            List<String> jobRequiredSkills,
            Double jobMinYearsExperience,
            Double jobMaxYearsExperience,
            String jobTitle,
            String jobCompany,
            String jobSource,
            String jobCountry,
            String jobDescription,
            boolean jobAccessibleAndOpen,
            boolean notDuplicateWithinCooldown,
            boolean allQuestionsAnswerable,
            boolean providerPermitsMethod,
            int todayCountInUserTz,
            int weekCountInUserTz,
            int readinessScore,
            boolean truthfulnessPassed,
            AutoApplyPreference preferences,
            ExclusionListsDto exclusions,
            List<String> enabledSources
    ) {}

    private final AutoApplyPreferenceRepository preferenceRepository;
    private final UserNotificationPreferenceRepository notificationPreferenceRepository;
    private final JobRepository jobRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ApplicationRepository applicationRepository;
    private final SourceCapabilityRepository sourceCapabilityRepository;
    private final ApplicationPackService packService;
    private final SkillCatalogService skillCatalogService;
    private final ObjectMapper objectMapper;

    public StrictEligibilityService(
            AutoApplyPreferenceRepository preferenceRepository,
            UserNotificationPreferenceRepository notificationPreferenceRepository,
            JobRepository jobRepository,
            ResumeVersionRepository resumeVersionRepository,
            ApplicationRepository applicationRepository,
            SourceCapabilityRepository sourceCapabilityRepository,
            ApplicationPackService packService,
            SkillCatalogService skillCatalogService,
            ObjectMapper objectMapper
    ) {
        this.preferenceRepository = preferenceRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.jobRepository = jobRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.applicationRepository = applicationRepository;
        this.sourceCapabilityRepository = sourceCapabilityRepository;
        this.packService = packService;
        this.skillCatalogService = skillCatalogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AutoApplyPreference getOrCreatePreferences(UUID userId) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            AutoApplyPreference p = new AutoApplyPreference();
            p.setUserId(userId);
            p.setUpdatedAt(Instant.now());
            return preferenceRepository.save(p);
        });
    }

    @Transactional
    public AutoApplyPreference updatePreferences(
            UUID userId,
            Integer minMatchedSkills,
            Boolean require50PercentSkillCoverage,
            String overQualifiedPolicy,
            String noExperienceStatedPolicy,
            Integer readinessThreshold,
            Integer dailyCap,
            Integer weeklyCap,
            Integer duplicateCooldownDays,
            List<String> enabledSources,
            ExclusionListsDto exclusions
    ) {
        AutoApplyPreference pref = getOrCreatePreferences(userId);
        if (minMatchedSkills != null) {
            if (minMatchedSkills < 1 || minMatchedSkills > 20) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minMatchedSkills must be between 1 and 20");
            }
            pref.setMinMatchedSkills(minMatchedSkills);
        }
        if (require50PercentSkillCoverage != null) {
            pref.setRequire50PercentSkillCoverage(require50PercentSkillCoverage);
        }
        if (overQualifiedPolicy != null && !overQualifiedPolicy.isBlank()) {
            String p = overQualifiedPolicy.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("BLOCK", "REVIEW", "ALLOW").contains(p)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid overQualifiedPolicy: " + overQualifiedPolicy);
            }
            pref.setOverQualifiedPolicy(p);
        }
        if (noExperienceStatedPolicy != null && !noExperienceStatedPolicy.isBlank()) {
            String p = noExperienceStatedPolicy.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("REVIEW", "ALLOW", "BLOCK").contains(p)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid noExperienceStatedPolicy: " + noExperienceStatedPolicy);
            }
            pref.setNoExperienceStatedPolicy(p);
        }
        if (readinessThreshold != null) {
            pref.setReadinessThreshold(Math.max(0, Math.min(100, readinessThreshold)));
        }
        if (dailyCap != null && dailyCap >= 0) {
            pref.setDailyCap(dailyCap);
        }
        if (weeklyCap != null && weeklyCap >= 0) {
            pref.setWeeklyCap(weeklyCap);
        }
        if (duplicateCooldownDays != null && duplicateCooldownDays >= 0) {
            pref.setDuplicateCooldownDays(duplicateCooldownDays);
        }
        if (enabledSources != null) {
            pref.setEnabledSourcesJson(writeJson(enabledSources));
        }
        if (exclusions != null) {
            pref.setExclusionsJson(writeJson(exclusions));
        }
        pref.setUpdatedAt(Instant.now());
        return preferenceRepository.save(pref);
    }

    @Transactional
    public AutoApplyPreference updateGlobalState(UUID userId, String requestedState, String confirmationText) {
        if (requestedState == null || requestedState.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state is required");
        }
        String norm = requestedState.trim().toUpperCase(Locale.ROOT);
        if ("RESUME".equals(norm)) {
            norm = "ACTIVE";
        }
        if ("PAUSE".equals(norm)) {
            norm = "PAUSED";
        }
        if ("STOP".equals(norm)) {
            norm = "STOPPED";
        }
        if (!Set.of("ACTIVE", "PAUSED", "STOPPED").contains(norm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid globalState (allowed: ACTIVE, PAUSED, STOPPED)");
        }
        if ("STOPPED".equals(norm)) {
            if (confirmationText == null || !"STOP".equals(confirmationText.trim().toUpperCase(Locale.ROOT))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Stopping auto-apply requires typed confirmation 'STOP'"
                );
            }
        }
        AutoApplyPreference pref = getOrCreatePreferences(userId);
        pref.setGlobalState(norm);
        pref.setUpdatedAt(Instant.now());
        return preferenceRepository.save(pref);
    }

    @Transactional
    public StrictEligibilityResultDto evaluateForUserAndJob(
            UUID userId,
            UUID jobId,
            List<String> questions,
            Instant evaluationTime
    ) {
        Instant now = evaluationTime != null ? evaluationTime : Instant.now();
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        AutoApplyPreference pref = getOrCreatePreferences(userId);
        ExclusionListsDto exclusions = parseExclusions(pref.getExclusionsJson());
        List<String> enabledSources = parseStringList(pref.getEnabledSourcesJson());

        ResumeVersion rv = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                .orElseGet(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .findFirst()
                        .orElse(null));

        List<String> verifiedSkills = rv != null ? parseStringList(rv.getVerifiedSkillsJson()) : List.of();
        Double candidateYears = rv != null ? rv.getYearsOfExperience() : null;
        boolean hasApprovedResume = rv != null && !verifiedSkills.isEmpty();

        List<String> jobReqSkills = parseStringList(job.getRequiredSkillsJson());

        // Build or inspect pack for answer coverage, readiness, and truthfulness
        var packDto = packService.getOrBuildPack(userId, job.getId(), questions, null, pref.getReadinessThreshold());
        boolean allQuestionsAnswerable = packDto.answers().stream()
                .noneMatch(a -> a.requiresUserPause() || a.approvedAnswer() == null || a.approvedAnswer().isBlank());

        // Check duplicate within cooldown
        Instant cooldownCutoff = now.minus(pref.getDuplicateCooldownDays(), ChronoUnit.DAYS);
        List<Application> userApps = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        boolean hasDuplicate = userApps.stream()
                .anyMatch(a -> a.getJobId().equals(job.getId()) && !a.getCreatedAt().isBefore(cooldownCutoff));

        // Check provider capability
        String src = job.getSource() != null ? job.getSource().trim().toLowerCase(Locale.ROOT) : "manual";
        boolean providerPermits = sourceCapabilityRepository.findBySourceIgnoreCase(src)
                .map(SourceCapability::isEnabled)
                .orElse("manual".equalsIgnoreCase(src));

        // Compute daily and weekly counts in user's timezone
        String userTz = notificationPreferenceRepository.findById(userId)
                .map(UserNotificationPreference::getTimeZone)
                .orElse("Asia/Kolkata");
        ZoneId zoneId = ZoneId.of(userTz);
        ZonedDateTime nowZoned = ZonedDateTime.ofInstant(now, zoneId);
        Instant startOfDay = nowZoned.toLocalDate().atStartOfDay(zoneId).toInstant();
        Instant startOfWeek = nowZoned.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zoneId)
                .toInstant();

        int todayCount = (int) userApps.stream()
                .filter(a -> a.getCreatedAt() != null && !a.getCreatedAt().isBefore(startOfDay))
                .count();
        int weekCount = (int) userApps.stream()
                .filter(a -> a.getCreatedAt() != null && !a.getCreatedAt().isBefore(startOfWeek))
                .count();

        EvaluationInput input = new EvaluationInput(
                verifiedSkills,
                candidateYears,
                hasApprovedResume,
                jobReqSkills,
                job.getMinYearsExperience(),
                job.getMaxYearsExperience(),
                job.getTitle(),
                job.getCompany(),
                src,
                job.getCountry() != null ? job.getCountry() : "India",
                job.getRawDescription(),
                !job.isStale(),
                !hasDuplicate,
                allQuestionsAnswerable,
                providerPermits,
                todayCount,
                weekCount,
                packDto.readinessScore(),
                packDto.truthfulnessPassed(),
                pref,
                exclusions,
                enabledSources
        );

        return evaluateCandidateAgainstJob(input);
    }

    /**
     * Pure deterministic evaluation of the Strict Match Rule and the 9 Eligibility Checks.
     * Any failure routes to Review Queue (never auto-submits).
     */
    public StrictEligibilityResultDto evaluateCandidateAgainstJob(EvaluationInput in) {
        AutoApplyPreference pref = in.preferences() != null ? in.preferences() : new AutoApplyPreference();
        int minSkills = pref.getMinMatchedSkills();
        boolean guard50Percent = pref.isRequire50PercentSkillCoverage();

        Map<String, String> aliasMap = skillCatalogService.buildAliasToCanonicalMap();

        Set<String> candidateNorms = new HashSet<>();
        if (in.candidateVerifiedSkills() != null) {
            for (String s : in.candidateVerifiedSkills()) {
                if (s == null || s.isBlank()) continue;
                String canonical = skillCatalogService.normalizeSkill(s, aliasMap);
                candidateNorms.add(SkillCatalogService.normalizeRawToken(canonical));
                candidateNorms.add(SkillCatalogService.normalizeRawToken(s));
            }
        }

        List<String> matchedSkills = new ArrayList<>();
        List<String> unmatchedSkills = new ArrayList<>();
        List<String> jobSkills = in.jobRequiredSkills() != null ? in.jobRequiredSkills() : List.of();

        for (String req : jobSkills) {
            if (req == null || req.isBlank()) continue;
            String canonical = skillCatalogService.normalizeSkill(req, aliasMap);
            String norm = SkillCatalogService.normalizeRawToken(canonical);
            if (candidateNorms.contains(norm) || candidateNorms.contains(SkillCatalogService.normalizeRawToken(req))) {
                matchedSkills.add(canonical);
            } else {
                unmatchedSkills.add(canonical);
            }
        }

        int matchedCount = matchedSkills.size();
        int totalJobSkills = matchedSkills.size() + unmatchedSkills.size();
        double coveragePercent = totalJobSkills > 0
                ? Math.round((matchedCount * 1000.0) / totalJobSkills) / 10.0
                : 0.0;

        // --- STRICT SKILL RULE ---
        boolean skillRulePassed;
        String skillRuleOutcome;
        if (totalJobSkills == 0) {
            skillRulePassed = false;
            skillRuleOutcome = "NO_SKILLS_LISTED_REVIEW: Job lists 0 skills -> routes to Review Queue.";
        } else if (totalJobSkills < minSkills) {
            if (matchedCount == totalJobSkills) {
                skillRulePassed = true;
                skillRuleOutcome = "PASS_FEWER_THAN_MIN_ALL_MATCHED: Job lists " + totalJobSkills
                        + " (< " + minSkills + ") skill(s) and all " + matchedCount + " matched.";
            } else {
                skillRulePassed = false;
                skillRuleOutcome = "FAIL_FEWER_THAN_MIN_NOT_ALL_MATCHED: Job lists " + totalJobSkills
                        + " (< " + minSkills + ") skill(s); all must match, but only " + matchedCount + " matched.";
            }
        } else {
            if (matchedCount < minSkills) {
                skillRulePassed = false;
                skillRuleOutcome = "FAIL_BELOW_MIN_SKILLS: Matched " + matchedCount + " verified skill(s), which is below required MIN_SKILLS=" + minSkills + ".";
            } else if (guard50Percent && coveragePercent < 50.0) {
                skillRulePassed = false;
                skillRuleOutcome = "FAIL_BELOW_50_PERCENT_COVERAGE: Matched " + matchedCount + "/" + totalJobSkills
                        + " (" + coveragePercent + "%), below 50% required-skill coverage guard.";
            } else {
                skillRulePassed = true;
                skillRuleOutcome = "PASS_STRICT_SKILLS: Matched " + matchedCount + "/" + totalJobSkills
                        + " verified skills (" + coveragePercent + "%, MIN_SKILLS=" + minSkills + ").";
            }
        }

        // --- STRICT EXPERIENCE RULE ---
        boolean expRulePassed;
        String expRuleOutcome;
        Double candYrs = in.candidateYearsExperience();
        Double minYrs = in.jobMinYearsExperience();
        Double maxYrs = in.jobMaxYearsExperience();

        if (minYrs == null && maxYrs == null) {
            if ("ALLOW".equalsIgnoreCase(pref.getNoExperienceStatedPolicy())) {
                expRulePassed = true;
                expRuleOutcome = "PASS_NO_EXP_STATED_ALLOWED";
            } else {
                expRulePassed = false;
                expRuleOutcome = "NO_EXPERIENCE_STATED_REVIEW: Job states no experience range -> routes to Review Queue by default.";
            }
        } else if (candYrs == null) {
            expRulePassed = false;
            expRuleOutcome = "CANDIDATE_EXP_MISSING_REVIEW: Candidate experience years not stated.";
        } else if (minYrs != null && candYrs < minYrs) {
            expRulePassed = false;
            expRuleOutcome = "BELOW_MINIMUM_NO: Candidate experience (" + candYrs + " yrs) is below job minimum (" + minYrs + " yrs).";
        } else if (maxYrs != null && candYrs > maxYrs) {
            String oqPolicy = pref.getOverQualifiedPolicy() != null ? pref.getOverQualifiedPolicy().toUpperCase(Locale.ROOT) : "BLOCK";
            if ("ALLOW".equals(oqPolicy)) {
                expRulePassed = true;
                expRuleOutcome = "PASS_OVER_QUALIFIED_ALLOWED: Candidate (" + candYrs + " yrs) > max (" + maxYrs + " yrs), permitted by policy.";
            } else if ("REVIEW".equals(oqPolicy)) {
                expRulePassed = false;
                expRuleOutcome = "OVER_QUALIFIED_REVIEW: Candidate (" + candYrs + " yrs) > max (" + maxYrs + " yrs) -> routes to Review.";
            } else {
                expRulePassed = false;
                expRuleOutcome = "OVER_QUALIFIED_BLOCKED: Candidate (" + candYrs + " yrs) exceeds stated max (" + maxYrs + " yrs); blocked by default policy.";
            }
        } else {
            expRulePassed = true;
            expRuleOutcome = "PASS_EXPERIENCE_RANGE: Candidate experience (" + candYrs + " yrs) is within stated range.";
        }

        // --- EXCLUSION CHECK ---
        ExclusionListsDto excl = in.exclusions() != null ? in.exclusions() : ExclusionListsDto.empty();
        List<String> exclusionHits = new ArrayList<>();
        if (in.jobCompany() != null && matchesAnyCaseInsensitive(in.jobCompany(), excl.companies())) {
            exclusionHits.add("Excluded company: " + in.jobCompany());
        }
        if (in.jobTitle() != null && containsAnySubstringCaseInsensitive(in.jobTitle(), excl.roles())) {
            exclusionHits.add("Excluded role/title: " + in.jobTitle());
        }
        if (in.jobSource() != null && matchesAnyCaseInsensitive(in.jobSource(), excl.sources())) {
            exclusionHits.add("Excluded source: " + in.jobSource());
        }
        if (in.jobDescription() != null && containsAnySubstringCaseInsensitive(in.jobDescription(), excl.keywords())) {
            exclusionHits.add("Excluded keyword found in job description");
        }
        if (in.jobCountry() != null && matchesAnyCaseInsensitive(in.jobCountry(), excl.countries())) {
            exclusionHits.add("Excluded country: " + in.jobCountry());
        }
        boolean notExcluded = exclusionHits.isEmpty();

        // --- CAPS, PAUSE & SOURCE ENABLED CHECK ---
        List<String> limitIssues = new ArrayList<>();
        if (!"ACTIVE".equalsIgnoreCase(pref.getGlobalState())) {
            limitIssues.add("Global state is " + pref.getGlobalState());
        }
        if (in.enabledSources() != null && !in.enabledSources().isEmpty()
                && in.jobSource() != null && !matchesAnyCaseInsensitive(in.jobSource(), in.enabledSources())) {
            limitIssues.add("Source '" + in.jobSource() + "' is disabled in user preferences");
        }
        if (in.todayCountInUserTz() >= pref.getDailyCap()) {
            limitIssues.add("Daily cap reached (" + in.todayCountInUserTz() + "/" + pref.getDailyCap() + ")");
        }
        if (in.weekCountInUserTz() >= pref.getWeeklyCap()) {
            limitIssues.add("Weekly cap reached (" + in.weekCountInUserTz() + "/" + pref.getWeeklyCap() + ")");
        }
        boolean capsPauseSourceOk = limitIssues.isEmpty();

        boolean readinessAndTruthOk = in.truthfulnessPassed() && in.readinessScore() >= pref.getReadinessThreshold();

        // --- BUILD THE 9 ELIGIBILITY CHECKS ---
        List<CheckItemDto> checks = List.of(
                new CheckItemDto(
                        1,
                        "Listing accessible and open",
                        in.jobAccessibleAndOpen(),
                        in.jobAccessibleAndOpen() ? "PASS" : "ROUTE_TO_REVIEW",
                        in.jobAccessibleAndOpen() ? "Job listing is active and open" : "Job listing is flagged stale or closed"
                ),
                new CheckItemDto(
                        2,
                        "Criteria + Strict Match Rule (Skills & Experience)",
                        skillRulePassed && expRulePassed,
                        (skillRulePassed && expRulePassed) ? "PASS" : "ROUTE_TO_REVIEW",
                        skillRuleOutcome + " | " + expRuleOutcome
                ),
                new CheckItemDto(
                        3,
                        "No duplicate within cooldown",
                        in.notDuplicateWithinCooldown(),
                        in.notDuplicateWithinCooldown() ? "PASS" : "ROUTE_TO_REVIEW",
                        in.notDuplicateWithinCooldown()
                                ? "No duplicate application within " + pref.getDuplicateCooldownDays() + "-day cooldown"
                                : "Duplicate application exists within cooldown window"
                ),
                new CheckItemDto(
                        4,
                        "Approved resume exists",
                        in.hasApprovedResume(),
                        in.hasApprovedResume() ? "PASS" : "ROUTE_TO_REVIEW",
                        in.hasApprovedResume() ? "Default verified resume version is available" : "No verified resume version found"
                ),
                new CheckItemDto(
                        5,
                        "All questions supported and answerable",
                        in.allQuestionsAnswerable(),
                        in.allQuestionsAnswerable() ? "PASS" : "ROUTE_TO_REVIEW",
                        in.allQuestionsAnswerable()
                                ? "All screening questions have approved answers without sensitive/legal pause"
                                : "One or more questions require user pause or lack an approved answer"
                ),
                new CheckItemDto(
                        6,
                        "Not on exclusion lists",
                        notExcluded,
                        notExcluded ? "PASS" : "ROUTE_TO_REVIEW",
                        notExcluded ? "Company, role, source, keywords, and country are not excluded" : String.join("; ", exclusionHits)
                ),
                new CheckItemDto(
                        7,
                        "Provider capability permits method",
                        in.providerPermitsMethod(),
                        in.providerPermitsMethod() ? "PASS" : "ROUTE_TO_REVIEW",
                        in.providerPermitsMethod()
                                ? "Source capability is enabled in registry"
                                : "Source capability is disabled or has no verified API access"
                ),
                new CheckItemDto(
                        8,
                        "Limits, global pause/stop & source enabled",
                        capsPauseSourceOk,
                        capsPauseSourceOk ? "PASS" : "ROUTE_TO_REVIEW",
                        capsPauseSourceOk
                                ? "Global state ACTIVE; source enabled; within daily (" + in.todayCountInUserTz() + "/" + pref.getDailyCap()
                                    + ") and weekly (" + in.weekCountInUserTz() + "/" + pref.getWeeklyCap() + ") caps"
                                : String.join("; ", limitIssues)
                ),
                new CheckItemDto(
                        9,
                        "Readiness threshold & truthfulness check",
                        readinessAndTruthOk,
                        readinessAndTruthOk ? "PASS" : "ROUTE_TO_REVIEW",
                        readinessAndTruthOk
                                ? "Readiness (" + in.readinessScore() + " >= " + pref.getReadinessThreshold() + ") and truthfulness verified"
                                : "Readiness score (" + in.readinessScore() + ") below threshold (" + pref.getReadinessThreshold() + ") or unsupported claim blocked"
                )
        );

        boolean allNinePass = checks.stream().allMatch(CheckItemDto::passed);
        String decision = allNinePass ? "ELIGIBLE" : "ROUTE_TO_REVIEW_QUEUE";

        List<String> failedReasons = checks.stream()
                .filter(c -> !c.passed())
                .map(c -> "[Check #" + c.checkNumber() + " " + c.name() + "] " + c.detail())
                .toList();

        String whySummary;
        if (allNinePass) {
            whySummary = "Eligible under Strict Match Rule: matched " + matchedCount + "/" + totalJobSkills
                    + " required skills (" + coveragePercent + "% coverage, matched: " + String.join(", ", matchedSkills)
                    + (unmatchedSkills.isEmpty() ? "" : "; unmatched: " + String.join(", ", unmatchedSkills))
                    + "), candidate experience " + candYrs + " yrs fits range, and all 9 safety/eligibility checks passed.";
        } else {
            whySummary = "Routed to Review Queue (never auto-submitted) — matched " + matchedCount + "/" + totalJobSkills
                    + " required skills (" + coveragePercent + "% coverage, matched: "
                    + (matchedSkills.isEmpty() ? "none" : String.join(", ", matchedSkills))
                    + (unmatchedSkills.isEmpty() ? "" : "; unmatched: " + String.join(", ", unmatchedSkills))
                    + "). Failed check(s): " + String.join(" || ", failedReasons);
        }

        return new StrictEligibilityResultDto(
                allNinePass,
                decision,
                !allNinePass,
                matchedSkills,
                unmatchedSkills,
                matchedCount,
                totalJobSkills,
                coveragePercent,
                minSkills,
                candYrs,
                minYrs,
                maxYrs,
                skillRuleOutcome,
                expRuleOutcome,
                checks,
                whySummary
        );
    }

    public ExclusionListsDto parseExclusions(String json) {
        if (json == null || json.isBlank()) return ExclusionListsDto.empty();
        try {
            return objectMapper.readValue(json, ExclusionListsDto.class);
        } catch (Exception e) {
            return ExclusionListsDto.empty();
        }
    }

    public List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean matchesAnyCaseInsensitive(String value, List<String> candidates) {
        if (value == null || candidates == null) return false;
        String trimmed = value.trim();
        return candidates.stream().anyMatch(c -> c != null && c.trim().equalsIgnoreCase(trimmed));
    }

    private boolean containsAnySubstringCaseInsensitive(String text, List<String> substrings) {
        if (text == null || substrings == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return substrings.stream()
                .filter(s -> s != null && !s.isBlank())
                .anyMatch(s -> lower.contains(s.trim().toLowerCase(Locale.ROOT)));
    }
}

