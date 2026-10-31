package in.skillsignal.submission;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    public record PrepareSubmissionRequest(
            @NotNull UUID jobId,
            String idempotencyKey,
            List<String> questions,
            Instant expiresAtOverride
    ) {}

    public record TransitionSubmissionRequest(
            @NotBlank String targetState,
            String providerResponse,
            String actor
    ) {}

    public record ResolveNeedsVerificationRequest(
            @NotBlank String resolution, // SUBMITTED_CONFIRMED or FAILED
            String note
    ) {}

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/prepare")
    public SubmissionService.SubmissionDto prepareSubmission(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody PrepareSubmissionRequest req
    ) {
        requireAuth(principal);
        return submissionService.prepareSubmission(
                principal.id(),
                req.jobId(),
                req.idempotencyKey(),
                req.questions(),
                req.expiresAtOverride()
        );
    }

    @GetMapping
    public List<SubmissionService.SubmissionDto> listSubmissions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String state
    ) {
        requireAuth(principal);
        return submissionService.listSubmissions(principal.id(), state);
    }

    @GetMapping("/{id}")
    public SubmissionService.SubmissionDto getSubmission(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return submissionService.getSubmission(principal.id(), id);
    }

    @GetMapping("/{id}/attempts")
    public List<SubmissionService.SubmissionAttemptDto> listAttempts(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return submissionService.listAttempts(principal.id(), id);
    }

    @GetMapping("/{id}/why")
    public Map<String, Object> getWhyExplanation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return submissionService.getWhyExplanation(principal.id(), id);
    }

    @PostMapping("/{id}/transition")
    public SubmissionService.SubmissionDto transitionSubmission(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody TransitionSubmissionRequest req
    ) {
        requireAuth(principal);
        return submissionService.transitionSubmission(
                principal.id(),
                id,
                req.targetState(),
                req.providerResponse(),
                req.actor() != null ? req.actor() : "USER",
                false
        );
    }

    @PostMapping("/{id}/resolve")
    public SubmissionService.SubmissionDto resolveNeedsVerification(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveNeedsVerificationRequest req
    ) {
        requireAuth(principal);
        return submissionService.resolveNeedsVerification(
                principal.id(),
                id,
                req.resolution(),
                req.note()
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

