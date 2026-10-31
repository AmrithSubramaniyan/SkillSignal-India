package in.skillsignal.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.audit.AuditService;
import in.skillsignal.consent.ConsentRecord;
import in.skillsignal.consent.ConsentRecordRepository;
import in.skillsignal.reminder.UserNotificationPreference;
import in.skillsignal.reminder.UserNotificationPreferenceRepository;
import in.skillsignal.security.JwtService;
import in.skillsignal.security.MailSenderService;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class SessionCheckoutEmailService {

    private static final Logger log = LoggerFactory.getLogger(SessionCheckoutEmailService.class);

    public static final Set<String> VALID_CHECKOUT_REASONS = Set.of(
            "LOGOUT", "BROWSER_CLOSE", "IDLE_TIMEOUT", "TOKEN_EXPIRED"
    );

    private static final DateTimeFormatter EMAIL_TIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z", Locale.ENGLISH);

    public record CheckInResultDto(
            UUID sessionId,
            String beaconToken,
            Instant checkedInAt,
            String timeZone
    ) {}

    public record UserSessionDto(
            UUID id,
            UUID userId,
            Instant checkedInAt,
            Instant lastSeenAt,
            Instant checkedOutAt,
            String checkoutReason,
            String timeZone,
            String summaryEmailStatus,
            long companiesAppliedCount,
            Instant createdAt
    ) {}

    private final UserSessionRepository sessionRepository;
    private final OutboxEventRepository outboxRepository;
    private final SessionAppliedItemRepository appliedItemRepository;
    private final SessionSummaryEmailRepository summaryEmailRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final ConsentRecordRepository consentRepository;
    private final UserRepository userRepository;
    private final MailSenderService mailSenderService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final KafkaTopicsConfig kafkaTopicsConfig;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    // Per-session heartbeat rate limiter (sliding window: max 20 heartbeats per 60s)
    private final Map<UUID, List<Instant>> heartbeatWindows = new ConcurrentHashMap<>();

    private final long idleTimeoutMinutes;
    private final long beaconGraceSeconds;
    private volatile long checkoutGraceMs;
    private final int maxSummaryEmailsPerDay;
    private final int maxEmailRows;
    private final int outboxRetentionDays;
    private final String frontendUrl;

    public SessionCheckoutEmailService(
            UserSessionRepository sessionRepository,
            OutboxEventRepository outboxRepository,
            SessionAppliedItemRepository appliedItemRepository,
            SessionSummaryEmailRepository summaryEmailRepository,
            UserNotificationPreferenceRepository preferenceRepository,
            ConsentRecordRepository consentRepository,
            UserRepository userRepository,
            MailSenderService mailSenderService,
            JwtService jwtService,
            AuditService auditService,
            KafkaTopicsConfig kafkaTopicsConfig,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.session.idle-timeout-minutes:15}") long idleTimeoutMinutes,
            @Value("${app.session.beacon-grace-seconds:30}") long beaconGraceSeconds,
            @Value("${app.session.checkout-grace-ms:30000}") long checkoutGraceMs,
            @Value("${app.session.max-summary-emails-per-day:20}") int maxSummaryEmailsPerDay,
            @Value("${app.session.max-email-rows:50}") int maxEmailRows,
            @Value("${app.session.outbox-retention-days:7}") int outboxRetentionDays,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.sessionRepository = sessionRepository;
        this.outboxRepository = outboxRepository;
        this.appliedItemRepository = appliedItemRepository;
        this.summaryEmailRepository = summaryEmailRepository;
        this.preferenceRepository = preferenceRepository;
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
        this.mailSenderService = mailSenderService;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.kafkaTopicsConfig = kafkaTopicsConfig;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.idleTimeoutMinutes = idleTimeoutMinutes;
        this.beaconGraceSeconds = beaconGraceSeconds;
        this.checkoutGraceMs = checkoutGraceMs;
        this.maxSummaryEmailsPerDay = maxSummaryEmailsPerDay;
        this.maxEmailRows = maxEmailRows;
        this.outboxRetentionDays = outboxRetentionDays;
        this.frontendUrl = frontendUrl;
    }

    public void setCheckoutGraceMs(long checkoutGraceMs) {
        this.checkoutGraceMs = checkoutGraceMs;
    }

    public long getCheckoutGraceMs() {
        return checkoutGraceMs;
    }

    // =========================================================================
    // STAGE S7-2: CHECK-IN, HEARTBEAT, CHECK-OUT, BEACON & IDLE TIMEOUT
    // =========================================================================

    @Transactional
    public CheckInResultDto checkIn(UUID userId) {
        UserNotificationPreference pref = getOrCreatePreference(userId);
        byte[] tokenBytes = new byte[24];
        secureRandom.nextBytes(tokenBytes);
        String rawBeaconToken = "sbn_" + HexFormat.of().formatHex(tokenBytes);
        String tokenHash = sha256Hex(rawBeaconToken);

        Instant now = Instant.now();
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setCheckedInAt(now);
        session.setLastSeenAt(now);
        session.setBeaconTokenHash(tokenHash);
        session.setTimeZone(pref.getTimeZone() != null ? pref.getTimeZone() : "Asia/Kolkata");
        session.setSummaryEmailStatus("NONE");
        session = sessionRepository.saveAndFlush(session);

        SessionContextHolder.setSessionId(session.getId());

        return new CheckInResultDto(
                session.getId(),
                rawBeaconToken,
                session.getCheckedInAt(),
                session.getTimeZone()
        );
    }

    @Transactional
    public UserSessionDto heartbeat(UUID userId, UUID sessionId) {
        UserSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        enforceHeartbeatRateLimit(sessionId);

        Instant now = Instant.now();
        session.setLastSeenAt(now);

        // 30-second beacon grace period: if a beacon arrived due to page reload, heartbeat cancels the pending checkout!
        if (session.getBeaconReceivedAt() != null && session.getCheckedOutAt() == null) {
            long elapsedSec = Duration.between(session.getBeaconReceivedAt(), now).getSeconds();
            if (elapsedSec <= beaconGraceSeconds) {
                session.setBeaconReceivedAt(null);
                session.setCheckoutReason(null);
                log.info("[SESSION] Heartbeat within {}s grace window cancelled pending beacon checkout for sessionId={}",
                        beaconGraceSeconds, sessionId);
            }
        }

        session = sessionRepository.saveAndFlush(session);
        return toSessionDto(session);
    }

    /**
     * Explicit check-out (logout button or token expiry).
     * Idempotent: checking out twice returns the already closed session without emitting a second event.
     * Ownership enforced: returns 404 if session belongs to another user.
     */
    @Transactional
    public UserSessionDto checkOut(UUID userId, UUID sessionId, String requestedReason) {
        UserSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getCheckedOutAt() != null) {
            // Already checked out -> idempotent no-op
            return toSessionDto(session);
        }

        String reason = normalizeCheckoutReason(requestedReason, "LOGOUT");
        Instant now = Instant.now();
        finalizeCheckoutInTransaction(session, now, reason);
        return toSessionDto(session);
    }

    /**
     * Best-effort check-out triggered by {@code navigator.sendBeacon} on tab/browser close.
     * Authenticates using the session-bound {@code beaconToken} issued at check-in.
     * Uses a 30-second grace window so a page reload followed by a heartbeat cancels the checkout.
     */
    @Transactional
    public Map<String, Object> receiveCheckoutBeacon(UUID sessionId, String rawBeaconToken, boolean immediateAfterGraceForTest) {
        if (rawBeaconToken == null || rawBeaconToken.isBlank()) {
            return Map.of("accepted", false, "reason", "Missing beacon token");
        }
        Optional<UserSession> opt = sessionRepository.findByIdForUpdate(sessionId);
        if (opt.isEmpty()) {
            return Map.of("accepted", false, "reason", "Unknown session");
        }
        UserSession session = opt.get();
        String expectedHash = session.getBeaconTokenHash();
        String providedHash = sha256Hex(rawBeaconToken.trim());
        if (!Objects.equals(expectedHash, providedHash)) {
            // Ignore anything that does not match the session's beacon token
            return Map.of("accepted", false, "reason", "Invalid beacon token");
        }
        if (session.getCheckedOutAt() != null) {
            return Map.of("accepted", true, "status", "ALREADY_CHECKED_OUT");
        }

        Instant now = Instant.now();
        session.setBeaconReceivedAt(now);
        sessionRepository.saveAndFlush(session);

        if (immediateAfterGraceForTest) {
            finalizeCheckoutInTransaction(session, now, "BROWSER_CLOSE");
            return Map.of("accepted", true, "status", "CHECKED_OUT_BROWSER_CLOSE");
        }

        return Map.of(
                "accepted", true,
                "status", "BEACON_GRACE_PERIOD_STARTED",
                "graceSeconds", beaconGraceSeconds
        );
    }

    /**
     * Scheduled job (runs every 60 seconds, and callable directly in tests) that:
     * 1. Closes sessions whose pending beacon grace window (30s) has elapsed without a cancelling heartbeat.
     * 2. Closes sessions with no heartbeat for {@code idleTimeoutMinutes} (15m default), setting
     *    {@code checkoutReason = "IDLE_TIMEOUT"} and {@code checkedOutAt = lastSeenAt}.
     */
    @Scheduled(fixedDelayString = "${app.session.idle-check-interval-ms:60000}", initialDelayString = "${app.session.idle-check-interval-ms:60000}")
    @Transactional
    public int closeExpiredIdleAndBeaconSessions() {
        return closeExpiredIdleAndBeaconSessionsAt(Instant.now());
    }

    @Transactional
    public int closeExpiredIdleAndBeaconSessionsAt(Instant evaluationTime) {
        Instant now = evaluationTime != null ? evaluationTime : Instant.now();
        Instant idleCutoff = now.minus(idleTimeoutMinutes, ChronoUnit.MINUTES);
        Instant beaconCutoff = now.minus(beaconGraceSeconds, ChronoUnit.SECONDS);

        List<UserSession> openSessions = sessionRepository.findByCheckedOutAtIsNull();
        int closedCount = 0;

        for (UserSession raw : openSessions) {
            Optional<UserSession> lockedOpt = sessionRepository.findByIdForUpdate(raw.getId());
            if (lockedOpt.isEmpty()) continue;
            UserSession session = lockedOpt.get();
            if (session.getCheckedOutAt() != null) continue;

            if (session.getBeaconReceivedAt() != null && !session.getBeaconReceivedAt().isAfter(beaconCutoff)) {
                finalizeCheckoutInTransaction(session, session.getBeaconReceivedAt(), "BROWSER_CLOSE");
                closedCount++;
            } else if (!session.getLastSeenAt().isAfter(idleCutoff)) {
                finalizeCheckoutInTransaction(session, session.getLastSeenAt(), "IDLE_TIMEOUT");
                closedCount++;
            }
        }
        return closedCount;
    }

    @Transactional(readOnly = true)
    public List<UserSessionDto> listUserSessions(UUID userId) {
        return sessionRepository.findByUserIdOrderByCheckedInAtDesc(userId).stream()
                .map(this::toSessionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserSessionDto getUserSession(UUID userId, UUID sessionId) {
        UserSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return toSessionDto(session);
    }

    private void finalizeCheckoutInTransaction(UserSession session, Instant checkedOutAt, String reason) {
        if (session.getCheckedOutAt() != null) {
            return;
        }
        UUID checkoutEventId = UUID.randomUUID();
        session.setCheckedOutAt(checkedOutAt);
        session.setCheckoutReason(reason);
        session.setCheckoutEventId(checkoutEventId);
        sessionRepository.saveAndFlush(session);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId().toString());
        payload.put("checkedInAt", session.getCheckedInAt().toString());
        payload.put("checkedOutAt", checkedOutAt.toString());
        payload.put("checkoutReason", reason);
        payload.put("timeZone", session.getTimeZone());

        enqueueOutboxEventInTransaction(
                checkoutEventId,
                session.getUserId(),
                session.getId(),
                KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT,
                KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT,
                checkedOutAt,
                payload
        );
    }

    // =========================================================================
    // STAGE S7-3: OUTBOX WRITER & RELAY (NO LOST EVENTS)
    // =========================================================================

    /**
     * Called inside the SAME database transaction whenever an application transitions to a
     * qualifying status (Rule 6):
     * - APPLIED (USER_CONFIRMED, method "Applied on company page")
     * - SENT (UNCONFIRMED, method "Sent by email")
     * - SUBMITTED_CONFIRMED (EXTERNALLY_CONFIRMED, method "Submitted by agent")
     *
     * Never called for NEEDS_VERIFICATION, FAILED, SUBMISSION_FAILED, INTERESTED, SAVED, or drafts.
     */
    @Transactional
    public OutboxEvent recordQualifyingApplicationApplied(
            UUID userId,
            UUID applicationId,
            String company,
            String jobTitle,
            Instant appliedAt,
            String method,
            String confirmationType,
            UUID explicitSessionId,
            boolean forceWhileAway
    ) {
        UUID resolvedSessionId = null;
        if (!forceWhileAway) {
            if (explicitSessionId != null) {
                resolvedSessionId = explicitSessionId;
            } else {
                UUID contextSessionId = SessionContextHolder.getSessionId();
                if (contextSessionId != null) {
                    Optional<UserSession> ctxSession = sessionRepository.findByIdAndUserId(contextSessionId, userId);
                    if (ctxSession.isPresent()) {
                        resolvedSessionId = ctxSession.get().getId();
                    }
                }
                if (resolvedSessionId == null) {
                    resolvedSessionId = sessionRepository
                            .findFirstByUserIdAndCheckedOutAtIsNullOrderByCheckedInAtDesc(userId)
                            .map(UserSession::getId)
                            .orElse(null);
                }
            }
        }

        Instant timestamp = appliedAt != null ? appliedAt : Instant.now();
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", applicationId.toString());
        payload.put("company", company != null ? company : "Unknown Company");
        payload.put("jobTitle", jobTitle != null ? jobTitle : "Open Role");
        payload.put("appliedAt", timestamp.toString());
        payload.put("method", method != null ? method : "Applied on company page");
        payload.put("confirmationType", confirmationType != null ? confirmationType : "USER_CONFIRMED");
        payload.put("sessionId", resolvedSessionId != null ? resolvedSessionId.toString() : null);

        return enqueueOutboxEventInTransaction(
                eventId,
                userId,
                resolvedSessionId,
                KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED,
                KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED,
                timestamp,
                payload
        );
    }

    @Transactional
    public OutboxEvent enqueueOutboxEventInTransaction(
            UUID eventId,
            UUID userId,
            UUID sessionId,
            String topic,
            String eventType,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        SkillSignalEventEnvelope envelope = SkillSignalEventEnvelope.create(
                eventId,
                eventType,
                occurredAt,
                userId,
                sessionId,
                payload
        );
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event envelope", e);
        }

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(envelope.eventId());
        outbox.setUserId(userId);
        outbox.setTopic(topic);
        outbox.setEventKey(userId.toString()); // Message key = userId so events of one user stay ordered
        outbox.setEventType(eventType);
        outbox.setPayloadJson(json);
        outbox.setAttempts(0);
        return outboxRepository.saveAndFlush(outbox);
    }

    /**
     * Scheduled outbox relay (every few seconds, row-locked with PESSIMISTIC_WRITE / FOR UPDATE)
     * that publishes unpublished rows to Kafka, marks {@code published_at}, retries with backoff
     * when Kafka is down, and never loses events.
     */
    @Scheduled(fixedDelayString = "${app.session.outbox-relay-interval-ms:3000}", initialDelayString = "${app.session.outbox-relay-interval-ms:3000}")
    public synchronized int relayPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findUnpublishedForRelay();
        if (pending.isEmpty()) {
            return 0;
        }
        int publishedCount = 0;
        for (OutboxEvent event : pending) {
            if (kafkaTopicsConfig.isKafkaDownSimulated()) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError("Kafka broker unavailable (outbox buffering active)");
                outboxRepository.saveAndFlush(event);
                continue;
            }
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayloadJson())
                        .get(5, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                outboxRepository.saveAndFlush(event);
                publishedCount++;
            } catch (Exception ex) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                outboxRepository.saveAndFlush(event);
                log.warn("[OUTBOX-RELAY] Deferred eventId={} topic={} attempts={} reason={}",
                        event.getEventId(), event.getTopic(), event.getAttempts(), ex.getClass().getSimpleName());
            }
        }
        return publishedCount;
    }

    /**
     * Cleanup job removing published outbox rows older than 7 days.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public int cleanupOldPublishedOutboxEvents() {
        Instant cutoff = Instant.now().minus(outboxRetentionDays, ChronoUnit.DAYS);
        return outboxRepository.deletePublishedBefore(cutoff);
    }

    // =========================================================================
    // STAGE S7-4: KAFKA CONSUMER HANDLERS & SESSION SUMMARY BUILDER
    // =========================================================================

    @Transactional
    public void handleApplicationAppliedMessage(String rawJson) {
        SkillSignalEventEnvelope env = parseAndValidateEnvelope(rawJson, KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED);
        Map<String, Object> payload = env.payload();
        if (payload == null || payload.get("applicationId") == null) {
            throw new IllegalArgumentException("Missing required payload.applicationId in application.applied event");
        }

        UUID eventId = env.eventId();
        UUID userId = env.userId();
        UUID applicationId = UUID.fromString(payload.get("applicationId").toString());

        // Idempotent consumer: ignore duplicate eventId or duplicate applicationId
        if (appliedItemRepository.existsByEventId(eventId)) {
            log.info("[CONSUMER] Ignoring duplicate application.applied delivery for eventId={}", eventId);
            return;
        }
        Optional<SessionAppliedItem> existingByApp = appliedItemRepository.findByApplicationId(applicationId);
        if (existingByApp.isPresent()) {
            SessionAppliedItem existing = existingByApp.get();
            String newConf = payload.get("confirmationType") != null
                    ? payload.get("confirmationType").toString()
                    : existing.getConfirmationType();
            if ("EXTERNALLY_CONFIRMED".equals(newConf) && !"EXTERNALLY_CONFIRMED".equals(existing.getConfirmationType())) {
                existing.setConfirmationType("EXTERNALLY_CONFIRMED");
                appliedItemRepository.saveAndFlush(existing);
            }
            return;
        }

        UUID targetSessionId = env.sessionId();
        if (targetSessionId == null && payload.get("sessionId") != null) {
            targetSessionId = UUID.fromString(payload.get("sessionId").toString());
        }

        boolean whileAway = (targetSessionId == null);

        // Edge case (Stage S7-4): applied event arrives AFTER summary email was already sent for that session!
        if (targetSessionId != null) {
            Optional<UserSession> targetSessionOpt = sessionRepository.findById(targetSessionId);
            if (targetSessionOpt.isPresent()) {
                UserSession targetSession = targetSessionOpt.get();
                if ("SENT".equals(targetSession.getSummaryEmailStatus())
                        || summaryEmailRepository.existsBySessionId(targetSessionId)) {
                    // Never send a second email for the same session!
                    // Attach to the user's current open session if they are in one now; otherwise ignore & log.
                    Optional<UserSession> newerOpen = sessionRepository
                            .findFirstByUserIdAndCheckedOutAtIsNullOrderByCheckedInAtDesc(userId);
                    if (newerOpen.isPresent() && !newerOpen.get().getId().equals(targetSessionId)) {
                        targetSessionId = newerOpen.get().getId();
                        whileAway = true;
                        log.info("[CONSUMER] Late application.applied after session summary already sent; attached as late item to newer open session={}",
                                targetSessionId);
                    } else {
                        log.info("[CONSUMER] Late application.applied arrived after summary already sent for sessionId={}; logged and ignored (no second email)",
                                targetSessionId);
                        return;
                    }
                }
            }
        }

        Instant appliedAt = payload.get("appliedAt") != null
                ? Instant.parse(payload.get("appliedAt").toString())
                : Instant.now();

        SessionAppliedItem item = new SessionAppliedItem();
        item.setSessionId(targetSessionId);
        item.setUserId(userId);
        item.setApplicationId(applicationId);
        item.setCompany(payload.getOrDefault("company", "Unknown Company").toString());
        item.setJobTitle(payload.getOrDefault("jobTitle", "Open Role").toString());
        item.setAppliedAt(appliedAt);
        item.setMethod(payload.getOrDefault("method", "Applied on company page").toString());
        item.setConfirmationType(payload.getOrDefault("confirmationType", "USER_CONFIRMED").toString());
        item.setWhileAway(whileAway);
        item.setEventId(eventId);
        appliedItemRepository.saveAndFlush(item);
    }

    @Transactional
    public void handleSessionCheckedOutMessage(String rawJson) {
        SkillSignalEventEnvelope env = parseAndValidateEnvelope(rawJson, KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT);
        UUID sessionId = env.sessionId();
        if (sessionId == null && env.payload() != null && env.payload().get("sessionId") != null) {
            sessionId = UUID.fromString(env.payload().get("sessionId").toString());
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("Missing sessionId in session.checked-out event");
        }

        // Wait configurable grace period (default 30s in prod, 50ms in tests) for late application.applied events
        if (checkoutGraceMs > 0) {
            try {
                Thread.sleep(Math.min(checkoutGraceMs, 5000L));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        evaluateSessionSummaryAndRequestEmail(sessionId);
    }

    @Transactional
    public void evaluateSessionSummaryAndRequestEmail(UUID sessionId) {
        Optional<UserSession> sessionOpt = sessionRepository.findByIdForUpdate(sessionId);
        if (sessionOpt.isEmpty()) {
            return;
        }
        UserSession session = sessionOpt.get();

        // Idempotent guard: if already SENT or already has a summary email row, never re-trigger
        if ("SENT".equals(session.getSummaryEmailStatus()) || summaryEmailRepository.existsBySessionId(sessionId)) {
            return;
        }

        UUID userId = session.getUserId();
        UserNotificationPreference pref = getOrCreatePreference(userId);

        // Bind any unassigned "while you were away" items (sessionId IS NULL) that occurred
        // after the previous session's check-out and before or at this session's check-out
        if (pref.isIncludeWhileAwayApplications()) {
            Instant prevCheckout = findPreviousSessionCheckoutTime(userId, session);
            Instant thisCheckout = session.getCheckedOutAt() != null ? session.getCheckedOutAt() : Instant.now();
            List<SessionAppliedItem> unassigned = appliedItemRepository.findByUserIdAndSessionIdIsNullOrderByAppliedAtAsc(userId);
            for (SessionAppliedItem item : unassigned) {
                boolean afterPrev = (prevCheckout == null) || !item.getAppliedAt().isBefore(prevCheckout);
                boolean beforeThis = !item.getAppliedAt().isAfter(thisCheckout.plusSeconds(5));
                if (afterPrev && beforeThis) {
                    item.setSessionId(sessionId);
                    item.setWhileAway(true);
                    appliedItemRepository.saveAndFlush(item);
                }
            }
        }

        List<SessionAppliedItem> sessionItems = appliedItemRepository.findBySessionIdOrderByAppliedAtAsc(sessionId);

        // Rule 3: Zero applications = NO email, status = SKIPPED_NO_APPLICATIONS
        if (sessionItems.isEmpty()) {
            session.setSummaryEmailStatus("SKIPPED_NO_APPLICATIONS");
            sessionRepository.saveAndFlush(session);
            return;
        }

        // Rule 5: Check email notification consent and session_summary_email_enabled setting
        if (!hasEmailSummaryConsentAndEnabled(userId, pref)) {
            session.setSummaryEmailStatus("SKIPPED_USER_OPTOUT");
            sessionRepository.saveAndFlush(session);
            return;
        }

        session.setSummaryEmailStatus("PENDING");
        sessionRepository.saveAndFlush(session);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId().toString());
        payload.put("checkedInAt", session.getCheckedInAt().toString());
        payload.put("checkedOutAt", (session.getCheckedOutAt() != null ? session.getCheckedOutAt() : Instant.now()).toString());
        payload.put("checkoutReason", session.getCheckoutReason() != null ? session.getCheckoutReason() : "LOGOUT");
        payload.put("timeZone", session.getTimeZone());
        payload.put("totalCompaniesApplied", sessionItems.size());

        enqueueOutboxEventInTransaction(
                UUID.randomUUID(),
                userId,
                session.getId(),
                KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED,
                KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED,
                Instant.now(),
                payload
        );
    }

    // =========================================================================
    // STAGE S7-5: SUMMARY EMAIL RENDERER, RATE LIMIT, IDEMPOTENCY & UNSUBSCRIBE
    // =========================================================================

    @Transactional
    public void handleSummaryEmailRequestedMessage(String rawJson) {
        SkillSignalEventEnvelope env = parseAndValidateEnvelope(rawJson, KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED);
        UUID sessionId = env.sessionId();
        if (sessionId == null && env.payload() != null && env.payload().get("sessionId") != null) {
            sessionId = UUID.fromString(env.payload().get("sessionId").toString());
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("Missing sessionId in session.summary-email.requested event");
        }
        renderAndSendSummaryEmailForSession(sessionId);
    }

    @Transactional
    public Optional<SessionSummaryEmail> renderAndSendSummaryEmailForSession(UUID sessionId) {
        Optional<UserSession> sessionOpt = sessionRepository.findByIdForUpdate(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        UserSession session = sessionOpt.get();

        // Unique session_id guarantees at most ONE email per session even if redelivered
        Optional<SessionSummaryEmail> existingEmail = summaryEmailRepository.findBySessionId(sessionId);
        if (existingEmail.isPresent()) {
            session.setSummaryEmailStatus("SENT");
            sessionRepository.saveAndFlush(session);
            return existingEmail;
        }

        UUID userId = session.getUserId();
        UserNotificationPreference pref = getOrCreatePreference(userId);
        if (!hasEmailSummaryConsentAndEnabled(userId, pref)) {
            session.setSummaryEmailStatus("SKIPPED_USER_OPTOUT");
            sessionRepository.saveAndFlush(session);
            return Optional.empty();
        }

        List<SessionAppliedItem> items = appliedItemRepository.findBySessionIdOrderByAppliedAtAsc(sessionId);
        if (items.isEmpty()) {
            session.setSummaryEmailStatus("SKIPPED_NO_APPLICATIONS");
            sessionRepository.saveAndFlush(session);
            return Optional.empty();
        }

        // Safety: Rate limit max 20 summary emails per user per day
        long sentLast24h = summaryEmailRepository.countByUserIdAndSentAtAfter(
                userId, Instant.now().minus(24, ChronoUnit.HOURS)
        );
        if (sentLast24h >= maxSummaryEmailsPerDay) {
            session.setSummaryEmailStatus("FAILED");
            sessionRepository.saveAndFlush(session);
            log.warn("[SESSION-EMAIL] Daily rate limit ({}) reached for userId={}; skipping session={}",
                    maxSummaryEmailsPerDay, userId, sessionId);
            return Optional.empty();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found for session summary email"));

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(session.getTimeZone() != null ? session.getTimeZone() : "Asia/Kolkata");
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Kolkata");
        }

        int totalCount = items.size();
        String subject = (totalCount == 1)
                ? "Your SkillSignal session: you applied to 1 company"
                : ("Your SkillSignal session: you applied to " + totalCount + " companies");

        String unsubscribeToken = issueUnsubscribeToken(userId, pref);
        String unsubscribeUrl = frontendUrl + "/api/sessions/summary-email/unsubscribe?token=" + unsubscribeToken;
        String applicationsUrl = frontendUrl + "/tracker";
        String settingsUrl = frontendUrl + "/notifications";

        String checkInFormatted = formatInZone(session.getCheckedInAt(), zoneId);
        String checkOutFormatted = formatInZone(
                session.getCheckedOutAt() != null ? session.getCheckedOutAt() : Instant.now(),
                zoneId
        );

        List<SessionAppliedItem> visibleRows = totalCount > maxEmailRows
                ? items.subList(0, maxEmailRows)
                : items;
        int overflowCount = Math.max(0, totalCount - maxEmailRows);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escapeHtml(subject)).append("</title></head>")
                .append("<body style=\"font-family: Arial, sans-serif; color: #1f2937; line-height: 1.5; max-width: 680px; margin: 0 auto; padding: 16px;\">")
                .append("<h2 style=\"color: #111827;\">").append(escapeHtml(subject)).append("</h2>")
                .append("<p>Hello ").append(escapeHtml(user.getFullName() != null ? user.getFullName() : "there")).append(",</p>")
                .append("<p>Here is the summary of your SkillSignal session (<strong>")
                .append(escapeHtml(checkInFormatted)).append("</strong> to <strong>")
                .append(escapeHtml(checkOutFormatted)).append("</strong>, time zone: ")
                .append(escapeHtml(zoneId.getId())).append("):</p>")
                .append("<table style=\"width: 100%; border-collapse: collapse; margin: 16px 0;\">")
                .append("<thead><tr style=\"background-color: #f3f4f6; text-align: left;\">")
                .append("<th style=\"padding: 8px; border: 1px solid #d1d5db;\">Company</th>")
                .append("<th style=\"padding: 8px; border: 1px solid #d1d5db;\">Job title</th>")
                .append("<th style=\"padding: 8px; border: 1px solid #d1d5db;\">Time</th>")
                .append("<th style=\"padding: 8px; border: 1px solid #d1d5db;\">Method</th>")
                .append("<th style=\"padding: 8px; border: 1px solid #d1d5db;\">Status</th>")
                .append("</tr></thead><tbody>");

        StringBuilder text = new StringBuilder();
        text.append(subject).append("\n\n")
                .append("Hello ").append(user.getFullName() != null ? user.getFullName() : "there").append(",\n")
                .append("Session time range (").append(zoneId.getId()).append("): ")
                .append(checkInFormatted).append(" to ").append(checkOutFormatted).append("\n\n")
                .append("Companies applied during this session:\n");

        for (SessionAppliedItem item : visibleRows) {
            String timeStr = formatInZone(item.getAppliedAt(), zoneId);
            String methodLabel = formatMethodLabel(item.getMethod(), item.isWhileAway());
            String statusLabel = formatTruthfulStatusLabel(item.getConfirmationType(), item.getMethod());

            html.append("<tr>")
                    .append("<td style=\"padding: 8px; border: 1px solid #d1d5db;\">").append(escapeHtml(item.getCompany())).append("</td>")
                    .append("<td style=\"padding: 8px; border: 1px solid #d1d5db;\">").append(escapeHtml(item.getJobTitle())).append("</td>")
                    .append("<td style=\"padding: 8px; border: 1px solid #d1d5db;\">").append(escapeHtml(timeStr)).append("</td>")
                    .append("<td style=\"padding: 8px; border: 1px solid #d1d5db;\">").append(escapeHtml(methodLabel)).append("</td>")
                    .append("<td style=\"padding: 8px; border: 1px solid #d1d5db;\">").append(escapeHtml(statusLabel)).append("</td>")
                    .append("</tr>");

            text.append("- ").append(item.getCompany())
                    .append(" | ").append(item.getJobTitle())
                    .append(" | ").append(timeStr)
                    .append(" | ").append(methodLabel)
                    .append(" | ").append(statusLabel).append("\n");
        }

        html.append("</tbody></table>");

        if (overflowCount > 0) {
            html.append("<p><em>...and ").append(overflowCount).append(" more.</em></p>");
            text.append("...and ").append(overflowCount).append(" more.\n");
        }

        String footerDisclaimer = "Statuses show what SkillSignal could confirm. Check each company's portal or inbox for the final result.";
        String howToTurnOff = "How to turn this email off: Open Notification Settings in SkillSignal and turn off \"Email me a summary of companies I applied to after each session\", or use the one-click unsubscribe link below.";

        html.append("<p style=\"margin-top: 16px; font-size: 13px; color: #374151;\">")
                .append(escapeHtml(footerDisclaimer)).append("</p>")
                .append("<p style=\"font-size: 13px;\">")
                .append("<a href=\"").append(escapeHtml(applicationsUrl)).append("\">View My Applications</a> | ")
                .append("<a href=\"").append(escapeHtml(settingsUrl)).append("\">Notification Settings</a></p>")
                .append("<hr style=\"border: none; border-top: 1px solid #e5e7eb; margin: 16px 0;\"/>")
                .append("<p style=\"font-size: 12px; color: #6b7280;\">").append(escapeHtml(howToTurnOff)).append("<br/>")
                .append("<a href=\"").append(escapeHtml(unsubscribeUrl)).append("\">Unsubscribe from session summary emails</a></p>")
                .append("</body></html>");

        text.append("\n").append(footerDisclaimer).append("\n")
                .append("My Applications: ").append(applicationsUrl).append("\n")
                .append("Notification Settings: ").append(settingsUrl).append("\n")
                .append(howToTurnOff).append("\n")
                .append("Unsubscribe: ").append(unsubscribeUrl).append("\n");

        // Send only to the account's verified email address
        String providerMessageId = mailSenderService.sendSessionSummaryEmail(
                user.getEmail(),
                subject,
                html.toString(),
                text.toString()
        );

        SessionSummaryEmail emailRecord = new SessionSummaryEmail();
        emailRecord.setSessionId(sessionId);
        emailRecord.setUserId(userId);
        emailRecord.setSentAt(Instant.now());
        emailRecord.setProviderMessageId(providerMessageId);
        emailRecord.setStatus("SENT");
        emailRecord.setSubject(subject);
        emailRecord.setBodyHtml(html.toString());
        emailRecord.setBodyText(text.toString());
        emailRecord = summaryEmailRepository.saveAndFlush(emailRecord);

        session.setSummaryEmailStatus("SENT");
        sessionRepository.saveAndFlush(session);

        return Optional.of(emailRecord);
    }

    /**
     * Unsubscribes a user from session summary emails using a signed, single-purpose token without requiring login.
     */
    @Transactional
    public Map<String, Object> unsubscribeByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsubscribe token is required");
        }
        UUID userId;
        try {
            Claims claims = jwtService.parseToken(rawToken.trim());
            String purpose = claims.get("purpose", String.class);
            if (!"SESSION_SUMMARY_UNSUBSCRIBE".equals(purpose)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token purpose");
            }
            userId = UUID.fromString(claims.getSubject());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            String tokenHash = sha256Hex(rawToken.trim());
            UserNotificationPreference byHash = preferenceRepository.findByUnsubscribeTokenHash(tokenHash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired unsubscribe token"));
            userId = byHash.getUserId();
        }

        UserNotificationPreference pref = getOrCreatePreference(userId);
        pref.setSessionSummaryEmailEnabled(false);
        pref.setUpdatedAt(Instant.now());
        preferenceRepository.saveAndFlush(pref);

        auditService.log(userId, "SESSION_SUMMARY_EMAIL_UNSUBSCRIBED", "UserNotificationPreference", userId.toString(),
                Map.of("sessionSummaryEmailEnabled", false));

        return Map.of(
                "unsubscribed", true,
                "sessionSummaryEmailEnabled", false,
                "message", "You have been unsubscribed from session check-out summary emails."
        );
    }

    /**
     * Manual resend endpoint for operators/users if a summary email previously failed (`RUNBOOK.md`).
     */
    @Transactional
    public Optional<SessionSummaryEmail> resendSummaryEmail(UUID userId, UUID sessionId) {
        UserSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return renderAndSendSummaryEmailForSession(session.getId());
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private boolean hasEmailSummaryConsentAndEnabled(UUID userId, UserNotificationPreference pref) {
        if (!pref.isSessionSummaryEmailEnabled()) {
            return false;
        }
        Optional<ConsentRecord> emailNotifConsent = consentRepository.findByUserIdAndConsentType(userId, "EMAIL_NOTIFICATIONS");
        if (emailNotifConsent.isPresent() && emailNotifConsent.get().isGranted()) {
            return true;
        }
        Optional<ConsentRecord> emailSendConsent = consentRepository.findByUserIdAndConsentType(userId, "ENABLE_EMAIL_SENDING");
        return emailSendConsent.isPresent() && emailSendConsent.get().isGranted();
    }

    private UserNotificationPreference getOrCreatePreference(UUID userId) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            UserNotificationPreference pref = new UserNotificationPreference();
            pref.setUserId(userId);
            pref.setTimeZone("Asia/Kolkata");
            pref.setQuietHoursStart("22:00");
            pref.setQuietHoursEnd("08:00");
            pref.setDailySummaryEnabled(true);
            pref.setSessionSummaryEmailEnabled(false);
            pref.setIncludeWhileAwayApplications(true);
            pref.setUpdatedAt(Instant.now());
            return preferenceRepository.saveAndFlush(pref);
        });
    }

    private Instant findPreviousSessionCheckoutTime(UUID userId, UserSession currentSession) {
        return sessionRepository.findByUserIdOrderByCheckedInAtDesc(userId).stream()
                .filter(s -> !s.getId().equals(currentSession.getId()))
                .filter(s -> s.getCheckedOutAt() != null)
                .filter(s -> !s.getCheckedOutAt().isAfter(currentSession.getCheckedInAt()))
                .map(UserSession::getCheckedOutAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String issueUnsubscribeToken(UUID userId, UserNotificationPreference pref) {
        String signedJwt = jwtService.generateUnsubscribeToken(userId);
        pref.setUnsubscribeTokenHash(sha256Hex(signedJwt));
        preferenceRepository.saveAndFlush(pref);
        return signedJwt;
    }

    public static String formatMethodLabel(String rawMethod, boolean whileAway) {
        String base;
        if (rawMethod == null || rawMethod.isBlank()) {
            base = "Applied on company page";
        } else {
            String upper = rawMethod.trim().toUpperCase(Locale.ROOT);
            base = switch (upper) {
                case "EMAIL_APPLY", "SENT BY EMAIL", "EMAIL" -> "Sent by email";
                case "AGENT_MODE3", "SUBMITTED BY AGENT", "AUTO_SUBMIT", "AGENT" -> "Submitted by agent";
                case "ASSISTED", "MANUAL", "APPLIED ON COMPANY PAGE" -> "Applied on company page";
                default -> rawMethod.trim();
            };
        }
        return whileAway ? (base + " (while you were away)") : base;
    }

    public static String formatTruthfulStatusLabel(String confirmationType, String method) {
        if (confirmationType == null) {
            return "Sent, not yet confirmed";
        }
        return switch (confirmationType.trim().toUpperCase(Locale.ROOT)) {
            case "USER_CONFIRMED" -> "User confirmed";
            case "EXTERNALLY_CONFIRMED" -> "Externally confirmed";
            default -> {
                if (method != null && method.toLowerCase(Locale.ROOT).contains("email")) {
                    yield "Sent, not yet confirmed";
                }
                yield "Sent, not yet confirmed";
            }
        };
    }

    public static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatInZone(Instant instant, ZoneId zoneId) {
        Instant ts = instant != null ? instant : Instant.now();
        return ZonedDateTime.ofInstant(ts, zoneId).format(EMAIL_TIME_FMT);
    }

    private SkillSignalEventEnvelope parseAndValidateEnvelope(String rawJson, String expectedTopic) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("Empty Kafka message payload on " + expectedTopic);
        }
        try {
            Map<String, Object> root = objectMapper.readValue(rawJson, new TypeReference<>() {});
            if (root.get("eventId") == null || root.get("userId") == null || root.get("eventType") == null) {
                throw new IllegalArgumentException("Missing required envelope fields (eventId, userId, eventType)");
            }
            int schemaVersion = root.get("schemaVersion") instanceof Number n ? n.intValue() : 1;
            UUID eventId = UUID.fromString(root.get("eventId").toString());
            String eventType = root.get("eventType").toString();
            String occurredAt = root.get("occurredAt") != null ? root.get("occurredAt").toString() : Instant.now().toString();
            UUID userId = UUID.fromString(root.get("userId").toString());
            UUID sessionId = root.get("sessionId") != null ? UUID.fromString(root.get("sessionId").toString()) : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = root.get("payload") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m
                    : Map.of();
            return new SkillSignalEventEnvelope(schemaVersion, eventId, eventType, occurredAt, userId, sessionId, payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed or poison event message on " + expectedTopic + ": " + e.getClass().getSimpleName(), e);
        }
    }

    private void enforceHeartbeatRateLimit(UUID sessionId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(60);
        List<Instant> timestamps = heartbeatWindows.computeIfAbsent(sessionId, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> t.isBefore(windowStart));
            if (timestamps.size() >= 20) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Heartbeat rate limit exceeded");
            }
            timestamps.add(now);
        }
    }

    private String normalizeCheckoutReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) return fallback;
        String upper = reason.trim().toUpperCase(Locale.ROOT);
        return VALID_CHECKOUT_REASONS.contains(upper) ? upper : fallback;
    }

    private UserSessionDto toSessionDto(UserSession s) {
        long count = appliedItemRepository.countBySessionId(s.getId());
        return new UserSessionDto(
                s.getId(),
                s.getUserId(),
                s.getCheckedInAt(),
                s.getLastSeenAt(),
                s.getCheckedOutAt(),
                s.getCheckoutReason(),
                s.getTimeZone(),
                s.getSummaryEmailStatus(),
                count,
                s.getCreatedAt()
        );
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

