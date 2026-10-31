package in.skillsignal.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.agent.ApplyTask;
import in.skillsignal.agent.ApplyTaskRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.AutoApplyPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class SafetyGovernorService {

    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;
    private static final int DEFAULT_RATE_LIMIT_PER_WINDOW = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final SafetyEventRepository safetyEventRepository;
    private final SourceCircuitBreakerRepository circuitBreakerRepository;
    private final AutoApplyPreferenceRepository preferenceRepository;
    private final ApplyTaskRepository applyTaskRepository;
    private final AuditService auditLogService;
    private final ObjectMapper objectMapper;

    public SafetyGovernorService(
            SafetyEventRepository safetyEventRepository,
            SourceCircuitBreakerRepository circuitBreakerRepository,
            AutoApplyPreferenceRepository preferenceRepository,
            ApplyTaskRepository applyTaskRepository,
            AuditService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.safetyEventRepository = safetyEventRepository;
        this.circuitBreakerRepository = circuitBreakerRepository;
        this.preferenceRepository = preferenceRepository;
        this.applyTaskRepository = applyTaskRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> triggerUserKillSwitch(UUID userId, String reason) {
        AutoApplyPreference pref = getOrCreatePreferenceWithLock(userId);
        pref.setGlobalState("STOPPED");
        pref.setUpdatedAt(Instant.now());
        preferenceRepository.save(pref);

        List<ApplyTask> activeTasks = applyTaskRepository.findByUserIdAndStatusInOrderByCreatedAtAsc(
                userId, List.of("QUEUED", "IN_PROGRESS")
        );
        int haltedCount = 0;
        for (ApplyTask task : activeTasks) {
            task.setStatus("CANCELLED");
            task.setBlockerType("KILL_SWITCH");
            task.setBlockerMessage(reason != null && !reason.isBlank() ? reason : "User triggered emergency STOP kill switch");
            applyTaskRepository.save(task);
            haltedCount++;
        }

        List<SourceCircuitBreaker> breakers = circuitBreakerRepository.findByUserIdOrderBySourceAsc(userId);
        for (SourceCircuitBreaker cb : breakers) {
            cb.setState("OPEN");
            cb.setOpenedReason("USER_KILL_SWITCH");
            circuitBreakerRepository.save(cb);
        }

        SafetyEvent event = recordSafetyEvent(
                userId,
                "KILL_SWITCH",
                "ALL",
                "CRITICAL",
                "GLOBAL_STOP_AND_CANCEL_QUEUED",
                Map.of(
                        "triggeredBy", "USER",
                        "reason", reason != null ? reason : "Emergency STOP button clicked",
                        "haltedTasksCount", haltedCount
                )
        );

        auditLogService.log(userId, "SAFETY_KILL_SWITCH_USER", "auto_apply_preferences", userId.toString(),
                Map.of("globalState", "STOPPED", "haltedTasksCount", haltedCount));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("globalState", "STOPPED");
        result.put("haltedTasksCount", haltedCount);
        result.put("safetyEventId", event.getId());
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    @Transactional
    public Map<String, Object> triggerAdminKillSwitch(UUID adminUserId, String reason) {
        List<AutoApplyPreference> allPrefs = preferenceRepository.findAll();
        for (AutoApplyPreference pref : allPrefs) {
            pref.setGlobalState("STOPPED");
            pref.setUpdatedAt(Instant.now());
            preferenceRepository.save(pref);
        }

        List<ApplyTask> activeTasks = applyTaskRepository.findByStatusInOrderByCreatedAtAsc(
                List.of("QUEUED", "IN_PROGRESS")
        );
        int haltedCount = 0;
        for (ApplyTask task : activeTasks) {
            task.setStatus("CANCELLED");
            task.setBlockerType("ADMIN_KILL_SWITCH");
            task.setBlockerMessage(reason != null && !reason.isBlank() ? reason : "Admin triggered global emergency kill switch");
            applyTaskRepository.save(task);
            haltedCount++;
        }

        List<SourceCircuitBreaker> allBreakers = circuitBreakerRepository.findAll();
        for (SourceCircuitBreaker cb : allBreakers) {
            cb.setState("OPEN");
            cb.setOpenedReason("ADMIN_KILL_SWITCH");
            circuitBreakerRepository.save(cb);
        }

        SafetyEvent event = recordSafetyEvent(
                adminUserId,
                "ADMIN_KILL_SWITCH",
                "ALL",
                "CRITICAL",
                "PLATFORM_WIDE_STOP_AND_CANCEL_QUEUED",
                Map.of(
                        "triggeredBy", "ADMIN",
                        "reason", reason != null ? reason : "Admin emergency kill switch",
                        "haltedTasksCount", haltedCount,
                        "affectedUsersCount", allPrefs.size()
                )
        );

        auditLogService.log(adminUserId, "SAFETY_KILL_SWITCH_ADMIN", "safety_events", event.getId().toString(),
                Map.of("haltedTasksCount", haltedCount, "affectedUsersCount", allPrefs.size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("globalState", "STOPPED");
        result.put("haltedTasksCount", haltedCount);
        result.put("affectedUsersCount", allPrefs.size());
        result.put("safetyEventId", event.getId());
        return result;
    }

    @Transactional
    public Map<String, Object> reportSafetySignal(UUID userId, String source, String signalType, String details) {
        String normalizedSource = (source == null || source.isBlank()) ? "unknown" : source.trim().toLowerCase(Locale.ROOT);
        String normalizedSignal = (signalType == null || signalType.isBlank()) ? "FAILURE" : signalType.trim().toUpperCase(Locale.ROOT);

        SourceCircuitBreaker cb = getOrCreateCircuitBreakerWithLock(userId, normalizedSource);
        AutoApplyPreference pref = getOrCreatePreferenceWithLock(userId);

        boolean autoPaused = false;
        String actionTaken = "RECORDED";
        int haltedTasksCount = 0;

        if ("SUCCESS".equals(normalizedSignal)) {
            cb.setConsecutiveFailures(0);
            if ("HALF_OPEN".equals(cb.getState())) {
                cb.setState("CLOSED");
                cb.setOpenedReason(null);
            }
            circuitBreakerRepository.save(cb);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("source", normalizedSource);
            res.put("signalType", normalizedSignal);
            res.put("circuitBreakerState", cb.getState());
            res.put("consecutiveFailures", cb.getConsecutiveFailures());
            res.put("globalState", pref.getGlobalState());
            res.put("autoPaused", false);
            return res;
        }

        if ("FAILURE".equals(normalizedSignal)) {
            int nextFailures = cb.getConsecutiveFailures() + 1;
            cb.setConsecutiveFailures(nextFailures);
            if (nextFailures >= CONSECUTIVE_FAILURE_THRESHOLD) {
                cb.setState("OPEN");
                cb.setOpenedReason("3_CONSECUTIVE_FAILURES: " + (details != null ? details : "Repeated failure"));
                pref.setGlobalState("PAUSED");
                pref.setUpdatedAt(Instant.now());
                preferenceRepository.save(pref);
                autoPaused = true;
                haltedTasksCount = cancelQueuedTasksForUserAndSource(userId, normalizedSource, "AUTO_PAUSE_3_FAILURES");
                actionTaken = "AUTO_PAUSED_AND_CIRCUIT_OPENED";
                recordSafetyEvent(userId, "CONSECUTIVE_FAILURES", normalizedSource, "HIGH", actionTaken,
                        Map.of("consecutiveFailures", nextFailures, "details", details != null ? details : "", "haltedTasksCount", haltedTasksCount));
            } else {
                recordSafetyEvent(userId, "SUBMISSION_FAILURE", normalizedSource, "MEDIUM", "INCREMENTED_FAILURE_COUNTER",
                        Map.of("consecutiveFailures", nextFailures, "details", details != null ? details : ""));
            }
            circuitBreakerRepository.save(cb);
        } else if (Set.of("CAPTCHA", "UNUSUAL_ACTIVITY", "REJECTION_SPIKE", "TERMS_CHANGE").contains(normalizedSignal)) {
            cb.setState("OPEN");
            cb.setOpenedReason(normalizedSignal + ": " + (details != null ? details : "Safety trigger detected"));
            circuitBreakerRepository.save(cb);

            pref.setGlobalState("PAUSED");
            pref.setUpdatedAt(Instant.now());
            preferenceRepository.save(pref);
            autoPaused = true;
            haltedTasksCount = cancelQueuedTasksForUserAndSource(userId, normalizedSource, "AUTO_PAUSE_" + normalizedSignal);
            actionTaken = "AUTO_PAUSED_AND_CIRCUIT_OPENED";

            recordSafetyEvent(userId, normalizedSignal, normalizedSource, "CRITICAL", actionTaken,
                    Map.of("signalType", normalizedSignal, "details", details != null ? details : "", "haltedTasksCount", haltedTasksCount));
        } else {
            recordSafetyEvent(userId, normalizedSignal, normalizedSource, "MEDIUM", "LOGGED",
                    Map.of("details", details != null ? details : ""));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", normalizedSource);
        result.put("signalType", normalizedSignal);
        result.put("circuitBreakerState", cb.getState());
        result.put("consecutiveFailures", cb.getConsecutiveFailures());
        result.put("globalState", pref.getGlobalState());
        result.put("autoPaused", autoPaused);
        result.put("haltedTasksCount", haltedTasksCount);
        result.put("actionTaken", actionTaken);
        return result;
    }

    @Transactional
    public Map<String, Object> checkAndAcquireSourcePermit(UUID userId, String source, int maxPerWindow) {
        String normalizedSource = (source == null || source.isBlank()) ? "unknown" : source.trim().toLowerCase(Locale.ROOT);
        int limit = maxPerWindow > 0 ? maxPerWindow : DEFAULT_RATE_LIMIT_PER_WINDOW;

        AutoApplyPreference pref = getOrCreatePreferenceWithLock(userId);
        if (!"ACTIVE".equalsIgnoreCase(pref.getGlobalState())) {
            return Map.of(
                    "allowed", false,
                    "reason", "GLOBAL_STATE_" + pref.getGlobalState(),
                    "source", normalizedSource
            );
        }

        SourceCircuitBreaker cb = getOrCreateCircuitBreakerWithLock(userId, normalizedSource);
        if ("OPEN".equalsIgnoreCase(cb.getState())) {
            return Map.of(
                    "allowed", false,
                    "reason", "CIRCUIT_BREAKER_OPEN: " + (cb.getOpenedReason() != null ? cb.getOpenedReason() : ""),
                    "source", normalizedSource,
                    "circuitBreakerState", cb.getState()
            );
        }

        Instant now = Instant.now();
        if (cb.getWindowStartedAt() == null || Duration.between(cb.getWindowStartedAt(), now).compareTo(RATE_LIMIT_WINDOW) >= 0) {
            cb.setWindowStartedAt(now);
            cb.setWindowRequestCount(0);
        }

        if (cb.getWindowRequestCount() >= limit) {
            recordSafetyEvent(userId, "RATE_LIMIT_EXCEEDED", normalizedSource, "MEDIUM", "THROTTLED",
                    Map.of("windowRequestCount", cb.getWindowRequestCount(), "limit", limit));
            return Map.of(
                    "allowed", false,
                    "reason", "RATE_LIMIT_EXCEEDED",
                    "source", normalizedSource,
                    "windowRequestCount", cb.getWindowRequestCount(),
                    "limit", limit
            );
        }

        cb.setWindowRequestCount(cb.getWindowRequestCount() + 1);
        circuitBreakerRepository.save(cb);

        return Map.of(
                "allowed", true,
                "source", normalizedSource,
                "windowRequestCount", cb.getWindowRequestCount(),
                "limit", limit,
                "circuitBreakerState", cb.getState()
        );
    }

    @Transactional
    public SourceCircuitBreaker resetCircuitBreaker(UUID userId, String source) {
        String normalizedSource = (source == null || source.isBlank()) ? "unknown" : source.trim().toLowerCase(Locale.ROOT);
        SourceCircuitBreaker cb = getOrCreateCircuitBreakerWithLock(userId, normalizedSource);
        cb.setState("CLOSED");
        cb.setConsecutiveFailures(0);
        cb.setOpenedReason(null);
        cb.setWindowRequestCount(0);
        cb.setWindowStartedAt(Instant.now());
        SourceCircuitBreaker saved = circuitBreakerRepository.save(cb);

        recordSafetyEvent(userId, "CIRCUIT_BREAKER_RESET", normalizedSource, "LOW", "USER_RESET_CIRCUIT_BREAKER",
                Map.of("source", normalizedSource));
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSafetyStatus(UUID userId) {
        AutoApplyPreference pref = preferenceRepository.findById(userId).orElseGet(() -> {
            AutoApplyPreference p = new AutoApplyPreference();
            p.setUserId(userId);
            return p;
        });
        List<SourceCircuitBreaker> breakers = circuitBreakerRepository.findByUserIdOrderBySourceAsc(userId);
        List<SafetyEvent> events = safetyEventRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("globalState", pref.getGlobalState());
        status.put("dailyCap", pref.getDailyCap());
        status.put("weeklyCap", pref.getWeeklyCap());
        status.put("circuitBreakers", breakers);
        status.put("recentSafetyEvents", events.size() > 50 ? events.subList(0, 50) : events);
        return status;
    }

    public SafetyEvent recordSafetyEvent(UUID userId, String eventType, String source, String severity, String actionTaken, Map<String, Object> details) {
        SafetyEvent event = new SafetyEvent();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setSource(source);
        event.setSeverity(severity);
        event.setActionTaken(actionTaken);
        try {
            event.setDetailsJson(objectMapper.writeValueAsString(details != null ? details : Map.of()));
        } catch (Exception e) {
            event.setDetailsJson("{}");
        }
        return safetyEventRepository.save(event);
    }

    private int cancelQueuedTasksForUserAndSource(UUID userId, String source, String reason) {
        List<ApplyTask> active = applyTaskRepository.findByUserIdAndStatusInOrderByCreatedAtAsc(
                userId, List.of("QUEUED", "IN_PROGRESS")
        );
        int count = 0;
        for (ApplyTask t : active) {
            if ("ALL".equalsIgnoreCase(source) || t.getPortal().equalsIgnoreCase(source)) {
                t.setStatus("CANCELLED");
                t.setBlockerType(reason);
                t.setBlockerMessage("Cancelled automatically by Safety Governor (" + reason + ")");
                applyTaskRepository.save(t);
                count++;
            }
        }
        return count;
    }

    private AutoApplyPreference getOrCreatePreferenceWithLock(UUID userId) {
        return preferenceRepository.findWithLockByUserId(userId).orElseGet(() -> {
            AutoApplyPreference pref = new AutoApplyPreference();
            pref.setUserId(userId);
            return preferenceRepository.saveAndFlush(pref);
        });
    }

    private SourceCircuitBreaker getOrCreateCircuitBreakerWithLock(UUID userId, String source) {
        return circuitBreakerRepository.findWithLockByUserIdAndSource(userId, source).orElseGet(() -> {
            SourceCircuitBreaker cb = new SourceCircuitBreaker();
            cb.setUserId(userId);
            cb.setSource(source);
            cb.setState("CLOSED");
            cb.setConsecutiveFailures(0);
            cb.setWindowRequestCount(0);
            cb.setWindowStartedAt(Instant.now());
            return circuitBreakerRepository.saveAndFlush(cb);
        });
    }
}
