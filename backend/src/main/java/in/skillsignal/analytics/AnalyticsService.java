package in.skillsignal.analytics;

import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.reminder.Reminder;
import in.skillsignal.reminder.ReminderRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.safety.SourceCircuitBreaker;
import in.skillsignal.safety.SourceCircuitBreakerRepository;
import in.skillsignal.source.SourceCapability;
import in.skillsignal.source.SourceCapabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    public static final String RESPONSE_RATE_FORMULA =
            "Response Rate (%) = (Applications reaching Assessment, Interview, or Offer / Total Applications in Applied or post-Applied statuses) × 100";

    private static final Set<String> POSITIVE_RESPONSE_STATUSES = Set.of("ASSESSMENT", "INTERVIEW", "OFFER");
    private static final Set<String> APPLIED_OR_BEYOND_STATUSES = Set.of(
            "APPLIED", "ASSESSMENT", "INTERVIEW", "ON_HOLD", "OFFER", "REJECTED", "CLOSED"
    );

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ReminderRepository reminderRepository;
    private final SourceCapabilityRepository sourceCapabilityRepository;
    private final SourceCircuitBreakerRepository circuitBreakerRepository;

    public AnalyticsService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            ResumeVersionRepository resumeVersionRepository,
            ReminderRepository reminderRepository,
            SourceCapabilityRepository sourceCapabilityRepository,
            SourceCircuitBreakerRepository circuitBreakerRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.reminderRepository = reminderRepository;
        this.sourceCapabilityRepository = sourceCapabilityRepository;
        this.circuitBreakerRepository = circuitBreakerRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJobSearchAnalytics(UUID userId) {
        List<Application> applications = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<Job> jobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<ResumeVersion> resumeVersions = resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Reminder> reminders = reminderRepository.findByUserIdOrderByDueAtAsc(userId);

        Map<UUID, ResumeVersion> rvById = resumeVersions.stream()
                .collect(Collectors.toMap(ResumeVersion::getId, r -> r, (a, b) -> a));

        // Metric 1: Applications per week (with sampleSize)
        Map<String, Long> weekBucketCounts = new TreeMap<>();
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        WeekFields wf = WeekFields.ISO;
        for (Application app : applications) {
            Instant ts = app.getAppliedAt() != null ? app.getAppliedAt() : app.getCreatedAt();
            if (ts != null) {
                LocalDate d = ts.atZone(zone).toLocalDate();
                String weekKey = d.get(wf.weekBasedYear()) + "-W" + String.format("%02d", d.get(wf.weekOfWeekBasedYear()));
                weekBucketCounts.merge(weekKey, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> weeklyBuckets = new ArrayList<>();
        for (var e : weekBucketCounts.entrySet()) {
            weeklyBuckets.add(Map.of("week", e.getKey(), "count", e.getValue()));
        }
        Map<String, Object> applicationsPerWeek = new LinkedHashMap<>();
        applicationsPerWeek.put("sampleSize", applications.size());
        applicationsPerWeek.put("weeks", weeklyBuckets);

        // Metric 2: Applications by status (with sampleSize)
        Map<String, Long> byStatusCounts = applications.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getStatus() != null ? a.getStatus() : "UNKNOWN",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        List<Map<String, Object>> statusBreakdown = new ArrayList<>();
        for (var e : byStatusCounts.entrySet()) {
            statusBreakdown.add(Map.of("status", e.getKey(), "count", e.getValue()));
        }
        Map<String, Object> applicationsByStatus = new LinkedHashMap<>();
        applicationsByStatus.put("sampleSize", applications.size());
        applicationsByStatus.put("breakdown", statusBreakdown);

        // Metric 3: Response rate (with explicit formula shown in UI & sampleSize)
        List<Application> appliedApps = applications.stream()
                .filter(a -> APPLIED_OR_BEYOND_STATUSES.contains(a.getStatus()))
                .toList();
        long positiveResponses = appliedApps.stream()
                .filter(a -> POSITIVE_RESPONSE_STATUSES.contains(a.getStatus()))
                .count();
        double responseRatePct = appliedApps.isEmpty()
                ? 0.0
                : Math.round((positiveResponses * 1000.0) / appliedApps.size()) / 10.0;

        Map<String, Object> responseRate = new LinkedHashMap<>();
        responseRate.put("sampleSize", appliedApps.size());
        responseRate.put("totalApplied", appliedApps.size());
        responseRate.put("positiveResponses", positiveResponses);
        responseRate.put("ratePercent", responseRatePct);
        responseRate.put("formula", RESPONSE_RATE_FORMULA);

        // Metric 4: Results by resume version (with sampleSize)
        Map<String, List<Application>> byRvGroup = appliedApps.stream()
                .collect(Collectors.groupingBy(a -> {
                    if (a.getResumeVersionId() != null && rvById.containsKey(a.getResumeVersionId())) {
                        ResumeVersion rv = rvById.get(a.getResumeVersionId());
                        return "v" + rv.getVersionNumber() + " (" + rv.getOriginalFilename() + ")";
                    }
                    return "Default / Unspecified Resume";
                }));
        List<Map<String, Object>> rvRows = new ArrayList<>();
        for (var entry : byRvGroup.entrySet()) {
            List<Application> list = entry.getValue();
            long pos = list.stream().filter(a -> POSITIVE_RESPONSE_STATUSES.contains(a.getStatus())).count();
            double pct = list.isEmpty() ? 0.0 : Math.round((pos * 1000.0) / list.size()) / 10.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resumeVersion", entry.getKey());
            row.put("sampleSize", list.size());
            row.put("totalApplied", list.size());
            row.put("positiveResponses", pos);
            row.put("responseRatePercent", pct);
            rvRows.add(row);
        }
        Map<String, Object> resultsByResumeVersion = new LinkedHashMap<>();
        resultsByResumeVersion.put("sampleSize", appliedApps.size());
        resultsByResumeVersion.put("versions", rvRows);

        // Metric 5: Follow-up completion (with sampleSize)
        List<Reminder> followUpReminders = reminders.stream()
                .filter(r -> "FOLLOW_UP".equalsIgnoreCase(r.getReminderType()))
                .toList();
        long completedFollowUps = followUpReminders.stream()
                .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
                .count();
        double followUpRatePct = followUpReminders.isEmpty()
                ? 0.0
                : Math.round((completedFollowUps * 1000.0) / followUpReminders.size()) / 10.0;

        Map<String, Object> followUpCompletion = new LinkedHashMap<>();
        followUpCompletion.put("sampleSize", followUpReminders.size());
        followUpCompletion.put("totalFollowUpReminders", followUpReminders.size());
        followUpCompletion.put("completedFollowUps", completedFollowUps);
        followUpCompletion.put("completionRatePercent", followUpRatePct);

        // Metric 6: Discovered vs Saved vs Applied funnel (with sampleSize)
        long savedJobsCount = jobs.stream().filter(Job::isSaved).count();
        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("sampleSize", jobs.size());
        funnel.put("discovered", jobs.size());
        funnel.put("saved", savedJobsCount);
        funnel.put("applied", appliedApps.size());

        // Disclosed salary ranges only (never estimate undisclosed salaries!)
        List<Map<String, Object>> disclosedSalaries = new ArrayList<>();
        int undisclosedExcluded = 0;
        for (Job j : jobs) {
            if (!j.isStale() && (j.getSalaryMin() != null || j.getSalaryMax() != null)) {
                Map<String, Object> sRow = new LinkedHashMap<>();
                sRow.put("jobId", j.getId());
                sRow.put("title", j.getTitle());
                sRow.put("company", j.getCompany());
                sRow.put("salaryMin", j.getSalaryMin());
                sRow.put("salaryMax", j.getSalaryMax());
                sRow.put("currency", j.getSalaryCurrency() != null ? j.getSalaryCurrency() : "INR");
                sRow.put("period", j.getSalaryPeriod() != null ? j.getSalaryPeriod() : "YEARLY");
                disclosedSalaries.add(sRow);
            } else {
                undisclosedExcluded++;
            }
        }
        Map<String, Object> disclosedSalaryRangesOnly = new LinkedHashMap<>();
        disclosedSalaryRangesOnly.put("sampleSize", disclosedSalaries.size());
        disclosedSalaryRangesOnly.put("undisclosedExcludedCount", undisclosedExcluded);
        disclosedSalaryRangesOnly.put("note", "Only disclosed salary ranges from stored listings are included; undisclosed listings are never estimated or fabricated.");
        disclosedSalaryRangesOnly.put("listings", disclosedSalaries);

        // Source Health Table (last success, errors, freshness)
        List<SourceCapability> capabilities = sourceCapabilityRepository.findAllByOrderByEnabledDescSourceAsc();
        Map<String, SourceCircuitBreaker> breakerBySource = circuitBreakerRepository.findByUserIdOrderBySourceAsc(userId).stream()
                .collect(Collectors.toMap(cb -> cb.getSource().toLowerCase(Locale.ROOT), cb -> cb, (a, b) -> a));
        Map<String, Instant> latestJobBySource = new HashMap<>();
        for (Job j : jobs) {
            String src = j.getSource() != null ? j.getSource().toLowerCase(Locale.ROOT) : "manual";
            Instant ts = j.getUpdatedAt() != null ? j.getUpdatedAt() : j.getCreatedAt();
            if (ts != null) {
                latestJobBySource.merge(src, ts, (oldTs, newTs) -> newTs.isAfter(oldTs) ? newTs : oldTs);
            }
        }

        List<Map<String, Object>> sourceHealthTable = new ArrayList<>();
        for (SourceCapability cap : capabilities) {
            String key = cap.getSource().toLowerCase(Locale.ROOT);
            SourceCircuitBreaker cb = breakerBySource.get(key);
            Instant lastSuccess = latestJobBySource.get(key);
            int errors = cb != null ? cb.getConsecutiveFailures() : 0;
            String state = cb != null ? cb.getState() : "CLOSED";
            String freshness = !cap.isEnabled()
                    ? "DISABLED_NO_VERIFIED_API"
                    : (lastSuccess != null ? "FRESH" : "READY_IDLE");

            Map<String, Object> sh = new LinkedHashMap<>();
            sh.put("source", cap.getSource());
            sh.put("displayName", cap.getDisplayName());
            sh.put("enabled", cap.isEnabled());
            sh.put("lastSuccessAt", lastSuccess != null ? lastSuccess.toString() : null);
            sh.put("consecutiveErrors", errors);
            sh.put("circuitBreakerState", state);
            sh.put("freshness", freshness);
            sourceHealthTable.add(sh);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationsPerWeek", applicationsPerWeek);
        result.put("applicationsByStatus", applicationsByStatus);
        result.put("responseRate", responseRate);
        result.put("resultsByResumeVersion", resultsByResumeVersion);
        result.put("followUpCompletion", followUpCompletion);
        result.put("discoveredVsSavedVsApplied", funnel);
        result.put("disclosedSalaryRangesOnly", disclosedSalaryRangesOnly);
        result.put("sourceHealthTable", sourceHealthTable);
        return result;
    }
}

