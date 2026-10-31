package in.skillsignal.privacy;

import in.skillsignal.agent.ApplyAgentRepository;
import in.skillsignal.agent.ApplyEvidenceRepository;
import in.skillsignal.agent.ApplyTaskRepository;
import in.skillsignal.agent.PortalSettingRepository;
import in.skillsignal.answer.AnswerLibraryRepository;
import in.skillsignal.application.ApplicationEventRepository;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.audit.AuditLog;
import in.skillsignal.audit.AuditLogRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.career.InterviewPrepSessionRepository;
import in.skillsignal.career.SkillRoadmapItemRepository;
import in.skillsignal.company.CompanyRepository;
import in.skillsignal.consent.ConsentRecordRepository;
import in.skillsignal.eligibility.AutoApplyPreferenceRepository;
import in.skillsignal.emailapply.ConnectedMailboxRepository;
import in.skillsignal.emailapply.EmailApplyMessageRepository;
import in.skillsignal.job.JobRepository;
import in.skillsignal.learning.RuleChangeSuggestionRepository;
import in.skillsignal.learning.SkipReasonFeedbackRepository;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.pack.ApplicationPackRepository;
import in.skillsignal.production.UsageEventRepository;
import in.skillsignal.production.UserFeedbackRepository;
import in.skillsignal.profile.CareerProfileRepository;
import in.skillsignal.reminder.NotificationRepository;
import in.skillsignal.reminder.NotificationSentLogRepository;
import in.skillsignal.reminder.ReminderRepository;
import in.skillsignal.reminder.UserNotificationPreferenceRepository;
import in.skillsignal.resume.ResumeRepository;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.resume.TailoredResumeRepository;
import in.skillsignal.rule.AutoApplyRuleRepository;
import in.skillsignal.rule.AutoApplyRunRepository;
import in.skillsignal.safety.SafetyEventRepository;
import in.skillsignal.safety.SourceCircuitBreakerRepository;
import in.skillsignal.search.SavedSearchRepository;
import in.skillsignal.security.AuthController;
import in.skillsignal.security.AuthenticatedUser;
import in.skillsignal.submission.ApplicationSubmissionAttemptRepository;
import in.skillsignal.submission.ApplicationSubmissionRepository;
import in.skillsignal.submission.IdempotencyKeyRepository;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    private final UserRepository userRepository;
    private final CareerProfileRepository profileRepository;
    private final ConsentRecordRepository consentRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSentLogRepository notificationSentLogRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final CompanyRepository companyRepository;
    private final AnswerLibraryRepository answerLibraryRepository;
    private final ApplicationPackRepository applicationPackRepository;
    private final AutoApplyPreferenceRepository autoApplyPreferenceRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ApplicationSubmissionRepository submissionRepository;
    private final ApplicationSubmissionAttemptRepository submissionAttemptRepository;
    private final AutoApplyRuleRepository autoApplyRuleRepository;
    private final AutoApplyRunRepository autoApplyRunRepository;
    private final ConnectedMailboxRepository connectedMailboxRepository;
    private final EmailApplyMessageRepository emailApplyMessageRepository;
    private final SafetyEventRepository safetyEventRepository;
    private final SourceCircuitBreakerRepository circuitBreakerRepository;
    private final ApplyAgentRepository applyAgentRepository;
    private final PortalSettingRepository portalSettingRepository;
    private final ApplyTaskRepository applyTaskRepository;
    private final ApplyEvidenceRepository applyEvidenceRepository;
    private final RuleChangeSuggestionRepository ruleChangeSuggestionRepository;
    private final SkipReasonFeedbackRepository skipReasonFeedbackRepository;
    private final SkillRoadmapItemRepository skillRoadmapItemRepository;
    private final InterviewPrepSessionRepository interviewPrepSessionRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final UsageEventRepository usageEventRepository;
    private final AuditService auditService;
    private final Path uploadRootDir;

    public PrivacyController(
            UserRepository userRepository,
            CareerProfileRepository profileRepository,
            ConsentRecordRepository consentRepository,
            ResumeRepository resumeRepository,
            ResumeVersionRepository resumeVersionRepository,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            AuditLogRepository auditLogRepository,
            SavedSearchRepository savedSearchRepository,
            TailoredResumeRepository tailoredResumeRepository,
            ReminderRepository reminderRepository,
            NotificationRepository notificationRepository,
            NotificationSentLogRepository notificationSentLogRepository,
            UserNotificationPreferenceRepository preferenceRepository,
            CompanyRepository companyRepository,
            AnswerLibraryRepository answerLibraryRepository,
            ApplicationPackRepository applicationPackRepository,
            AutoApplyPreferenceRepository autoApplyPreferenceRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            ApplicationSubmissionRepository submissionRepository,
            ApplicationSubmissionAttemptRepository submissionAttemptRepository,
            AutoApplyRuleRepository autoApplyRuleRepository,
            AutoApplyRunRepository autoApplyRunRepository,
            ConnectedMailboxRepository connectedMailboxRepository,
            EmailApplyMessageRepository emailApplyMessageRepository,
            SafetyEventRepository safetyEventRepository,
            SourceCircuitBreakerRepository circuitBreakerRepository,
            ApplyAgentRepository applyAgentRepository,
            PortalSettingRepository portalSettingRepository,
            ApplyTaskRepository applyTaskRepository,
            ApplyEvidenceRepository applyEvidenceRepository,
            RuleChangeSuggestionRepository ruleChangeSuggestionRepository,
            SkipReasonFeedbackRepository skipReasonFeedbackRepository,
            SkillRoadmapItemRepository skillRoadmapItemRepository,
            InterviewPrepSessionRepository interviewPrepSessionRepository,
            UserFeedbackRepository userFeedbackRepository,
            UsageEventRepository usageEventRepository,
            AuditService auditService,
            @Value("${app.uploads.dir:./uploads}") String uploadDir
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.consentRepository = consentRepository;
        this.resumeRepository = resumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.savedSearchRepository = savedSearchRepository;
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.reminderRepository = reminderRepository;
        this.notificationRepository = notificationRepository;
        this.notificationSentLogRepository = notificationSentLogRepository;
        this.preferenceRepository = preferenceRepository;
        this.companyRepository = companyRepository;
        this.answerLibraryRepository = answerLibraryRepository;
        this.applicationPackRepository = applicationPackRepository;
        this.autoApplyPreferenceRepository = autoApplyPreferenceRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.submissionRepository = submissionRepository;
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.autoApplyRuleRepository = autoApplyRuleRepository;
        this.autoApplyRunRepository = autoApplyRunRepository;
        this.connectedMailboxRepository = connectedMailboxRepository;
        this.emailApplyMessageRepository = emailApplyMessageRepository;
        this.safetyEventRepository = safetyEventRepository;
        this.circuitBreakerRepository = circuitBreakerRepository;
        this.applyAgentRepository = applyAgentRepository;
        this.portalSettingRepository = portalSettingRepository;
        this.applyTaskRepository = applyTaskRepository;
        this.applyEvidenceRepository = applyEvidenceRepository;
        this.ruleChangeSuggestionRepository = ruleChangeSuggestionRepository;
        this.skipReasonFeedbackRepository = skipReasonFeedbackRepository;
        this.skillRoadmapItemRepository = skillRoadmapItemRepository;
        this.interviewPrepSessionRepository = interviewPrepSessionRepository;
        this.userFeedbackRepository = userFeedbackRepository;
        this.usageEventRepository = usageEventRepository;
        this.auditService = auditService;
        this.uploadRootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> getAuditLogs(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
    }

    @GetMapping("/export")
    public Map<String, Object> exportUserData(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        auditService.log(user.getId(), "PRIVACY_DATA_EXPORTED", "User", user.getId().toString(), Map.of());

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now().toString());
        export.put("user", AuthController.UserDto.from(user));
        export.put("careerProfile", profileRepository.findByUserId(user.getId()).orElse(null));
        export.put("consents", consentRepository.findByUserIdOrderByConsentTypeAsc(user.getId()));
        export.put("resumes", resumeRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("resumeVersions", resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("jobs", jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("jobMatches", jobMatchRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("applications", applicationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()));
        export.put("applicationEvents", applicationEventRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("savedSearches", savedSearchRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("tailoredResumes", tailoredResumeRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("reminders", reminderRepository.findByUserIdOrderByDueAtAsc(user.getId()));
        export.put("notifications", notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("notificationPreferences", preferenceRepository.findById(user.getId()).orElse(null));
        export.put("companies", companyRepository.findByUserIdOrderByPriorityDescNameAsc(user.getId()));
        export.put("answerLibrary", answerLibraryRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()));
        export.put("autoApplyPreferences", autoApplyPreferenceRepository.findById(user.getId()).orElse(null));
        export.put("submissions", submissionRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()));
        export.put("autoApplyRules", autoApplyRuleRepository.findByUserIdOrderByPriorityOrderAscCreatedAtAsc(user.getId()));
        export.put("autoApplyRuns", autoApplyRunRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("emailApplyMessages", emailApplyMessageRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()));
        export.put("safetyEvents", safetyEventRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("portalSettings", portalSettingRepository.findByUserIdOrderByPortalAsc(user.getId()));
        export.put("applyTasks", applyTaskRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("applyEvidence", applyEvidenceRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("ruleChangeSuggestions", ruleChangeSuggestionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("skipReasonFeedbacks", skipReasonFeedbackRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("skillRoadmapItems", skillRoadmapItemRepository.findByUserIdOrderByWeekNumberAscSkillNameAsc(user.getId()));
        export.put("interviewPrepSessions", interviewPrepSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("userFeedback", userFeedbackRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("usageEvents", usageEventRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("auditLogs", auditLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        return export;
    }

    @DeleteMapping("/account")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 1. Delete user's uploaded files directory safely
        Path userUploadDir = uploadRootDir.resolve(user.getId().toString()).normalize();
        if (userUploadDir.startsWith(uploadRootDir) && Files.exists(userUploadDir)) {
            try (Stream<Path> walk = Files.walk(userUploadDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }

        // 2. Delete all user records
        usageEventRepository.deleteByUserId(user.getId());
        userFeedbackRepository.deleteByUserId(user.getId());
        interviewPrepSessionRepository.deleteByUserId(user.getId());
        skillRoadmapItemRepository.deleteByUserId(user.getId());
        skipReasonFeedbackRepository.deleteByUserId(user.getId());
        ruleChangeSuggestionRepository.deleteByUserId(user.getId());
        applyEvidenceRepository.deleteByUserId(user.getId());
        applyTaskRepository.deleteByUserId(user.getId());
        portalSettingRepository.deleteByUserId(user.getId());
        applyAgentRepository.deleteByUserId(user.getId());
        circuitBreakerRepository.deleteByUserId(user.getId());
        safetyEventRepository.deleteByUserId(user.getId());
        emailApplyMessageRepository.deleteByUserId(user.getId());
        connectedMailboxRepository.deleteByUserId(user.getId());
        autoApplyRunRepository.deleteByUserId(user.getId());
        autoApplyRuleRepository.deleteByUserId(user.getId());
        submissionAttemptRepository.deleteByUserId(user.getId());
        submissionRepository.deleteByUserId(user.getId());
        idempotencyKeyRepository.deleteByUserId(user.getId());
        autoApplyPreferenceRepository.deleteByUserId(user.getId());
        applicationPackRepository.deleteByUserId(user.getId());
        answerLibraryRepository.deleteByUserId(user.getId());
        companyRepository.deleteByUserId(user.getId());
        notificationSentLogRepository.deleteByUserId(user.getId());
        notificationRepository.deleteByUserId(user.getId());
        reminderRepository.deleteByUserId(user.getId());
        preferenceRepository.deleteByUserId(user.getId());
        tailoredResumeRepository.deleteByUserId(user.getId());
        savedSearchRepository.deleteByUserId(user.getId());
        applicationEventRepository.deleteByUserId(user.getId());
        applicationRepository.deleteByUserId(user.getId());
        jobMatchRepository.deleteByUserId(user.getId());
        jobRepository.deleteByUserId(user.getId());
        resumeVersionRepository.deleteByUserId(user.getId());
        resumeRepository.deleteByUserId(user.getId());
        consentRepository.deleteByUserId(user.getId());
        profileRepository.deleteByUserId(user.getId());
        auditLogRepository.deleteByUserId(user.getId());
        userRepository.delete(user);

        return ResponseEntity.ok(Map.of("message", "Account, records, and uploaded files have been permanently erased."));
    }
}
