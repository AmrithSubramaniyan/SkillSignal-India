package in.skillsignal.learning;

import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auto-apply/centre")
public class LearningAndCentreController {

    public record ApplySuggestionRequest(Boolean consentGranted) {}

    public record SkipFeedbackRequest(
            UUID submissionId,
            UUID jobId,
            String skipReason,
            boolean agreeWithSkip,
            String userComment
    ) {}

    private final LearningAndCentreService service;

    public LearningAndCentreController(LearningAndCentreService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> getCentre(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.getAutoApplyCentre(principal.id());
    }

    @PostMapping("/suggestions/{id}/apply")
    public RuleChangeSuggestion applySuggestionWithConsent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ApplySuggestionRequest req
    ) {
        requireAuth(principal);
        return service.applyRuleSuggestionWithConsent(
                principal.id(),
                id,
                req != null ? req.consentGranted() : Boolean.FALSE
        );
    }

    @PostMapping("/suggestions/{id}/dismiss")
    public RuleChangeSuggestion dismissSuggestion(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.dismissRuleSuggestion(principal.id(), id);
    }

    @PostMapping("/skip-feedback")
    public ResponseEntity<SkipReasonFeedback> submitSkipFeedback(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody SkipFeedbackRequest req
    ) {
        requireAuth(principal);
        SkipReasonFeedback saved = service.recordSkipFeedback(
                principal.id(),
                req != null ? req.submissionId() : null,
                req != null ? req.jobId() : null,
                req != null ? req.skipReason() : "SKIPPED",
                req != null && req.agreeWithSkip(),
                req != null ? req.userComment() : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

