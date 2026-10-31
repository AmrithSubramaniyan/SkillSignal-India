package in.skillsignal.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.company.Company;
import in.skillsignal.company.CompanyRepository;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.StrictEligibilityService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.reminder.UserNotificationPreference;
import in.skillsignal.reminder.UserNotificationPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AutoApplyRuleAndDryRunService {

    public static final int MAX_RULES_PER_USER = 5;
    public static final int MAX_CONDITIONS_PER_RULE = 3;

    public record RuleConditionDto(
            String field,    // WORK_MODE, LOCATION, TITLE_KEYWORD, MIN_MATCH_SCORE, SOURCE, COMPANY
            String operator, // EQUALS, CONTAINS, GTE
            String value
    ) {}

    public record AutoApplyRuleDto(
            UUID id,
            UUID userId,
            String ruleName,
            int priorityOrder,
            boolean enabled,
            List<RuleConditionDto> conditions,
            String tierT1Policy,
            String tierT2Policy,
            String tierT3Policy,
            String timeWindowStart,
            String timeWindowEnd,
            int companyCooldownDays,
            boolean overrideCompanyCooldownIfApproved,
            int minSpreadMinutes,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record JobDryRunItemDto(
            UUID jobId,
            String jobTitle,
            String company,
            String companyTier,
            boolean matchedRuleConditions,
            String matchedRuleName,
            boolean strictEligibilityPassed,
            boolean withinTimeWindow,
            boolean companyCooldownPassed,
            String tierPolicyOutcome,
            boolean wouldMatch,
            String routingOutcome, // WOULD_MATCH_ELIGIBLE, ROUTE_TO_REVIEW_T1, ROUTE_TO_REVIEW, SKIPPED_NOT_MATCHED
            int scheduledSpreadOffsetMinutes,
            String explanation
    ) {}

    public record AutoApplyRunDto(
            UUID id,
            UUID userId,
            UUID ruleId,
            boolean dryRun,
            int totalJobsEvaluated,
            int matchedJobsCount,
            int submissionsCreatedCount,
            String summaryText,
            List<JobDryRunItemDto> items,
            Instant createdAt
    ) {}

    private final AutoApplyRuleRepository ruleRepository;
    private final AutoApplyRunRepository runRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final UserNotificationPreferenceRepository notificationPreferenceRepository;
    private final StrictEligibilityService eligibilityService;
    private final ObjectMapper objectMapper;

    public AutoApplyRuleAndDryRunService(
            AutoApplyRuleRepository ruleRepository,
            AutoApplyRunRepository runRepository,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            CompanyRepository companyRepository,
            ApplicationRepository applicationRepository,
            UserNotificationPreferenceRepository notificationPreferenceRepository,
            StrictEligibilityService eligibilityService,
            ObjectMapper objectMapper
    ) {
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.eligibilityService = eligibilityService;
        this.objectMapper = objectMapper;
    }

    public List<AutoApplyRuleDto> listRules(UUID userId) {
        return ruleRepository.findByUserIdOrderByPriorityOrderAscCreatedAtAsc(userId).stream()
                .map(this::toRuleDto)
                .toList();
    }

    @Transactional
    public AutoApplyRuleDto createRule(
            UUID userId,
            String ruleName,
            Integer priorityOrder,
            Boolean enabled,
            List<RuleConditionDto> conditions,
            String tierT1Policy,
            String tierT2Policy,
            String tierT3Policy,
            String timeWindowStart,
            String timeWindowEnd,
            Integer companyCooldownDays,
            Boolean overrideCompanyCooldownIfApproved,
            Integer minSpreadMinutes
    ) {
        long existingCount = ruleRepository.countByUserId(userId);
        if (existingCount >= MAX_RULES_PER_USER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_RULES_PER_USER + " auto-apply rules allowed per user"
            );
        }
        validateRuleInputs(ruleName, conditions, tierT1Policy, timeWindowStart, timeWindowEnd);

        AutoApplyRule rule = new AutoApplyRule();
        rule.setUserId(userId);
        rule.setRuleName(ruleName.trim());
        rule.setPriorityOrder(priorityOrder != null ? priorityOrder : (int) (existingCount + 1));
        rule.setEnabled(enabled == null || enabled);
        rule.setConditionsJson(writeJson(conditions != null ? conditions : List.of()));
        rule.setTierT1Policy("REVIEW"); // T1 is strictly REVIEW
        rule.setTierT2Policy(normalizeTierPolicy(tierT2Policy, "ELIGIBLE"));
        rule.setTierT3Policy(normalizeTierPolicy(tierT3Policy, "ELIGIBLE"));
        rule.setTimeWindowStart(timeWindowStart != null && !timeWindowStart.isBlank() ? timeWindowStart.trim() : "09:00");
        rule.setTimeWindowEnd(timeWindowEnd != null && !timeWindowEnd.isBlank() ? timeWindowEnd.trim() : "19:00");
        rule.setCompanyCooldownDays(companyCooldownDays != null && companyCooldownDays >= 0 ? companyCooldownDays : 30);
        rule.setOverrideCompanyCooldownIfApproved(Boolean.TRUE.equals(overrideCompanyCooldownIfApproved));
        rule.setMinSpreadMinutes(minSpreadMinutes != null && minSpreadMinutes >= 1 ? minSpreadMinutes : 15);

        rule = ruleRepository.save(rule);
        return toRuleDto(rule);
    }

    @Transactional
    public AutoApplyRuleDto updateRule(
            UUID userId,
            UUID ruleId,
            String ruleName,
            Integer priorityOrder,
            Boolean enabled,
            List<RuleConditionDto> conditions,
            String tierT1Policy,
            String tierT2Policy,
            String tierT3Policy,
            String timeWindowStart,
            String timeWindowEnd,
            Integer companyCooldownDays,
            Boolean overrideCompanyCooldownIfApproved,
            Integer minSpreadMinutes
    ) {
        AutoApplyRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));

        if (conditions != null && conditions.size() > MAX_CONDITIONS_PER_RULE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_CONDITIONS_PER_RULE + " conditions allowed per rule"
            );
        }
        if (tierT1Policy != null && !tierT1Policy.isBlank() && !"REVIEW".equalsIgnoreCase(tierT1Policy.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Per-tier policy for T1 companies must always be REVIEW"
            );
        }
        if (ruleName != null && !ruleName.isBlank()) {
            rule.setRuleName(ruleName.trim());
        }
        if (priorityOrder != null) {
            rule.setPriorityOrder(priorityOrder);
        }
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        if (conditions != null) {
            rule.setConditionsJson(writeJson(conditions));
        }
        rule.setTierT1Policy("REVIEW");
        if (tierT2Policy != null) {
            rule.setTierT2Policy(normalizeTierPolicy(tierT2Policy, rule.getTierT2Policy()));
        }
        if (tierT3Policy != null) {
            rule.setTierT3Policy(normalizeTierPolicy(tierT3Policy, rule.getTierT3Policy()));
        }
        if (timeWindowStart != null && !timeWindowStart.isBlank()) {
            parseTime(timeWindowStart.trim());
            rule.setTimeWindowStart(timeWindowStart.trim());
        }
        if (timeWindowEnd != null && !timeWindowEnd.isBlank()) {
            parseTime(timeWindowEnd.trim());
            rule.setTimeWindowEnd(timeWindowEnd.trim());
        }
        if (companyCooldownDays != null && companyCooldownDays >= 0) {
            rule.setCompanyCooldownDays(companyCooldownDays);
        }
        if (overrideCompanyCooldownIfApproved != null) {
            rule.setOverrideCompanyCooldownIfApproved(overrideCompanyCooldownIfApproved);
        }
        if (minSpreadMinutes != null && minSpreadMinutes >= 1) {
            rule.setMinSpreadMinutes(minSpreadMinutes);
        }

        rule = ruleRepository.save(rule);
        return toRuleDto(rule);
    }

    @Transactional
    public void deleteRule(UUID userId, UUID ruleId) {
        AutoApplyRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        ruleRepository.delete(rule);
    }

    @Transactional
    public AutoApplyRunDto simulateSingleRule(UUID userId, UUID ruleId, Instant evaluationTime) {
        AutoApplyRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        return runDryEvaluationInternal(userId, List.of(rule), rule.getId(), true, evaluationTime);
    }

    /**
     * Executes an auto-apply run across the user's ordered rules.
     * In dry_run=true mode (and whenever Mode 3 is not explicitly active), creates ZERO submissions
     * and stores a preview summary "Would have matched N of M jobs".
     */
    @Transactional
    public AutoApplyRunDto executeRun(UUID userId, boolean dryRun, Instant evaluationTime) {
        List<AutoApplyRule> activeRules = ruleRepository.findByUserIdOrderByPriorityOrderAscCreatedAtAsc(userId).stream()
                .filter(AutoApplyRule::isEnabled)
                .toList();
        return runDryEvaluationInternal(userId, activeRules, null, true, evaluationTime);
    }

    public AutoApplyRunDto getRun(UUID userId, UUID runId) {
        AutoApplyRun run = runRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auto-apply run not found"));
        return toRunDto(run);
    }

    public List<AutoApplyRunDto> listRuns(UUID userId) {
        return runRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toRunDto)
                .toList();
    }

    private AutoApplyRunDto runDryEvaluationInternal(
            UUID userId,
            List<AutoApplyRule> rulesToEvaluate,
            UUID specificRuleId,
            boolean dryRun,
            Instant evaluationTime
    ) {
        Instant now = evaluationTime != null ? evaluationTime : Instant.now();
        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, Job> jobsById = new HashMap<>();
        userJobs.forEach(j -> jobsById.put(j.getId(), j));

        Map<String, String> companyTiers = new HashMap<>();
        for (Company c : companyRepository.findByUserIdOrderByPriorityDescNameAsc(userId)) {
            if (c.getName() != null) {
                companyTiers.put(c.getName().trim().toLowerCase(Locale.ROOT), c.getTier() != null ? c.getTier() : "T2");
            }
        }

        List<Application> userApps = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        AutoApplyPreference pref = eligibilityService.getOrCreatePreferences(userId);
        String userTz = notificationPreferenceRepository.findById(userId)
                .map(UserNotificationPreference::getTimeZone)
                .orElse("Asia/Kolkata");
        ZoneId zoneId = ZoneId.of(userTz);

        List<JobDryRunItemDto> items = new ArrayList<>();
        int matchedCount = 0;
        int spreadSlotIndex = 0;

        for (Job job : userJobs) {
            Integer matchScore = jobMatchRepository
                    .findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), userId)
                    .map(JobMatch::getTotalScore)
                    .orElse(80);

            AutoApplyRule matchedRule = null;
            for (AutoApplyRule r : rulesToEvaluate) {
                if (matchesAllConditions(job, matchScore, parseConditions(r.getConditionsJson()))) {
                    matchedRule = r;
                    break;
                }
            }

            if (matchedRule == null) {
                items.add(new JobDryRunItemDto(
                        job.getId(),
                        job.getTitle(),
                        job.getCompany(),
                        resolveCompanyTier(job.getCompany(), companyTiers),
                        false,
                        null,
                        false,
                        false,
                        false,
                        "NO_RULE_MATCHED",
                        false,
                        "SKIPPED_NOT_MATCHED",
                        0,
                        "Did not match any active rule conditions"
                ));
                continue;
            }

            String tier = resolveCompanyTier(job.getCompany(), companyTiers);
            String tierPolicy = switch (tier) {
                case "T1" -> "REVIEW"; // T1 is always REVIEW
                case "T3" -> matchedRule.getTierT3Policy();
                default -> matchedRule.getTierT2Policy();
            };

            boolean withinTimeWindow = isWithinTimeWindow(
                    matchedRule.getTimeWindowStart(),
                    matchedRule.getTimeWindowEnd(),
                    now,
                    zoneId
            );

            // Check 30-day per-company cooldown (one application per company per N days unless approved)
            Instant companyCutoff = now.minus(matchedRule.getCompanyCooldownDays(), ChronoUnit.DAYS);
            boolean hasRecentCompanyApp = userApps.stream().anyMatch(a -> {
                Job appJob = jobsById.get(a.getJobId());
                if (appJob == null || appJob.getCompany() == null || job.getCompany() == null) return false;
                return appJob.getCompany().trim().equalsIgnoreCase(job.getCompany().trim())
                        && a.getCreatedAt() != null
                        && !a.getCreatedAt().isBefore(companyCutoff);
            });
            boolean companyCooldownPassed = !hasRecentCompanyApp || matchedRule.isOverrideCompanyCooldownIfApproved();

            StrictEligibilityService.StrictEligibilityResultDto elig = eligibilityService.evaluateForUserAndJob(
                    userId, job.getId(), null, now
            );

            // A job "matches" the rule & criteria when rule conditions pass, strict skill+experience criteria pass,
            // company cooldown passes, and within remaining daily/weekly caps
            boolean criteriaCheckPassed = elig.nineChecks().stream()
                    .filter(c -> c.checkNumber() == 2)
                    .findFirst()
                    .map(StrictEligibilityService.CheckItemDto::passed)
                    .orElse(false);

            boolean wouldMatch = criteriaCheckPassed && companyCooldownPassed && !job.isStale()
                    && matchedCount < pref.getDailyCap();

            String routingOutcome;
            String explanation;
            int spreadMinutes = 0;

            if (!wouldMatch) {
                routingOutcome = "ROUTE_TO_REVIEW";
                List<String> reasons = new ArrayList<>();
                if (!criteriaCheckPassed) reasons.add("Strict skill/experience rule failed");
                if (!companyCooldownPassed) {
                    reasons.add("Company cooldown active (1 application per " + matchedRule.getCompanyCooldownDays() + " days for " + job.getCompany() + ")");
                }
                if (job.isStale()) reasons.add("Job is stale/closed");
                if (matchedCount >= pref.getDailyCap()) reasons.add("Daily cap limit reached");
                explanation = "Routed to Review / Skipped: " + String.join("; ", reasons);
            } else {
                matchedCount++;
                spreadMinutes = spreadSlotIndex * matchedRule.getMinSpreadMinutes();
                spreadSlotIndex++;

                if ("T1".equals(tier) || "REVIEW".equalsIgnoreCase(tierPolicy)) {
                    routingOutcome = "ROUTE_TO_REVIEW_T1";
                    explanation = "Matched rule '" + matchedRule.getRuleName() + "'; company tier is " + tier
                            + " (policy=REVIEW) -> routes to Review Queue (spread +" + spreadMinutes + "m).";
                } else if (!withinTimeWindow) {
                    routingOutcome = "QUEUED_FOR_TIME_WINDOW";
                    explanation = "Matched rule '" + matchedRule.getRuleName() + "'; queued for time window "
                            + matchedRule.getTimeWindowStart() + "-" + matchedRule.getTimeWindowEnd()
                            + " (spread +" + spreadMinutes + "m).";
                } else {
                    routingOutcome = "WOULD_MATCH_ELIGIBLE";
                    explanation = "Matched rule '" + matchedRule.getRuleName() + "' and passed company cooldown, tier ("
                            + tier + "), and strict criteria (spread +" + spreadMinutes + "m).";
                }
            }

            items.add(new JobDryRunItemDto(
                    job.getId(),
                    job.getTitle(),
                    job.getCompany(),
                    tier,
                    true,
                    matchedRule.getRuleName(),
                    elig.eligible(),
                    withinTimeWindow,
                    companyCooldownPassed,
                    tier + "=" + tierPolicy,
                    wouldMatch,
                    routingOutcome,
                    spreadMinutes,
                    explanation
            ));
        }

        String summary = "Would have matched " + matchedCount + " of " + userJobs.size() + " jobs";

        AutoApplyRun run = new AutoApplyRun();
        run.setUserId(userId);
        run.setRuleId(specificRuleId);
        run.setDryRun(true);
        run.setTotalJobsEvaluated(userJobs.size());
        run.setMatchedJobsCount(matchedCount);
        run.setSubmissionsCreatedCount(0); // Strictly ZERO submissions created on dry run!
        run.setSummaryText(summary);
        run.setResultsJson(writeJson(items));
        run = runRepository.save(run);

        return toRunDto(run);
    }

    private boolean matchesAllConditions(Job job, int matchScore, List<RuleConditionDto> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (RuleConditionDto cond : conditions) {
            if (cond == null || cond.field() == null || cond.value() == null) continue;
            String field = cond.field().trim().toUpperCase(Locale.ROOT);
            String op = cond.operator() != null ? cond.operator().trim().toUpperCase(Locale.ROOT) : "CONTAINS";
            String val = cond.value().trim();

            boolean matched = switch (field) {
                case "WORK_MODE" -> matchString(job.getWorkMode(), op, val);
                case "LOCATION" -> matchString(job.getLocation(), op, val);
                case "TITLE_KEYWORD", "TITLE" -> matchString(job.getTitle(), op, val);
                case "SOURCE" -> matchString(job.getSource(), op, val);
                case "COMPANY" -> matchString(job.getCompany(), op, val);
                case "MIN_MATCH_SCORE", "MATCH_SCORE" -> {
                    try {
                        int minScore = Integer.parseInt(val);
                        yield matchScore >= minScore;
                    } catch (NumberFormatException e) {
                        yield false;
                    }
                }
                default -> true;
            };
            if (!matched) return false;
        }
        return true;
    }

    private boolean matchString(String actual, String operator, String expected) {
        if (actual == null) return false;
        if ("EQUALS".equals(operator)) {
            return actual.trim().equalsIgnoreCase(expected);
        }
        return actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private String resolveCompanyTier(String companyName, Map<String, String> companyTiers) {
        if (companyName == null) return "T2";
        return companyTiers.getOrDefault(companyName.trim().toLowerCase(Locale.ROOT), "T2");
    }

    private boolean isWithinTimeWindow(String startStr, String endStr, Instant instant, ZoneId zone) {
        LocalTime local = ZonedDateTime.ofInstant(instant, zone).toLocalTime();
        LocalTime start = parseTime(startStr);
        LocalTime end = parseTime(endStr);
        if (start.equals(end)) return true;
        if (start.isBefore(end)) {
            return !local.isBefore(start) && !local.isAfter(end);
        } else {
            return !local.isBefore(start) || !local.isAfter(end);
        }
    }

    private void validateRuleInputs(
            String ruleName,
            List<RuleConditionDto> conditions,
            String tierT1Policy,
            String timeWindowStart,
            String timeWindowEnd
    ) {
        if (ruleName == null || ruleName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ruleName is required");
        }
        if (conditions != null && conditions.size() > MAX_CONDITIONS_PER_RULE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_CONDITIONS_PER_RULE + " conditions allowed per rule"
            );
        }
        if (tierT1Policy != null && !tierT1Policy.isBlank() && !"REVIEW".equalsIgnoreCase(tierT1Policy.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Per-tier policy for T1 companies must always be REVIEW"
            );
        }
        if (timeWindowStart != null && !timeWindowStart.isBlank()) {
            parseTime(timeWindowStart.trim());
        }
        if (timeWindowEnd != null && !timeWindowEnd.isBlank()) {
            parseTime(timeWindowEnd.trim());
        }
    }

    private String normalizeTierPolicy(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("REVIEW", "ELIGIBLE", "SKIP").contains(norm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tier policy: " + raw);
        }
        return norm;
    }

    private LocalTime parseTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time window format (expected HH:mm): " + hhmm);
        }
    }

    private AutoApplyRuleDto toRuleDto(AutoApplyRule r) {
        return new AutoApplyRuleDto(
                r.getId(),
                r.getUserId(),
                r.getRuleName(),
                r.getPriorityOrder(),
                r.isEnabled(),
                parseConditions(r.getConditionsJson()),
                r.getTierT1Policy(),
                r.getTierT2Policy(),
                r.getTierT3Policy(),
                r.getTimeWindowStart(),
                r.getTimeWindowEnd(),
                r.getCompanyCooldownDays(),
                r.isOverrideCompanyCooldownIfApproved(),
                r.getMinSpreadMinutes(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private AutoApplyRunDto toRunDto(AutoApplyRun run) {
        List<JobDryRunItemDto> items = List.of();
        try {
            items = objectMapper.readValue(run.getResultsJson(), new TypeReference<List<JobDryRunItemDto>>() {});
        } catch (Exception ignored) {
        }
        return new AutoApplyRunDto(
                run.getId(),
                run.getUserId(),
                run.getRuleId(),
                run.isDryRun(),
                run.getTotalJobsEvaluated(),
                run.getMatchedJobsCount(),
                run.getSubmissionsCreatedCount(),
                run.getSummaryText(),
                items,
                run.getCreatedAt()
        );
    }

    private List<RuleConditionDto> parseConditions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<RuleConditionDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
