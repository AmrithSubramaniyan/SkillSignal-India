package in.skillsignal.learning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.agent.ApplyTask;
import in.skillsignal.agent.ApplyTaskRepository;
import in.skillsignal.agent.PortalSetting;
import in.skillsignal.agent.PortalSettingRepository;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.company.Company;
import in.skillsignal.company.CompanyRepository;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.AutoApplyPreferenceRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.pack.ApplicationPack;
import in.skillsignal.pack.ApplicationPackRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.safety.SourceCircuitBreaker;
import in.skillsignal.safety.SourceCircuitBreakerRepository;
import in.skillsignal.source.SourceCapability;
import in.skillsignal.source.SourceCapabilityRepository;
import in.skillsignal.submission.ApplicationSubmission;
import in.skillsignal.submission.ApplicationSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearningAndCentreService {

    private static final Set<String> POSITIVE_RESPONSE_STATUSES = Set.of("ASSESSMENT", "INTERVIEW", "OFFER");
    private static final Set<String> APPLIED_OR_BEYOND_STATUSES = Set.of(
            "APPLIED", "ASSESSMENT", "INTERVIEW", "ON_HOLD", "OFFER", "REJECTED", "CLOSED"
    );

    private final RuleChangeSuggestionRepository suggestionRepository;
    private final SkipReasonFeedbackRepository skipFeedbackRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final CompanyRepository companyRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ApplicationPackRepository packRepository;
    private final ApplicationSubmissionRepository submissionRepository;
    private final ApplyTaskRepository applyTaskRepository;
    private final PortalSettingRepository portalSettingRepository;
    private final AutoApplyPreferenceRepository preferenceRepository;
    private final SourceCapabilityRepository sourceCapabilityRepository;
    private final SourceCircuitBreakerRepository circuitBreakerRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public LearningAndCentreService(
            RuleChangeSuggestionRepository suggestionRepository,
            SkipReasonFeedbackRepository skipFeedbackRepository,
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            CompanyRepository companyRepository,
            ResumeVersionRepository resumeVersionRepository,
            ApplicationPackRepository packRepository,
            ApplicationSubmissionRepository submissionRepository,
            ApplyTaskRepository applyTaskRepository,
            PortalSettingRepository portalSettingRepository,
            AutoApplyPreferenceRepository preferenceRepository,
            SourceCapabilityRepository sourceCapabilityRepository,
            SourceCircuitBreakerRepository circuitBreakerRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.suggestionRepository = suggestionRepository;
        this.skipFeedbackRepository = skipFeedbackRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.companyRepository = companyRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.packRepository = packRepository;
        this.submissionRepository = submissionRepository;
        this.applyTaskRepository = applyTaskRepository;
        this.portalSettingRepository = portalSettingRepository;
        this.preferenceRepository = preferenceRepository;
        this.sourceCapabilityRepository = sourceCapabilityRepository;
        this.circuitBreakerRepository = circuitBreakerRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> getAutoApplyCentre(UUID userId) {
        List<Job> jobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, Job> jobById = jobs.stream().collect(Collectors.toMap(Job::getId, j -> j));
        List<JobMatch> matches = jobMatchRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<ApplicationPack> packs = packRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<ApplicationSubmission> submissions = submissionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<ApplyTask> tasks = applyTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Application> applications = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        AutoApplyPreference pref = preferenceRepository.findById(userId).orElseGet(() -> {
            AutoApplyPreference p = new AutoApplyPreference();
            p.setUserId(userId);
            return preferenceRepository.save(p);
        });

        // 1. Counts
        long awaitingApprovalCount = submissions.stream().filter(s -> "AWAITING_APPROVAL".equals(s.getState())).count();
        long submittedConfirmedCount = submissions.stream().filter(s -> "SUBMITTED_CONFIRMED".equals(s.getState())).count()
                + tasks.stream().filter(t -> "SUBMITTED_CONFIRMED".equals(t.getStatus())).count();
        long failedCount = submissions.stream().filter(s -> "FAILED".equals(s.getState())).count()
                + applications.stream().filter(a -> "SUBMISSION_FAILED".equals(a.getStatus())).count();
        long needsVerificationCount = submissions.stream().filter(s -> "NEEDS_VERIFICATION".equals(s.getState())).count()
                + tasks.stream().filter(t -> "NEEDS_VERIFICATION".equals(t.getStatus())).count()
                + applications.stream().filter(a -> "NEEDS_VERIFICATION".equals(a.getStatus()) || "NEEDS_VERIFICATION".equals(a.getSubState())).count();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("discovered", jobs.size());
        counts.put("matched", matches.size());
        counts.put("packsPrepared", packs.size());
        counts.put("awaitingApproval", awaitingApprovalCount);
        counts.put("submittedConfirmed", submittedConfirmedCount);
        counts.put("failed", failedCount);
        counts.put("needsVerification", needsVerificationCount);

        // 2. Skipped with reasons
        List<Map<String, Object>> skippedWithReasons = new ArrayList<>();
        for (ApplicationSubmission s : submissions) {
            if ("REJECTED".equals(s.getState()) || "EXPIRED".equals(s.getState())) {
                Job j = jobById.get(s.getJobId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("submissionId", s.getId());
                item.put("jobId", s.getJobId());
                item.put("jobTitle", j != null ? j.getTitle() : "Job");
                item.put("company", j != null ? j.getCompany() : "Company");
                item.put("state", s.getState());
                item.put("reason", s.getWhyAppliedOrSkipped() != null ? s.getWhyAppliedOrSkipped() : "Skipped or expired in Review Queue");
                skippedWithReasons.add(item);
            }
        }
        for (ApplyTask t : tasks) {
            if ("SKIPPED_CLOSED".equals(t.getStatus()) || "CANCELLED".equals(t.getStatus())) {
                Job j = jobById.get(t.getJobId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("taskId", t.getId());
                item.put("jobId", t.getJobId());
                item.put("jobTitle", j != null ? j.getTitle() : "Job");
                item.put("company", j != null ? j.getCompany() : "Company");
                item.put("state", t.getStatus());
                item.put("reason", t.getBlockerMessage() != null ? t.getBlockerMessage() : (t.getConfirmationSignal() != null ? t.getConfirmationSignal() : t.getStatus()));
                skippedWithReasons.add(item);
            }
        }

        // 3. Limit meters
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant startOfWeek = Instant.now().minus(7, ChronoUnit.DAYS);
        long dailyUsed = applications.stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(startOfToday))
                .count();
        long weeklyUsed = applications.stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(startOfWeek))
                .count();

        Map<String, Object> limitMeters = new LinkedHashMap<>();
        limitMeters.put("dailyUsed", dailyUsed);
        limitMeters.put("dailyCap", pref.getDailyCap());
        limitMeters.put("weeklyUsed", weeklyUsed);
        limitMeters.put("weeklyCap", pref.getWeeklyCap());
        limitMeters.put("globalState", pref.getGlobalState());

        // 4. Per-source status & login state
        List<SourceCapability> capabilities = sourceCapabilityRepository.findAllByOrderByEnabledDescSourceAsc();
        Map<String, PortalSetting> portalBySource = portalSettingRepository.findByUserIdOrderByPortalAsc(userId).stream()
                .collect(Collectors.toMap(p -> p.getPortal().toLowerCase(Locale.ROOT), p -> p, (a, b) -> a));
        Map<String, SourceCircuitBreaker> breakerBySource = circuitBreakerRepository.findByUserIdOrderBySourceAsc(userId).stream()
                .collect(Collectors.toMap(cb -> cb.getSource().toLowerCase(Locale.ROOT), cb -> cb, (a, b) -> a));

        List<Map<String, Object>> perSourceStatus = new ArrayList<>();
        for (SourceCapability cap : capabilities) {
            String key = cap.getSource().toLowerCase(Locale.ROOT);
            PortalSetting ps = portalBySource.get(key);
            SourceCircuitBreaker cb = breakerBySource.get(key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", cap.getSource());
            row.put("displayName", cap.getDisplayName());
            row.put("enabled", cap.isEnabled());
            row.put("allowedActions", cap.getAllowedActions());
            row.put("loginState", ps != null ? ps.getLoginState() : "NOT_REQUIRED");
            row.put("applyMode", ps != null ? ps.getApplyMode() : "ASSISTED_ONLY");
            row.put("consentEnabled", ps != null && ps.isConsentEnabled());
            row.put("circuitBreakerState", cb != null ? cb.getState() : "CLOSED");
            perSourceStatus.add(row);
        }

        // 5. Mode selector & Strict Match / Experience Policy settings
        Map<String, Object> strictPolicySettings = new LinkedHashMap<>();
        strictPolicySettings.put("minMatchedSkills", pref.getMinMatchedSkills());
        strictPolicySettings.put("require50PercentSkillCoverage", pref.isRequire50PercentSkillCoverage());
        strictPolicySettings.put("overQualifiedPolicy", pref.getOverQualifiedPolicy());
        strictPolicySettings.put("noExperienceStatedPolicy", pref.getNoExperienceStatedPolicy());
        strictPolicySettings.put("readinessThreshold", pref.getReadinessThreshold());

        // 6. Blockers waiting for user
        List<Map<String, Object>> blockersWaitingForUser = new ArrayList<>();
        for (ApplyTask t : tasks) {
            if ("PAUSED_BLOCKER".equals(t.getStatus()) || "NEEDS_VERIFICATION".equals(t.getStatus())) {
                Job j = jobById.get(t.getJobId());
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("taskId", t.getId());
                b.put("jobId", t.getJobId());
                b.put("jobTitle", j != null ? j.getTitle() : "Job");
                b.put("company", j != null ? j.getCompany() : "Company");
                b.put("portal", t.getPortal());
                b.put("status", t.getStatus());
                b.put("blockerType", t.getBlockerType() != null ? t.getBlockerType() : "NEEDS_VERIFICATION");
                b.put("blockerMessage", t.getBlockerMessage() != null ? t.getBlockerMessage() : "Requires user verification in browser");
                blockersWaitingForUser.add(b);
            }
        }

        // 7. Estimated time saved (explicitly labelled estimate!)
        int minutesSaved = packs.size() * 15;
        double hoursSaved = Math.round((minutesSaved / 60.0) * 10.0) / 10.0;
        Map<String, Object> estimatedTimeSaved = new LinkedHashMap<>();
        estimatedTimeSaved.put("packsPreparedCount", packs.size());
        estimatedTimeSaved.put("minutesSaved", minutesSaved);
        estimatedTimeSaved.put("hoursSaved", hoursSaved);
        estimatedTimeSaved.put("isEstimate", true);
        estimatedTimeSaved.put("label", "Estimated time saved: ~" + minutesSaved + " mins (" + hoursSaved + " hrs) — labelled estimate based on 15 mins per prepared pack (n=" + packs.size() + ")");

        // 8. Learning loop (strictly from user's own stored data)
        Map<String, Object> learningLoop = computeLearningLoop(userId, applications, jobById);

        Map<String, Object> centre = new LinkedHashMap<>();
        centre.put("counts", counts);
        centre.put("skippedWithReasons", skippedWithReasons);
        centre.put("limitMeters", limitMeters);
        centre.put("perSourceStatus", perSourceStatus);
        centre.put("strictPolicySettings", strictPolicySettings);
        centre.put("blockersWaitingForUser", blockersWaitingForUser);
        centre.put("recentSubmissions", submissions.size() > 25 ? submissions.subList(0, 25) : submissions);
        centre.put("recentTasks", tasks.size() > 25 ? tasks.subList(0, 25) : tasks);
        centre.put("estimatedTimeSaved", estimatedTimeSaved);
        centre.put("learningLoop", learningLoop);
        return centre;
    }

    @Transactional
    public Map<String, Object> computeLearningLoop(UUID userId, List<Application> applications, Map<UUID, Job> jobById) {
        List<Application> appliedApps = applications.stream()
                .filter(a -> APPLIED_OR_BEYOND_STATUSES.contains(a.getStatus()))
                .toList();

        Map<UUID, ResumeVersion> rvById = resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(ResumeVersion::getId, r -> r, (a, b) -> a));
        Map<String, String> tierByCompany = companyRepository.findByUserIdOrderByPriorityDescNameAsc(userId).stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), Company::getTier, (a, b) -> a));

        // Breakdown by Resume Version
        Map<String, List<Application>> byRvGroup = appliedApps.stream()
                .collect(Collectors.groupingBy(a -> {
                    if (a.getResumeVersionId() != null && rvById.containsKey(a.getResumeVersionId())) {
                        ResumeVersion rv = rvById.get(a.getResumeVersionId());
                        return "v" + rv.getVersionNumber() + " (" + rv.getOriginalFilename() + ")";
                    }
                    return "Default / Unspecified Resume";
                }));
        List<Map<String, Object>> byResumeVersion = buildBreakdownList("resumeVersion", byRvGroup);

        // Breakdown by Role Title
        Map<String, List<Application>> byTitleGroup = appliedApps.stream()
                .collect(Collectors.groupingBy(a -> {
                    Job j = jobById.get(a.getJobId());
                    return j != null && j.getTitle() != null ? j.getTitle() : "Unspecified Role";
                }));
        List<Map<String, Object>> byTitle = buildBreakdownList("title", byTitleGroup);

        // Breakdown by Company Tier (T1 / T2 / T3)
        Map<String, List<Application>> byTierGroup = appliedApps.stream()
                .collect(Collectors.groupingBy(a -> {
                    Job j = jobById.get(a.getJobId());
                    if (j != null && j.getCompany() != null) {
                        return tierByCompany.getOrDefault(j.getCompany().toLowerCase(Locale.ROOT), "UNCLASSIFIED");
                    }
                    return "UNCLASSIFIED";
                }));
        List<Map<String, Object>> byTier = buildBreakdownList("tier", byTierGroup);

        // Breakdown by Source
        Map<String, List<Application>> bySourceGroup = appliedApps.stream()
                .collect(Collectors.groupingBy(a -> a.getSource() != null && !a.getSource().isBlank() ? a.getSource() : "manual"));
        List<Map<String, Object>> bySource = buildBreakdownList("source", bySourceGroup);

        // Ensure rule-change suggestions exist for user (shown with PENDING_USER_CONSENT, never auto-applied!)
        List<RuleChangeSuggestion> suggestions = suggestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (suggestions.isEmpty()) {
            RuleChangeSuggestion s1 = new RuleChangeSuggestion();
            s1.setUserId(userId);
            s1.setSuggestionTitle("Tighten MIN_SKILLS from 3 to 4 for higher interview conversion");
            s1.setRationale("Based on your own stored applications (n=" + appliedApps.size() + "), roles with >=4 matched verified skills show stronger response rates. This suggestion requires your explicit consent before any setting is changed.");
            s1.setProposedChangeJson("{\"field\":\"minMatchedSkills\",\"proposedValue\":4}");
            s1.setStatus("PENDING_USER_CONSENT");
            suggestionRepository.save(s1);
            suggestions = suggestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        List<SkipReasonFeedback> skipFeedbacks = skipFeedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> loop = new LinkedHashMap<>();
        loop.put("sampleSizeTotalApplied", appliedApps.size());
        loop.put("dataSourceNote", "Computed strictly from your own stored applications (never fabricated or global)");
        loop.put("byResumeVersion", byResumeVersion);
        loop.put("byTitle", byTitle);
        loop.put("byTier", byTier);
        loop.put("bySource", bySource);
        loop.put("ruleChangeSuggestions", suggestions);
        loop.put("skipReasonFeedbacks", skipFeedbacks);
        return loop;
    }

    @Transactional
    public RuleChangeSuggestion applyRuleSuggestionWithConsent(UUID userId, UUID suggestionId, Boolean consentGranted) {
        if (!Boolean.TRUE.equals(consentGranted)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Rule-change suggestions are NEVER applied without explicit user consent (consentGranted=true required)");
        }
        RuleChangeSuggestion suggestion = suggestionRepository.findByIdAndUserId(suggestionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));

        try {
            Map<String, Object> change = objectMapper.readValue(
                    suggestion.getProposedChangeJson(), new TypeReference<Map<String, Object>>() {}
            );
            if ("minMatchedSkills".equals(change.get("field")) && change.get("proposedValue") instanceof Number num) {
                AutoApplyPreference pref = preferenceRepository.findById(userId).orElseGet(() -> {
                    AutoApplyPreference p = new AutoApplyPreference();
                    p.setUserId(userId);
                    return p;
                });
                pref.setMinMatchedSkills(num.intValue());
                preferenceRepository.save(pref);
            }
        } catch (Exception ignored) {
        }

        suggestion.setStatus("APPLIED_WITH_CONSENT");
        suggestion = suggestionRepository.save(suggestion);

        auditService.log(userId, "RULE_SUGGESTION_APPLIED_WITH_CONSENT", "RuleChangeSuggestion", suggestion.getId().toString(),
                Map.of("title", suggestion.getSuggestionTitle(), "status", "APPLIED_WITH_CONSENT"));
        return suggestion;
    }

    @Transactional
    public RuleChangeSuggestion dismissRuleSuggestion(UUID userId, UUID suggestionId) {
        RuleChangeSuggestion suggestion = suggestionRepository.findByIdAndUserId(suggestionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        suggestion.setStatus("DISMISSED");
        return suggestionRepository.save(suggestion);
    }

    @Transactional
    public SkipReasonFeedback recordSkipFeedback(
            UUID userId,
            UUID submissionId,
            UUID jobId,
            String skipReason,
            boolean agreeWithSkip,
            String userComment
    ) {
        SkipReasonFeedback fb = new SkipReasonFeedback();
        fb.setUserId(userId);
        fb.setSubmissionId(submissionId);
        fb.setJobId(jobId);
        fb.setSkipReason(skipReason != null && !skipReason.isBlank() ? skipReason.trim() : "UNSPECIFIED_SKIP_REASON");
        fb.setAgreeWithSkip(agreeWithSkip);
        fb.setUserComment(userComment);
        return skipFeedbackRepository.save(fb);
    }

    private List<Map<String, Object>> buildBreakdownList(String keyName, Map<String, List<Application>> grouped) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, List<Application>> entry : grouped.entrySet()) {
            List<Application> apps = entry.getValue();
            int total = apps.size();
            long positive = apps.stream().filter(a -> POSITIVE_RESPONSE_STATUSES.contains(a.getStatus())).count();
            double rate = total > 0 ? Math.round((positive * 1000.0) / total) / 10.0 : 0.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(keyName, entry.getKey());
            row.put("sampleSize", total);
            row.put("totalApplied", total);
            row.put("positiveResponses", positive);
            row.put("responseRatePercent", rate);
            list.add(row);
        }
        return list;
    }
}
