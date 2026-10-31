package in.skillsignal.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class UserSessionController {

    public record CheckoutRequest(String reason) {}

    private final SessionCheckoutEmailService sessionService;
    private final SessionSummaryEmailRepository summaryEmailRepository;
    private final ObjectMapper objectMapper;

    public UserSessionController(
            SessionCheckoutEmailService sessionService,
            SessionSummaryEmailRepository summaryEmailRepository,
            ObjectMapper objectMapper
    ) {
        this.sessionService = sessionService;
        this.summaryEmailRepository = summaryEmailRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<SessionCheckoutEmailService.UserSessionDto> listSessions(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return sessionService.listUserSessions(principal.id());
    }

    @GetMapping("/{id}")
    public SessionCheckoutEmailService.UserSessionDto getSession(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return sessionService.getUserSession(principal.id(), id);
    }

    @PostMapping("/{id}/heartbeat")
    public SessionCheckoutEmailService.UserSessionDto heartbeat(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return sessionService.heartbeat(principal.id(), id);
    }

    @PostMapping("/{id}/checkout")
    public SessionCheckoutEmailService.UserSessionDto checkout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) CheckoutRequest req
    ) {
        requireAuth(principal);
        String reason = req != null ? req.reason() : "LOGOUT";
        return sessionService.checkOut(principal.id(), id, reason);
    }

    /**
     * Accepts {@code navigator.sendBeacon} payloads on pagehide / visibilitychange / beforeunload.
     * Authenticated via the session-bound {@code beaconToken} issued at check-in.
     */
    @PostMapping("/{id}/checkout-beacon")
    public ResponseEntity<Map<String, Object>> checkoutBeacon(
            @PathVariable UUID id,
            @RequestParam(required = false) String beaconToken,
            @RequestBody(required = false) String rawBody
    ) {
        String resolvedToken = beaconToken;
        boolean immediate = false;
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                Map<?, ?> parsed = objectMapper.readValue(rawBody, Map.class);
                if (resolvedToken == null && parsed.get("beaconToken") != null) {
                    resolvedToken = parsed.get("beaconToken").toString();
                }
                if (Boolean.TRUE.equals(parsed.get("immediate"))) {
                    immediate = true;
                }
            } catch (Exception ignored) {
                if (resolvedToken == null) {
                    resolvedToken = rawBody.trim();
                }
            }
        }
        Map<String, Object> result = sessionService.receiveCheckoutBeacon(id, resolvedToken, immediate);
        return ResponseEntity.accepted().body(result);
    }

    @RequestMapping(value = "/summary-email/unsubscribe", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> unsubscribeSummaryEmail(@RequestParam String token) {
        return sessionService.unsubscribeByToken(token);
    }

    @PostMapping("/{id}/resend-summary")
    public ResponseEntity<Map<String, Object>> resendSummary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        var sentOpt = sessionService.resendSummaryEmail(principal.id(), id);
        if (sentOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("resent", false, "reason", "No qualifying applications or user opted out"));
        }
        return ResponseEntity.ok(Map.of("resent", true, "emailId", sentOpt.get().getId()));
    }

    @GetMapping("/summary-emails")
    public List<SessionSummaryEmail> listSummaryEmails(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return summaryEmailRepository.findByUserIdOrderBySentAtDesc(principal.id());
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

