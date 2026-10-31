package in.skillsignal.safety;

import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/safety")
public class SafetyGovernorController {

    public record KillSwitchRequest(String reason) {}

    public record SafetySignalRequest(String source, String signalType, String details) {}

    public record RateLimitCheckRequest(String source, Integer maxPerWindow) {}

    private final SafetyGovernorService service;

    public SafetyGovernorController(SafetyGovernorService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.getSafetyStatus(principal.id());
    }

    @PostMapping("/kill-switch")
    public Map<String, Object> triggerUserKillSwitch(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) KillSwitchRequest req
    ) {
        requireAuth(principal);
        return service.triggerUserKillSwitch(principal.id(), req != null ? req.reason() : null);
    }

    @PostMapping("/admin/kill-switch")
    public Map<String, Object> triggerAdminKillSwitch(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) KillSwitchRequest req
    ) {
        requireAuth(principal);
        return service.triggerAdminKillSwitch(principal.id(), req != null ? req.reason() : null);
    }

    @PostMapping("/report-signal")
    public Map<String, Object> reportSignal(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody SafetySignalRequest req
    ) {
        requireAuth(principal);
        return service.reportSafetySignal(
                principal.id(),
                req != null ? req.source() : "unknown",
                req != null ? req.signalType() : "FAILURE",
                req != null ? req.details() : null
        );
    }

    @PostMapping("/check-rate-limit")
    public Map<String, Object> checkRateLimit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody RateLimitCheckRequest req
    ) {
        requireAuth(principal);
        return service.checkAndAcquireSourcePermit(
                principal.id(),
                req != null ? req.source() : "unknown",
                req != null && req.maxPerWindow() != null ? req.maxPerWindow() : 5
        );
    }

    @PostMapping("/circuit-breakers/{source}/reset")
    public SourceCircuitBreaker resetCircuitBreaker(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String source
    ) {
        requireAuth(principal);
        return service.resetCircuitBreaker(principal.id(), source);
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

