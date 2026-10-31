package in.skillsignal.submission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.answer.QuestionClassifierAndAnswerService;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationEvent;
import in.skillsignal.application.ApplicationEventRepository;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.eligibility.StrictEligibilityService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.pack.ApplicationPackService;
import in.skillsignal.reminder.Reminder;
import in.skillsignal.reminder.ReminderAndNotificationService;
import in.skillsignal.reminder.ReminderRepository;
import in.skillsignal.security.MailSenderService;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SubmissionService {

    public record SubmissionAttemptDto(
            UUID id,
            UUID submissionId,
            String fromState,
            String toState,
            Map<String, Object> ruleDecision,
            String resumeHash,
            List<Map<String, Object>> answersUsed,
            Map<String, Object> packSnapshot,
            String providerResponse,
            String actor,
            Instant createdAt
    ) {}

    public record SubmissionDto(
            UUID id,
            UUID jobId,
            String jobTitle,
            String company,
            String location,
            String source,
            String sourceUrl,
            UUID packId,
            UUID applicationId,
            String state,
            Set<String> allowedNextStates,
            String idempotencyKey,
            StrictEligibilityService.StrictEligibilityResultDto ruleDecision,
            String whyAppliedOrSkipped,
            String resumeHash,
            List<QuestionClassifierAndAnswerService.QuestionResolutionDto> answersUsed,
            Map<String, Object> packSnapshot,
            Instant expiresAt,
            boolean expired,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ApprovalLinkResponseDto(
            UUID submissionId,
            String approvalToken,
            String approvalUrl,
            Instant expiresAt,
            String policyNote
    ) {}

    public record BatchApproveResultDto(
            int approvedCount,
            int skippedExceptionCount,
            List<SubmissionDto> approvedSubmissions,
            List<UUID> skippedExceptionIds
    ) {}

    private final Object submissionLock = new Object();

    private final ApplicationSubmissionRepository submissionRepository;
    private final ApplicationSubmissionAttemptRepository attemptRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final SubmissionStateMachine stateMachine;
    private final ApplicationPackService packService;
    private final StrictEligibilityService eligibilityService;
    private final QuestionClassifierAndAnswerService answerService;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderAndNotificationService reminderService;
    private final UserRepository userRepository;
    private final MailSenderService mailSenderService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SubmissionService(
            ApplicationSubmissionRepository submissionRepository,
            ApplicationSubmissionAttemptRepository attemptRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            SubmissionStateMachine stateMachine,
            ApplicationPackService packService,
            StrictEligibilityService eligibilityService,
            QuestionClassifierAndAnswerService answerService,
            JobRepository jobRepository,
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            ReminderRepository reminderRepository,
            ReminderAndNotificationService reminderService,
            UserRepository userRepository,
            MailSenderService mailSenderService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.submissionRepository = submissionRepository;
        this.attemptRepository = attemptRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.stateMachine = stateMachine;
        this.packService = packService;
        this.eligibilityService = eligibilityService;
        this.answerService = answerService;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.reminderRepository = reminderRepository;
        this.reminderService = reminderService;
        this.userRepository = userRepository;
        this.mailSenderService = mailSenderService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotently prepares an application submission for a job, stepping through
     * DISCOVERED -> MATCHED -> PACK_PREPARING -> PACK_READY -> AWAITING_APPROVAL
     * with immutable audit records at each transition.
     */
    @Transactional
    public SubmissionDto prepareSubmission(
            UUID userId,
            UUID jobId,
            String idempotencyKey,
            List<String> questions,
            Instant expiresAtOverride
    ) {
        synchronized (submissionLock) {
            Job job = jobRepository.findByIdAndUserId(jobId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? idempotencyKey.trim()
                    : ("PREPARE_JOB:" + job.getId());

            Optional<IdempotencyKey> existingKey = idempotencyKeyRepository
                    .findByUserIdAndIdempotencyKey(userId, effectiveKey);
            if (existingKey.isPresent()) {
                Optional<ApplicationSubmission> existingSub = submissionRepository
                        .findByIdAndUserId(existingKey.get().getTargetEntityId(), userId);
                if (existingSub.isPresent()) {
                    return toDto(existingSub.get(), job);
                }
            }

            Optional<ApplicationSubmission> bySubKey = submissionRepository
                    .findByUserIdAndIdempotencyKey(userId, effectiveKey);
            if (bySubKey.isPresent()) {
                return toDto(bySubKey.get(), job);
            }

            // Build pack & evaluate 9-check eligibility
            ApplicationPackService.ApplicationPackDto packDto = packService.getOrBuildPack(
                    userId, job.getId(), questions, null, null
            );
            StrictEligibilityService.StrictEligibilityResultDto eligDto = eligibilityService.evaluateForUserAndJob(
                    userId, job.getId(), questions, Instant.now()
            );

            String ruleDecisionJson = writeJson(eligDto);
            String answersJson = writeJson(packDto.answers());
            String packSnapshotJson = writeJson(packDto);

            Instant expiresAt = expiresAtOverride != null
                    ? expiresAtOverride
                    : Instant.now().plus(7, ChronoUnit.DAYS);

            ApplicationSubmission sub = new ApplicationSubmission();
            sub.setUserId(userId);
            sub.setJobId(job.getId());
            sub.setPackId(packDto.id());
            sub.setState("DISCOVERED");
            sub.setIdempotencyKey(effectiveKey);
            sub.setRuleDecisionJson(ruleDecisionJson);
            sub.setWhyAppliedOrSkipped(eligDto.whyAppliedOrSkipped());
            sub.setResumeHash(packDto.resumeHash());
            sub.setAnswersUsedJson(answersJson);
            sub.setPackSnapshotJson(packSnapshotJson);
            sub.setExpiresAt(expiresAt);
            sub = submissionRepository.saveAndFlush(sub);

            recordAttempt(sub, null, "DISCOVERED", "Job discovered for preparation", "SYSTEM");

            // Step through state machine: DISCOVERED -> MATCHED -> PACK_PREPARING -> PACK_READY -> AWAITING_APPROVAL
            stepInternal(sub, "MATCHED", "Strict match and 9 eligibility checks evaluated", "SYSTEM");
            stepInternal(sub, "PACK_PREPARING", "Preparing tailored application pack and truthfulness verification", "SYSTEM");
            stepInternal(sub, "PACK_READY", "Application pack ready (readiness=" + packDto.readinessScore() + ")", "SYSTEM");
            stepInternal(sub, "AWAITING_APPROVAL", "Routed to Approval Queue for user review", "SYSTEM");

            sub = submissionRepository.saveAndFlush(sub);

            IdempotencyKey ik = new IdempotencyKey();
            ik.setUserId(userId);
            ik.setIdempotencyKey(effectiveKey);
            ik.setTargetEntityId(sub.getId());
            idempotencyKeyRepository.saveAndFlush(ik);

            auditService.log(userId, "SUBMISSION_PREPARED", "ApplicationSubmission", sub.getId().toString(),
                    Map.of("jobId", job.getId().toString(), "idempotencyKey", effectiveKey, "state", sub.getState()));

            return toDto(sub, job);
        }
    }

    @Transactional
    public List<SubmissionDto> listSubmissions(UUID userId, String stateFilter) {
        Map<UUID, Job> jobsById = loadJobsMap(userId);
        return submissionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(sub -> {
                    Job job = jobsById.get(sub.getJobId());
                    checkAndExpireIfStaleOrPastExpiry(sub, job);
                    return toDto(sub, job);
                })
                .filter(dto -> stateFilter == null || stateFilter.isBlank() || dto.state().equalsIgnoreCase(stateFilter.trim()))
                .toList();
    }

    @Transactional
    public SubmissionDto getSubmission(UUID userId, UUID submissionId) {
        ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId).orElse(null);
        checkAndExpireIfStaleOrPastExpiry(sub, job);
        return toDto(sub, job);
    }

    public List<SubmissionAttemptDto> listAttempts(UUID userId, UUID submissionId) {
        ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        return attemptRepository.findBySubmissionIdAndUserIdOrderByCreatedAtAsc(sub.getId(), userId).stream()
                .map(this::toAttemptDto)
                .toList();
    }

    public Map<String, Object> getWhyExplanation(UUID userId, UUID submissionId) {
        ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        StrictEligibilityService.StrictEligibilityResultDto decision = parseRuleDecision(sub.getRuleDecisionJson());
        return Map.of(
                "submissionId", sub.getId(),
                "jobId", sub.getJobId(),
                "state", sub.getState(),
                "whyAppliedOrSkipped", sub.getWhyAppliedOrSkipped(),
                "ruleDecision", decision != null ? decision : Map.of()
        );
    }

    @Transactional
    public SubmissionDto transitionSubmission(
            UUID userId,
            UUID submissionId,
            String targetState,
            String providerResponse,
            String actor,
            boolean isUserResolveAction
    ) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId).orElse(null);

            String normTarget = stateMachine.normalizeState(targetState);
            stateMachine.validateTransition(sub.getState(), normTarget, isUserResolveAction);

            String oldState = sub.getState();
            sub.setState(normTarget);
            sub.setUpdatedAt(Instant.now());
            sub = submissionRepository.saveAndFlush(sub);

            recordAttempt(sub, oldState, normTarget, providerResponse, actor != null ? actor : "USER");
            return toDto(sub, job);
        }
    }

    /**
     * Resolves a submission in NEEDS_VERIFICATION state.
     * Golden Rule: NEEDS_VERIFICATION never auto-retries; only explicit user resolution
     * via this endpoint can move it to SUBMITTED_CONFIRMED or FAILED.
     */
    @Transactional
    public SubmissionDto resolveNeedsVerification(
            UUID userId,
            UUID submissionId,
            String resolution,
            String note
    ) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));

            if (!"NEEDS_VERIFICATION".equals(sub.getState())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Submission is in state " + sub.getState() + "; only NEEDS_VERIFICATION submissions can be resolved via this endpoint"
                );
            }

            String normRes = stateMachine.normalizeState(resolution);
            if ("CONFIRMED".equals(normRes) || "APPLIED".equals(normRes) || "USER_CONFIRMED".equals(normRes)) {
                normRes = "SUBMITTED_CONFIRMED";
            }
            if (!"SUBMITTED_CONFIRMED".equals(normRes) && !"FAILED".equals(normRes)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Resolution must be SUBMITTED_CONFIRMED or FAILED"
                );
            }

            stateMachine.validateTransition("NEEDS_VERIFICATION", normRes, true);

            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            String oldState = sub.getState();
            sub.setState(normRes);
            sub.setUpdatedAt(Instant.now());

            if ("SUBMITTED_CONFIRMED".equals(normRes)) {
                Application app = ensureAppliedTrackerAndFollowUp(
                        userId,
                        sub,
                        job,
                        "USER_CONFIRMED",
                        "ASSISTED",
                        note != null && !note.isBlank() ? note : "User resolved Needs Verification as Submitted-Confirmed"
                );
                sub.setApplicationId(app.getId());
                sub.setWhyAppliedOrSkipped("User resolved Needs Verification as Submitted-Confirmed. " + sub.getWhyAppliedOrSkipped());
            } else {
                // FAILED
                applicationRepository.findByUserIdAndJobId(userId, job.getId()).ifPresent(app -> {
                    app.setStatus("SUBMISSION_FAILED");
                    app.setSubState(null);
                    app.setConfirmationType("UNCONFIRMED");
                    app.setNotes(note != null && !note.isBlank() ? note : "User resolved Needs Verification as Failed");
                    applicationRepository.saveAndFlush(app);
                });
            }

            sub = submissionRepository.saveAndFlush(sub);
            recordAttempt(
                    sub,
                    oldState,
                    normRes,
                    note != null && !note.isBlank() ? note : ("User resolved Needs Verification -> " + normRes),
                    "USER"
            );

            auditService.log(userId, "SUBMISSION_NEEDS_VERIFICATION_RESOLVED", "ApplicationSubmission", sub.getId().toString(),
                    Map.of("resolution", normRes));

            return toDto(sub, job);
        }
    }

    // =========================================================================
    // STAGE 22: MODES 1 & 2 (ASSISTED APPLY & APPROVAL QUEUE)
    // =========================================================================

    @Transactional
    public List<SubmissionDto> listApprovalQueue(UUID userId) {
        Map<UUID, Job> jobsById = loadJobsMap(userId);
        return submissionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(sub -> {
                    Job job = jobsById.get(sub.getJobId());
                    checkAndExpireIfStaleOrPastExpiry(sub, job);
                    return toDto(sub, job);
                })
                .toList();
    }

    /**
     * Approves an item in the Approval Queue.
     * In Sprint 4, "approve" ONLY leads to the user-submits (Assisted Apply) path:
     * transitions submission to APPROVED and ensures a single tracker Application in READY_TO_APPLY status.
     * Concurrent approvals are serialized so they produce EXACTLY ONE tracker Application.
     */
    @Transactional
    public SubmissionDto approveQueueItem(UUID userId, UUID submissionId, String note) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            // Freshness and expiry re-check before approving
            if (checkAndExpireIfStaleOrPastExpiry(sub, job)) {
                throw new ResponseStatusException(
                        HttpStatus.GONE,
                        "Cannot approve: submission has expired or the job listing is now flagged stale/closed"
                );
            }

            if ("NEEDS_VERIFICATION".equals(sub.getState())) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Submission is in NEEDS_VERIFICATION state: use /api/submissions/{id}/resolve instead"
                );
            }

            // Idempotent if already APPROVED or SUBMITTED_CONFIRMED
            if (!"APPROVED".equals(sub.getState()) && !"SUBMITTED_CONFIRMED".equals(sub.getState())) {
                stateMachine.validateTransition(sub.getState(), "APPROVED", false);
                String oldState = sub.getState();
                sub.setState("APPROVED");
                sub.setUpdatedAt(Instant.now());
                recordAttempt(
                        sub,
                        oldState,
                        "APPROVED",
                        note != null && !note.isBlank() ? note : "Approved by user for Assisted Apply (user-submits path)",
                        "USER"
                );
            }

            // Ensure exactly one tracker Application exists on the user-submits path (READY_TO_APPLY)
            Application app = applicationRepository.findByUserIdAndJobId(userId, job.getId())
                    .orElseGet(() -> {
                        Application a = new Application();
                        a.setUserId(userId);
                        a.setJobId(job.getId());
                        a.setStatus("READY_TO_APPLY");
                        a.setApplyMode("ASSISTED");
                        a.setSubmissionMethod("ASSISTED");
                        a.setConfirmationType("UNCONFIRMED");
                        a.setSource(job.getSource());
                        a.setApplicationUrl(job.getSourceUrl());
                        a.setNotes("Approved in Review Queue — ready for Assisted Apply (user submits).");
                        Application saved = applicationRepository.saveAndFlush(a);

                        ApplicationEvent ev = new ApplicationEvent();
                        ev.setApplicationId(saved.getId());
                        ev.setUserId(userId);
                        ev.setFromStatus(null);
                        ev.setToStatus("READY_TO_APPLY");
                        ev.setNote("Approved from Review Queue (user-submits path)");
                        applicationEventRepository.save(ev);
                        return saved;
                    });

            sub.setApplicationId(app.getId());
            sub = submissionRepository.saveAndFlush(sub);

            auditService.log(userId, "APPROVAL_QUEUE_ITEM_APPROVED", "ApplicationSubmission", sub.getId().toString(),
                    Map.of("jobId", job.getId().toString(), "applicationId", app.getId().toString()));

            return toDto(sub, job);
        }
    }

    @Transactional
    public SubmissionDto rejectQueueItem(UUID userId, UUID submissionId, String note) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId).orElse(null);

            stateMachine.validateTransition(sub.getState(), "REJECTED", false);
            String oldState = sub.getState();
            sub.setState("REJECTED");
            sub.setUpdatedAt(Instant.now());
            sub = submissionRepository.saveAndFlush(sub);

            recordAttempt(sub, oldState, "REJECTED", note != null ? note : "Rejected by user in Approval Queue", "USER");
            return toDto(sub, job);
        }
    }

    @Transactional
    public SubmissionDto skipQueueItem(UUID userId, UUID submissionId, String note) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId).orElse(null);

            stateMachine.validateTransition(sub.getState(), "BLOCKED_MANUAL_ACTION", false);
            String oldState = sub.getState();
            sub.setState("BLOCKED_MANUAL_ACTION");
            sub.setUpdatedAt(Instant.now());
            sub = submissionRepository.saveAndFlush(sub);

            recordAttempt(sub, oldState, "BLOCKED_MANUAL_ACTION", note != null ? note : "Skipped by user in Approval Queue", "USER");
            return toDto(sub, job);
        }
    }

    @Transactional
    public SubmissionDto updateQueueItemAnswers(
            UUID userId,
            UUID submissionId,
            Map<String, String> updatedAnswersByQuestion,
             boolean saveToAnswerLibrary
    ) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            List<String> questions = new ArrayList<>();
            if (updatedAnswersByQuestion != null && !updatedAnswersByQuestion.isEmpty()) {
                for (Map.Entry<String, String> entry : updatedAnswersByQuestion.entrySet()) {
                    String qText = entry.getKey();
                    String ansText = entry.getValue();
                    questions.add(qText);
                    if (saveToAnswerLibrary && ansText != null && !ansText.isBlank()) {
                        answerService.createOrUpdateAnswer(userId, qText, null, ansText, 1.0, null);
                    }
                }
            }

            List<QuestionClassifierAndAnswerService.QuestionResolutionDto> currentAnswers = parseAnswers(sub.getAnswersUsedJson());
            List<QuestionClassifierAndAnswerService.QuestionResolutionDto> newAnswers = new ArrayList<>();

            if (updatedAnswersByQuestion != null && !updatedAnswersByQuestion.isEmpty()) {
                for (Map.Entry<String, String> entry : updatedAnswersByQuestion.entrySet()) {
                    String qText = entry.getKey();
                    String ansText = entry.getValue();
                    String normQ = answerService.normalizeQuestion(qText);
                    String category = answerService.classifyQuestion(qText);
                    newAnswers.add(new QuestionClassifierAndAnswerService.QuestionResolutionDto(
                            qText,
                            normQ,
                            category,
                            ansText,
                            1.0,
                            "USER_EDITED_IN_QUEUE",
                            true,
                            false,
                            !saveToAnswerLibrary,
                            "USER_APPROVED",
                            "User edited and approved answer in Review Queue"
                    ));
                }
            } else {
                newAnswers.addAll(currentAnswers);
            }

            String oldState = sub.getState();
            stateMachine.validateTransition(oldState, "EDITED", false);
            sub.setState("EDITED");
            sub.setAnswersUsedJson(writeJson(newAnswers));
            sub.setUpdatedAt(Instant.now());
            sub = submissionRepository.saveAndFlush(sub);

            recordAttempt(sub, oldState, "EDITED", "User updated screening answers in Approval Queue", "USER");
            return toDto(sub, job);
        }
    }

    @Transactional
    public BatchApproveResultDto batchApproveQueueItems(
            UUID userId,
            List<UUID> submissionIds,
            List<UUID> exceptionIds,
            String note
    ) {
        Set<UUID> exceptions = exceptionIds != null ? new HashSet<>(exceptionIds) : Set.of();
        List<SubmissionDto> approvedList = new ArrayList<>();
        List<UUID> skippedList = new ArrayList<>();

        if (submissionIds != null) {
            for (UUID id : submissionIds) {
                if (exceptions.contains(id)) {
                    skippedList.add(id);
                    continue;
                }
                SubmissionDto dto = approveQueueItem(userId, id, note != null ? note : "Batch approved by user");
                approvedList.add(dto);
            }
        }

        return new BatchApproveResultDto(
                approvedList.size(),
                skippedList.size(),
                approvedList,
                skippedList
        );
    }

    /**
     * Generates a secure single-use expiring approval token, sends the approval link via email,
     * and stores only the SHA-256 token hash on the submission.
     */
    @Transactional
    public ApprovalLinkResponseDto generateAndSendApprovalLink(UUID userId, UUID submissionId) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
            String tokenHash = ApplicationPackService.sha256Hex(rawToken);
            Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

            sub.setApprovalTokenHash(tokenHash);
            sub.setApprovalTokenExpiresAt(expiresAt);
            sub.setApprovalTokenUsedAt(null);
            submissionRepository.saveAndFlush(sub);

            User user = userRepository.findById(userId).orElse(null);
            String email = user != null ? user.getEmail() : "user@skillsignal.in";
            String approvalUrl = "http://localhost:5173/approval-queue?token=" + rawToken;

            mailSenderService.sendApprovalLinkEmail(email, job.getTitle(), job.getCompany(), approvalUrl);

            auditService.log(userId, "APPROVAL_LINK_ISSUED", "ApplicationSubmission", sub.getId().toString(),
                    Map.of("expiresAt", expiresAt.toString()));

            return new ApprovalLinkResponseDto(
                    sub.getId(),
                    rawToken,
                    approvalUrl,
                    expiresAt,
                    "Single-use expiring approval link sent. Login is required when redeeming the token."
            );
        }
    }

    /**
     * Redeems a single-use expiring email approval token (requires authenticated user session).
     */
    @Transactional
    public SubmissionDto approveBySingleUseToken(UUID userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval token is required");
        }
        synchronized (submissionLock) {
            String tokenHash = ApplicationPackService.sha256Hex(rawToken.trim());
            ApplicationSubmission sub = submissionRepository.findByUserIdAndApprovalTokenHash(userId, tokenHash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid approval token"));

            if (sub.getApprovalTokenUsedAt() != null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This single-use approval link has already been used"
                );
            }
            if (sub.getApprovalTokenExpiresAt() == null || sub.getApprovalTokenExpiresAt().isBefore(Instant.now())) {
                throw new ResponseStatusException(
                        HttpStatus.GONE,
                        "This approval link has expired"
                );
            }

            sub.setApprovalTokenUsedAt(Instant.now());
            submissionRepository.saveAndFlush(sub);

            return approveQueueItem(userId, sub.getId(), "Approved via secure single-use email link");
        }
    }

    /**
     * Mode 1 (Assisted Apply): User clicks "I applied" after manually submitting on the original page.
     * Sets submission to SUBMITTED_CONFIRMED, tracker Application to APPLIED with USER_CONFIRMED,
     * and auto-creates a +7 day FOLLOW_UP reminder and follow-up email draft (Stage 23 F17).
     */
    @Transactional
    public SubmissionDto confirmAssistedApplied(UUID userId, UUID submissionId, String note) {
        synchronized (submissionLock) {
            ApplicationSubmission sub = submissionRepository.findByIdAndUserId(submissionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
            Job job = jobRepository.findByIdAndUserId(sub.getJobId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

            if (!"SUBMITTED_CONFIRMED".equals(sub.getState())) {
                stateMachine.validateTransition(sub.getState(), "SUBMITTED_CONFIRMED", false);
                String oldState = sub.getState();
                sub.setState("SUBMITTED_CONFIRMED");
                sub.setUpdatedAt(Instant.now());
                recordAttempt(
                        sub,
                        oldState,
                        "SUBMITTED_CONFIRMED",
                        note != null && !note.isBlank() ? note : "User confirmed Assisted Apply ('I applied' clicked)",
                        "USER"
                );
            }

            Application app = ensureAppliedTrackerAndFollowUp(
                    userId,
                    sub,
                    job,
                    "USER_CONFIRMED",
                    "ASSISTED",
                    note != null && !note.isBlank() ? note : "User confirmed application via Assisted Apply ('I applied')"
            );
            sub.setApplicationId(app.getId());
            sub = submissionRepository.saveAndFlush(sub);

            auditService.log(userId, "ASSISTED_APPLY_USER_CONFIRMED", "ApplicationSubmission", sub.getId().toString(),
                    Map.of("jobId", job.getId().toString(), "applicationId", app.getId().toString(), "confirmationType", "USER_CONFIRMED"));

            return toDto(sub, job);
        }
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private Application ensureAppliedTrackerAndFollowUp(
            UUID userId,
            ApplicationSubmission sub,
            Job job,
            String confirmationType,
            String submissionMethod,
            String note
    ) {
        Application app = applicationRepository.findByUserIdAndJobId(userId, job.getId())
                .orElseGet(() -> {
                    Application a = new Application();
                    a.setUserId(userId);
                    a.setJobId(job.getId());
                    a.setSource(job.getSource());
                    a.setApplicationUrl(job.getSourceUrl());
                    return a;
                });

        String prevStatus = app.getStatus();
        app.setStatus("APPLIED");
        app.setSubState(null);
        app.setConfirmationType(confirmationType != null ? confirmationType : "USER_CONFIRMED");
        app.setApplyMode(submissionMethod != null ? submissionMethod : "ASSISTED");
        app.setSubmissionMethod(submissionMethod != null ? submissionMethod : "ASSISTED");
        if (app.getAppliedAt() == null) {
            app.setAppliedAt(Instant.now());
        }
        if (app.getFollowUpDate() == null) {
            app.setFollowUpDate(LocalDate.now().plusDays(7));
        }
        app.setNotes(note);
        app = applicationRepository.saveAndFlush(app);

        ApplicationEvent ev = new ApplicationEvent();
        ev.setApplicationId(app.getId());
        ev.setUserId(userId);
        ev.setFromStatus(prevStatus);
        ev.setToStatus("APPLIED");
        ev.setNote(note);
        applicationEventRepository.save(ev);

        // Stage 23 (F17): Auto-create follow-up reminder and draft after Applied
        final UUID appId = app.getId();
        boolean hasFollowUpReminder = reminderRepository.findByUserIdOrderByDueAtAsc(userId).stream()
                .anyMatch(r -> appId.equals(r.getApplicationId()) && "FOLLOW_UP".equalsIgnoreCase(r.getReminderType()));
        if (!hasFollowUpReminder) {
            ReminderAndNotificationService.FollowUpDraftDto draft = reminderService.generateFollowUpEmailDraft(userId, appId);
            Reminder rem = new Reminder();
            rem.setUserId(userId);
            rem.setApplicationId(appId);
            rem.setReminderType("FOLLOW_UP");
            rem.setTitle("Follow up on " + job.getTitle() + " at " + job.getCompany());
            rem.setDueAt(Instant.now().plus(7, ChronoUnit.DAYS));
            rem.setStatus("PENDING");
            rem.setNotes("Auto-generated follow-up draft:\nSubject: " + draft.subject() + "\n\n" + draft.body());
            reminderRepository.save(rem);
        }

        return app;
    }

    private boolean checkAndExpireIfStaleOrPastExpiry(ApplicationSubmission sub, Job job) {
        Set<String> expirableStates = Set.of("DISCOVERED", "MATCHED", "PACK_READY", "AWAITING_APPROVAL", "EDITED", "APPROVED");
        if (!expirableStates.contains(sub.getState())) {
            return "EXPIRED".equals(sub.getState());
        }
        boolean pastExpiry = sub.getExpiresAt() != null && sub.getExpiresAt().isBefore(Instant.now());
        boolean jobStale = job != null && job.isStale();
        if (pastExpiry || jobStale) {
            String oldState = sub.getState();
            sub.setState("EXPIRED");
            sub.setUpdatedAt(Instant.now());
            submissionRepository.saveAndFlush(sub);
            String reason = jobStale
                    ? "Freshness re-check failed: job listing is flagged stale/closed"
                    : "Approval window expired at " + sub.getExpiresAt();
            recordAttempt(sub, oldState, "EXPIRED", reason, "SYSTEM");
            return true;
        }
        return false;
    }

    private void stepInternal(ApplicationSubmission sub, String nextState, String message, String actor) {
        String fromState = sub.getState();
        stateMachine.validateTransition(fromState, nextState, false);
        sub.setState(nextState);
        sub.setUpdatedAt(Instant.now());
        recordAttempt(sub, fromState, nextState, message, actor);
    }

    private void recordAttempt(
            ApplicationSubmission sub,
            String fromState,
            String toState,
            String providerResponse,
            String actor
    ) {
        ApplicationSubmissionAttempt att = new ApplicationSubmissionAttempt();
        att.setSubmissionId(sub.getId());
        att.setUserId(sub.getUserId());
        att.setFromState(fromState);
        att.setToState(toState);
        att.setRuleDecisionJson(sub.getRuleDecisionJson());
        att.setResumeHash(sub.getResumeHash());
        att.setAnswersUsedJson(sub.getAnswersUsedJson());
        att.setPackSnapshotJson(sub.getPackSnapshotJson());
        att.setProviderResponse(providerResponse);
        att.setActor(actor);
        attemptRepository.save(att);
    }

    private Map<UUID, Job> loadJobsMap(UUID userId) {
        Map<UUID, Job> map = new HashMap<>();
        jobRepository.findByUserIdOrderByCreatedAtDesc(userId).forEach(j -> map.put(j.getId(), j));
        return map;
    }

    private SubmissionDto toDto(ApplicationSubmission sub, Job job) {
        boolean isExpired = "EXPIRED".equals(sub.getState())
                || (sub.getExpiresAt() != null && sub.getExpiresAt().isBefore(Instant.now()));
        return new SubmissionDto(
                sub.getId(),
                sub.getJobId(),
                job != null ? job.getTitle() : "Unknown Job",
                job != null ? job.getCompany() : "",
                job != null ? job.getLocation() : "",
                job != null ? job.getSource() : "manual",
                job != null ? job.getSourceUrl() : null,
                sub.getPackId(),
                sub.getApplicationId(),
                sub.getState(),
                stateMachine.getAllowedNextStates(sub.getState()),
                sub.getIdempotencyKey(),
                parseRuleDecision(sub.getRuleDecisionJson()),
                sub.getWhyAppliedOrSkipped(),
                sub.getResumeHash(),
                parseAnswers(sub.getAnswersUsedJson()),
                parseMap(sub.getPackSnapshotJson()),
                sub.getExpiresAt(),
                isExpired,
                sub.getCreatedAt(),
                sub.getUpdatedAt()
        );
    }

    private SubmissionAttemptDto toAttemptDto(ApplicationSubmissionAttempt a) {
        return new SubmissionAttemptDto(
                a.getId(),
                a.getSubmissionId(),
                a.getFromState(),
                a.getToState(),
                parseMap(a.getRuleDecisionJson()),
                a.getResumeHash(),
                parseListOfMaps(a.getAnswersUsedJson()),
                parseMap(a.getPackSnapshotJson()),
                a.getProviderResponse(),
                a.getActor(),
                a.getCreatedAt()
        );
    }

    private StrictEligibilityService.StrictEligibilityResultDto parseRuleDecision(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, StrictEligibilityService.StrictEligibilityResultDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<QuestionClassifierAndAnswerService.QuestionResolutionDto> parseAnswers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<QuestionClassifierAndAnswerService.QuestionResolutionDto>>() {}
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseListOfMaps(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
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
}
