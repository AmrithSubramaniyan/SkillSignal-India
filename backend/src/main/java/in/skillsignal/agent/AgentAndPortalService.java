package in.skillsignal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.consent.ConsentRecord;
import in.skillsignal.consent.ConsentRecordRepository;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.AutoApplyPreferenceRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.rule.AutoApplyRunRepository;
import in.skillsignal.safety.SafetyGovernorService;
import in.skillsignal.source.SourceCapability;
import in.skillsignal.source.SourceCapabilityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AgentAndPortalService {

    public static final String REQUIRED_TYPED_CONFIRMATION = "I UNDERSTAND THE ACCOUNT BAN AND LEGAL RISKS";
    private static final Set<String> MODE3_ALLOWED_PORTALS = Set.of("mock_portal", "email_apply");
    private static final List<String>FORBIDDEN_CREDENTIAL_KEYS = List.of(
            "\"password\"", "\"cookie\"", "\"cookies\"", "\"sessiontoken\"", "\"session_token\""
    );

    private final ApplyAgentRepository agentRepository;
    private final PortalSettingRepository portalSettingRepository;
    private final ApplyTaskRepository taskRepository;
    private final ApplyEvidenceRepository evidenceRepository;
    private final AutoApplyRunRepository autoApplyRunRepository;
    private final AutoApplyPreferenceRepository preferenceRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final SourceCapabilityRepository sourceCapabilityRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final SafetyGovernorService safetyGovernorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentAndPortalService(
            ApplyAgentRepository agentRepository,
            PortalSettingRepository portalSettingRepository,
            ApplyTaskRepository taskRepository,
            ApplyEvidenceRepository evidenceRepository,
            AutoApplyRunRepository autoApplyRunRepository,
            AutoApplyPreferenceRepository preferenceRepository,
            ConsentRecordRepository consentRecordRepository,
            SourceCapabilityRepository sourceCapabilityRepository,
            JobRepository jobRepository,
            ApplicationRepository applicationRepository,
            SafetyGovernorService safetyGovernorService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.agentRepository = agentRepository;
        this.portalSettingRepository = portalSettingRepository;
        this.taskRepository = taskRepository;
        this.evidenceRepository = evidenceRepository;
        this.autoApplyRunRepository = autoApplyRunRepository;
        this.preferenceRepository = preferenceRepository;
        this.consentRecordRepository = consentRecordRepository;
        this.sourceCapabilityRepository = sourceCapabilityRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.safetyGovernorService = safetyGovernorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // STAGE 27: MODE 3 ENABLEMENT & PORTAL SETTINGS (F11)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<PortalSetting> listPortalSettings(UUID userId) {
        return portalSettingRepository.findByUserIdOrderByPortalAsc(userId);
    }

    @Transactional
    public Map<String, Object> updatePortalSetting(
            UUID userId,
            String portal,
            Boolean consentEnabled,
            String consentVersion,
            String typedConfirmation,
            String applyMode,
            String loginState,
            Boolean allowMockPortalAutoSubmitOverride
    ) {
        String normPortal = normalizePortal(portal);
        PortalSetting setting = portalSettingRepository.findByUserIdAndPortalIgnoreCase(userId, normPortal)
                .orElseGet(() -> {
                    PortalSetting ps = new PortalSetting();
                    ps.setUserId(userId);
                    ps.setPortal(normPortal);
                    ps.setConsentEnabled(false);
                    ps.setApplyMode("FILL_ONLY");
                    ps.setLoginState("UNKNOWN");
                    return ps;
                });

        boolean hasDryRun = autoApplyRunRepository.existsByUserIdAndDryRunTrue(userId) || setting.isDryRunCompleted();
        setting.setDryRunCompleted(hasDryRun);

        if (loginState != null && !loginState.isBlank()) {
            setting.setLoginState(loginState.trim().toUpperCase(Locale.ROOT));
        }

        int cancelledTasks = 0;

        // Handle consent revocation -> cancels pending tasks instantly!
        if (Boolean.FALSE.equals(consentEnabled)) {
            setting.setConsentEnabled(false);
            setting.setApplyMode("FILL_ONLY");
            cancelledTasks = cancelPendingTasksForPortal(userId, normPortal, "CONSENT_REVOKED");
            revokePortalConsentRecord(userId, normPortal);
            portalSettingRepository.save(setting);

            auditService.log(userId, "PORTAL_CONSENT_REVOKED", "PortalSetting", setting.getId().toString(),
                    Map.of("portal", normPortal, "cancelledTasksCount", cancelledTasks));

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("setting", setting);
            res.put("cancelledTasksCount", cancelledTasks);
            return res;
        }

        String requestedMode = applyMode != null ? applyMode.trim().toUpperCase(Locale.ROOT) : setting.getApplyMode();

        // Enabling Mode 3 (AUTO_SUBMIT) or enabling consent with AUTO_SUBMIT
        if ("AUTO_SUBMIT".equals(requestedMode)) {
            validateCapabilityAllowsMode3(normPortal);

            if (!hasDryRun) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Mandatory dry run must be completed before enabling Mode 3 automatic submission");
            }
            if (typedConfirmation == null || !REQUIRED_TYPED_CONFIRMATION.equals(typedConfirmation.trim())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Typed confirmation must exactly match: " + REQUIRED_TYPED_CONFIRMATION);
            }
            if (!Boolean.TRUE.equals(consentEnabled) && !setting.isConsentEnabled()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Per-portal consent toggle must be enabled for Mode 3");
            }

            // First 10 applications per portal are FILL_ONLY unless explicitly overridden on mock_portal after dry run
            boolean overrideWarmup = "mock_portal".equals(normPortal) && Boolean.TRUE.equals(allowMockPortalAutoSubmitOverride);
            if (setting.getCompletedFillOnlyCount() < 10 && !overrideWarmup) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "First 10 applications on a portal must use FILL_ONLY mode (completed: " + setting.getCompletedFillOnlyCount() + "/10)");
            }

            String version = (consentVersion != null && !consentVersion.isBlank()) ? consentVersion.trim() : "v1.0-mode3";
            setting.setConsentEnabled(true);
            setting.setConsentVersion(version);
            setting.setTypedConfirmation(REQUIRED_TYPED_CONFIRMATION);
            setting.setApplyMode("AUTO_SUBMIT");

            // Enforce default low caps when enabling Mode 3
            AutoApplyPreference pref = preferenceRepository.findById(userId).orElseGet(() -> {
                AutoApplyPreference p = new AutoApplyPreference();
                p.setUserId(userId);
                return p;
            });
            if (pref.getDailyCap() > 3) pref.setDailyCap(3);
            if (pref.getWeeklyCap() > 10) pref.setWeeklyCap(10);
            preferenceRepository.save(pref);

            grantPortalConsentRecord(userId, normPortal, version);
            portalSettingRepository.save(setting);

            auditService.log(userId, "MODE3_ENABLED", "PortalSetting", setting.getId().toString(),
                    Map.of("portal", normPortal, "consentVersion", version, "dailyCap", pref.getDailyCap(), "weeklyCap", pref.getWeeklyCap()));
        } else {
            if (Boolean.TRUE.equals(consentEnabled)) {
                String version = (consentVersion != null && !consentVersion.isBlank()) ? consentVersion.trim() : "v1.0-fill-only";
                setting.setConsentEnabled(true);
                setting.setConsentVersion(version);
                if (typedConfirmation != null) setting.setTypedConfirmation(typedConfirmation.trim());
                grantPortalConsentRecord(userId, normPortal, version);
            }
            setting.setApplyMode("FILL_ONLY");
            portalSettingRepository.save(setting);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("setting", setting);
        res.put("cancelledTasksCount", cancelledTasks);
        return res;
    }

    private void validateCapabilityAllowsMode3(String normPortal) {
        if ("mock_portal".equalsIgnoreCase(normPortal)) {
            return;
        }
        Optional<SourceCapability> capOpt = sourceCapabilityRepository.findBySourceIgnoreCase(normPortal);
        if (capOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Source '" + normPortal + "' is not registered in source_capabilities");
        }
        SourceCapability cap = capOpt.get();
        if (!cap.isEnabled() || !MODE3_ALLOWED_PORTALS.contains(normPortal.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mode 3 automatic submission is blocked for '" + normPortal + "' by source capability record (Assisted/Fill-Only only)");
        }
    }

    private void grantPortalConsentRecord(UUID userId, String portal, String version) {
        String type = "PORTAL_AUTOMATION_" + portal.toUpperCase(Locale.ROOT);
        ConsentRecord record = consentRecordRepository.findByUserIdAndConsentType(userId, type)
                .orElseGet(() -> {
                    ConsentRecord cr = new ConsentRecord();
                    cr.setUserId(userId);
                    cr.setConsentType(type);
                    return cr;
                });
        record.setConsentVersion(version);
        record.setGranted(true);
        record.setGrantedAt(Instant.now());
        record.setRevokedAt(null);
        consentRecordRepository.save(record);
    }

    private void revokePortalConsentRecord(UUID userId, String portal) {
        String type = "PORTAL_AUTOMATION_" + portal.toUpperCase(Locale.ROOT);
        consentRecordRepository.findByUserIdAndConsentType(userId, type).ifPresent(cr -> {
            cr.setGranted(false);
            cr.setRevokedAt(Instant.now());
            consentRecordRepository.save(cr);
        });
    }

    private int cancelPendingTasksForPortal(UUID userId, String portal, String reason) {
        List<ApplyTask> pending = taskRepository.findByUserIdAndStatusInOrderByCreatedAtAsc(
                userId, List.of("QUEUED", "IN_PROGRESS")
        );
        int count = 0;
        for (ApplyTask task : pending) {
            if (task.getPortal().equalsIgnoreCase(portal)) {
                task.setStatus("CANCELLED");
                task.setBlockerType(reason);
                task.setBlockerMessage("Cancelled immediately upon consent revocation");
                taskRepository.save(task);
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // STAGE 28: PLAYWRIGHT APPLY-AGENT & TASK ORCHESTRATION (F14)
    // =========================================================================

    @Transactional
    public Map<String, Object> registerAgent(UUID userId, String agentName) {
        String name = (agentName == null || agentName.isBlank()) ? "Local Playwright Agent" : agentName.trim();
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String rawDeviceToken = "ssa_" + HexFormat.of().formatHex(bytes);
        String tokenHash = sha256Hex(rawDeviceToken);

        ApplyAgent agent = new ApplyAgent();
        agent.setUserId(userId);
        agent.setAgentName(name);
        agent.setDeviceTokenHash(tokenHash);
        agent.setStatus("ACTIVE");
        agent.setLastSeenAt(Instant.now());
        agent = agentRepository.save(agent);

        auditService.log(userId, "APPLY_AGENT_REGISTERED", "ApplyAgent", agent.getId().toString(),
                Map.of("agentName", name, "status", "ACTIVE"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("agent", agent);
        res.put("deviceToken", rawDeviceToken); // Returned once; only hash is stored
        return res;
    }

    @Transactional(readOnly = true)
    public List<ApplyAgent> listAgents(UUID userId) {
        return agentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public ApplyAgent revokeAgent(UUID userId, UUID agentId) {
        ApplyAgent agent = agentRepository.findByIdAndUserId(agentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        agent.setStatus("REVOKED");
        agent = agentRepository.save(agent);

        auditService.log(userId, "APPLY_AGENT_REVOKED", "ApplyAgent", agent.getId().toString(),
                Map.of("status", "REVOKED"));
        return agent;
    }

    @Transactional
    public ApplyTask enqueueTask(
            UUID userId,
            UUID jobId,
            String portal,
            String requestedMode,
            Map<String, Object> payload
    ) {
        rejectIfContainsCredentialsOrCookies(payload);

        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        AutoApplyPreference pref = preferenceRepository.findById(userId).orElseGet(() -> {
            AutoApplyPreference p = new AutoApplyPreference();
            p.setUserId(userId);
            return preferenceRepository.save(p);
        });

        if (!"ACTIVE".equalsIgnoreCase(pref.getGlobalState())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Auto-apply is currently " + pref.getGlobalState() + " (kill switch or pause active)");
        }

        // Prevent double-submit on the same job
        Optional<ApplyTask> existingActive = taskRepository.findFirstByUserIdAndJobIdAndStatusIn(
                userId, jobId, List.of("QUEUED", "IN_PROGRESS", "SUBMITTED_CONFIRMED", "FILL_ONLY_COMPLETED", "NEEDS_VERIFICATION")
        );
        if (existingActive.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Double-submit prevented: an active or completed task already exists for this job (" + existingActive.get().getStatus() + ")");
        }

        // Enforce daily & weekly caps
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant startOfWeek = Instant.now().minus(7, ChronoUnit.DAYS);
        List<ApplyTask> userTasks = taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        long todayCount = userTasks.stream()
                .filter(t -> t.getCreatedAt().isAfter(startOfToday))
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"SKIPPED_CLOSED".equals(t.getStatus()))
                .count();
        long weekCount = userTasks.stream()
                .filter(t -> t.getCreatedAt().isAfter(startOfWeek))
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"SKIPPED_CLOSED".equals(t.getStatus()))
                .count();

        if (todayCount >= pref.getDailyCap()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily cap reached (" + todayCount + "/" + pref.getDailyCap() + ")");
        }
        if (weekCount >= pref.getWeeklyCap()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Weekly cap reached (" + weekCount + "/" + pref.getWeeklyCap() + ")");
        }

        String normPortal = normalizePortal(portal != null ? portal : job.getSource());
        PortalSetting setting = portalSettingRepository.findByUserIdAndPortalIgnoreCase(userId, normPortal)
                .orElse(null);

        String effectiveMode = "FILL_ONLY";
        if ("AUTO_SUBMIT".equalsIgnoreCase(requestedMode)) {
            if (setting == null || !setting.isConsentEnabled() || !"AUTO_SUBMIT".equalsIgnoreCase(setting.getApplyMode())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "AUTO_SUBMIT is not enabled for portal '" + normPortal + "' (defaults to FILL_ONLY until Mode 3 consent & dry run are completed)");
            }
            effectiveMode = "AUTO_SUBMIT";
        }

        ApplyTask task = new ApplyTask();
        task.setUserId(userId);
        task.setJobId(jobId);
        task.setPortal(normPortal);
        task.setApplyMode(effectiveMode);
        task.setStatus("QUEUED");
        try {
            task.setPayloadJson(objectMapper.writeValueAsString(payload != null ? payload : Map.of()));
        } catch (Exception e) {
            task.setPayloadJson("{}");
        }
        return taskRepository.save(task);
    }

    @Transactional
    public Optional<ApplyTask> pullNextTask(UUID userIdFromJwt, String rawDeviceToken) {
        UUID userId = resolveUserId(userIdFromJwt, rawDeviceToken);
        UUID agentId = resolveAgentId(rawDeviceToken);

        AutoApplyPreference pref = preferenceRepository.findById(userId).orElse(null);
        if (pref != null && !"ACTIVE".equalsIgnoreCase(pref.getGlobalState())) {
            return Optional.empty();
        }

        List<ApplyTask> queued = taskRepository.findByUserIdAndStatusInOrderByCreatedAtAsc(userId, List.of("QUEUED"));
        if (queued.isEmpty()) {
            return Optional.empty();
        }

        ApplyTask next = queued.get(0);
        Map<String, Object> permit = safetyGovernorService.checkAndAcquireSourcePermit(userId, next.getPortal(), 10);
        if (!Boolean.TRUE.equals(permit.get("allowed"))) {
            return Optional.empty();
        }

        next.setStatus("IN_PROGRESS");
        if (agentId != null) {
            next.setAgentId(agentId);
        }
        return Optional.of(taskRepository.save(next));
    }

    @Transactional
    public ApplyTask reportTaskOutcome(
            UUID userIdFromJwt,
            String rawDeviceToken,
            UUID taskId,
            String outcome,
            String confirmationSignal,
            Map<String, Object> resultDetails
    ) {
        UUID userId = resolveUserId(userIdFromJwt, rawDeviceToken);
        rejectIfContainsCredentialsOrCookies(resultDetails);

        ApplyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if ("CANCELLED".equalsIgnoreCase(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task was already cancelled by kill switch or consent revocation");
        }

        String normOutcome = outcome != null ? outcome.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";

        // Enforce FILL_ONLY never submits!
        if ("FILL_ONLY".equalsIgnoreCase(task.getApplyMode()) && "CONFIRMED".equals(normOutcome)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "FILL_ONLY task must stop before final Submit and never report CONFIRMED submission");
        }

        try {
            task.setResultJson(objectMapper.writeValueAsString(resultDetails != null ? resultDetails : Map.of()));
        } catch (Exception e) {
            task.setResultJson("{}");
        }

        Job job = jobRepository.findByIdAndUserId(task.getJobId(), userId).orElse(null);

        switch (normOutcome) {
            case "FILL_ONLY", "FILL_ONLY_COMPLETED" -> {
                task.setStatus("FILL_ONLY_COMPLETED");
                task.setConfirmationSignal("STOPPED_AT_FINAL_REVIEW_FOR_USER_SUBMIT");
                incrementFillOnlyCount(userId, task.getPortal());
                upsertTrackerApplication(userId, job, task.getPortal(), "READY_TO_APPLY", "UNCONFIRMED", "ASSISTED_AGENT_FILL_ONLY");
                safetyGovernorService.reportSafetySignal(userId, task.getPortal(), "SUCCESS", "FILL_ONLY completed");
            }
            case "EXTERNAL_REDIRECT" -> {
                task.setStatus("ASSISTED_FALLBACK");
                task.setConfirmationSignal("EXTERNAL_ATS_REDIRECT");
                upsertTrackerApplication(userId, job, task.getPortal(), "READY_TO_APPLY", "UNCONFIRMED", "ASSISTED");
            }
            case "CLOSED_LISTING" -> {
                task.setStatus("SKIPPED_CLOSED");
                task.setConfirmationSignal("LISTING_CLOSED_OR_404");
                if (job != null) {
                    job.setStale(true);
                    job.setStaleReason("CLOSED_DETECTED_BY_AGENT");
                    jobRepository.save(job);
                }
            }
            case "CONFIRMED" -> {
                if (confirmationSignal == null || confirmationSignal.isBlank()) {
                    // Missing confirmation signal -> Needs Verification, NEVER assume Applied!
                    task.setStatus("NEEDS_VERIFICATION");
                    task.setConfirmationSignal("MISSING_CONFIRMATION_SIGNAL");
                    upsertTrackerApplication(userId, job, task.getPortal(), "NEEDS_VERIFICATION", "UNCONFIRMED", "AGENT_MODE3");
                    safetyGovernorService.reportSafetySignal(userId, task.getPortal(), "FAILURE", "Missing confirmation signal");
                } else {
                    task.setStatus("SUBMITTED_CONFIRMED");
                    task.setConfirmationSignal(confirmationSignal.trim());
                    upsertTrackerApplication(userId, job, task.getPortal(), "APPLIED", "EXTERNALLY_CONFIRMED", "AGENT_MODE3");
                    safetyGovernorService.reportSafetySignal(userId, task.getPortal(), "SUCCESS", "Confirmed: " + confirmationSignal);
                }
            }
            default -> {
                // NOT_CONFIRMED, UNKNOWN, TIMEOUT, HTTP_4XX, HTTP_5XX, PARTIAL -> Needs Verification, NO auto-retry!
                task.setStatus("NEEDS_VERIFICATION");
                task.setConfirmationSignal(confirmationSignal != null ? confirmationSignal : normOutcome);
                upsertTrackerApplication(userId, job, task.getPortal(), "NEEDS_VERIFICATION", "UNCONFIRMED", "AGENT_MODE3");
                safetyGovernorService.reportSafetySignal(userId, task.getPortal(), "FAILURE", "Ambiguous or failed outcome: " + normOutcome);
            }
        }

        return taskRepository.save(task);
    }

    @Transactional
    public ApplyTask reportTaskBlocker(
            UUID userIdFromJwt,
            String rawDeviceToken,
            UUID taskId,
            String blockerType,
            String blockerMessage
    ) {
        UUID userId = resolveUserId(userIdFromJwt, rawDeviceToken);
        if (blockerMessage != null) {
            String lower = blockerMessage.toLowerCase(Locale.ROOT);
            if (lower.contains("password=") || lower.contains("cookie=")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Never include passwords or cookies in blocker reports");
            }
        }

        ApplyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        String normBlocker = (blockerType == null || blockerType.isBlank()) ? "UNKNOWN_BLOCKER" : blockerType.trim().toUpperCase(Locale.ROOT);
        task.setStatus("PAUSED_BLOCKER");
        task.setBlockerType(normBlocker);
        task.setBlockerMessage(blockerMessage != null ? blockerMessage : "Paused waiting for user action");
        task = taskRepository.save(task);

        if ("CAPTCHA".equals(normBlocker) || "UNUSUAL_ACTIVITY".equals(normBlocker)) {
            safetyGovernorService.reportSafetySignal(userId, task.getPortal(), normBlocker, blockerMessage);
        }

        return task;
    }

    @Transactional(readOnly = true)
    public List<ApplyTask> listTasks(UUID userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // =========================================================================
    // EVIDENCE CRUD (USER DELETABLE)
    // =========================================================================

    @Transactional
    public ApplyEvidence addEvidence(UUID userId, UUID taskId, String evidenceType, String storagePath, String summaryNote) {
        ApplyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        ApplyEvidence ev = new ApplyEvidence();
        ev.setUserId(userId);
        ev.setTaskId(task.getId());
        ev.setEvidenceType(evidenceType != null ? evidenceType : "CONFIRMATION_SCREENSHOT");
        ev.setStoragePath(storagePath != null ? storagePath : "./uploads/evidence/" + task.getId() + ".png");
        ev.setSummaryNote(summaryNote);
        return evidenceRepository.save(ev);
    }

    @Transactional(readOnly = true)
    public List<ApplyEvidence> listEvidence(UUID userId) {
        return evidenceRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteEvidence(UUID userId, UUID evidenceId) {
        ApplyEvidence ev = evidenceRepository.findByIdAndUserId(evidenceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
        evidenceRepository.delete(ev);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void incrementFillOnlyCount(UUID userId, String portal) {
        PortalSetting ps = portalSettingRepository.findByUserIdAndPortalIgnoreCase(userId, portal)
                .orElseGet(() -> {
                    PortalSetting s = new PortalSetting();
                    s.setUserId(userId);
                    s.setPortal(portal);
                    return s;
                });
        ps.setCompletedFillOnlyCount(ps.getCompletedFillOnlyCount() + 1);
        portalSettingRepository.save(ps);
    }

    private void upsertTrackerApplication(UUID userId, Job job, String source, String status, String confirmationType, String submissionMethod) {
        if (job == null) return;
        Application app = applicationRepository.findByUserIdAndJobId(userId, job.getId())
                .orElseGet(() -> {
                    Application a = new Application();
                    a.setUserId(userId);
                    a.setJobId(job.getId());
                    return a;
                });
        app.setSource(source);
        app.setStatus(status);
        app.setConfirmationType(confirmationType);
        app.setSubmissionMethod(submissionMethod);
        if ("APPLIED".equals(status) && app.getAppliedAt() == null) {
            app.setAppliedAt(Instant.now());
        }
        applicationRepository.save(app);
    }

    private void rejectIfContainsCredentialsOrCookies(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(map).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            json = map.toString().toLowerCase(Locale.ROOT);
        }
        for (String forbidden : FORBIDDEN_CREDENTIAL_KEYS) {
            if (json.contains(forbidden)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Security violation: apply-agent payloads must never contain passwords, cookies, or session tokens");
            }
        }
    }

    private UUID resolveUserId(UUID userIdFromJwt, String rawDeviceToken) {
        if (userIdFromJwt != null) return userIdFromJwt;
        if (rawDeviceToken != null && !rawDeviceToken.isBlank()) {
            String hash = sha256Hex(rawDeviceToken.trim());
            ApplyAgent agent = agentRepository.findByDeviceTokenHashAndStatus(hash, "ACTIVE")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked agent device token"));
            agent.setLastSeenAt(Instant.now());
            agentRepository.save(agent);
            return agent.getUserId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private UUID resolveAgentId(String rawDeviceToken) {
        if (rawDeviceToken != null && !rawDeviceToken.isBlank()) {
            String hash = sha256Hex(rawDeviceToken.trim());
            return agentRepository.findByDeviceTokenHashAndStatus(hash, "ACTIVE")
                    .map(ApplyAgent::getId)
                    .orElse(null);
        }
        return null;
    }

    private String normalizePortal(String portal) {
        return (portal == null || portal.isBlank()) ? "mock_portal" : portal.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
