package in.skillsignal.submission;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/approval-queue")
public class ApprovalQueueController {

    public record QueueActionRequest(
            String note
    ) {}

    public record UpdateAnswersRequest(
            Map<String, String> answers,
            Boolean saveToAnswerLibrary
    ) {}

    public record BatchApproveRequest(
            @NotEmpty List<UUID> submissionIds,
            List<UUID> exceptionIds,
            String note
    ) {}

    public record ApproveByTokenRequest(
            @NotBlank String token
    ) {}

    private final SubmissionService submissionService;

    public ApprovalQueueController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping
    public List<SubmissionService.SubmissionDto> listApprovalQueue(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return submissionService.listApprovalQueue(principal.id());
    }

    @PostMapping("/{id}/approve")
    public SubmissionService.SubmissionDto approveItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) QueueActionRequest req
    ) {
        requireAuth(principal);
        return submissionService.approveQueueItem(
                principal.id(),
                id,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/{id}/reject")
    public SubmissionService.SubmissionDto rejectItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) QueueActionRequest req
    ) {
        requireAuth(principal);
        return submissionService.rejectQueueItem(
                principal.id(),
                id,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/{id}/skip")
    public SubmissionService.SubmissionDto skipItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) QueueActionRequest req
    ) {
        requireAuth(principal);
        return submissionService.skipQueueItem(
                principal.id(),
                id,
                req != null ? req.note() : null
        );
    }

    @PutMapping("/{id}/answers")
    public SubmissionService.SubmissionDto updateAnswers(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAnswersRequest req
    ) {
        requireAuth(principal);
        return submissionService.updateQueueItemAnswers(
                principal.id(),
                id,
                req.answers(),
                Boolean.TRUE.equals(req.saveToAnswerLibrary())
        );
    }

    @PostMapping("/batch-approve")
    public SubmissionService.BatchApproveResultDto batchApprove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BatchApproveRequest req
    ) {
        requireAuth(principal);
        return submissionService.batchApproveQueueItems(
                principal.id(),
                req.submissionIds(),
                req.exceptionIds(),
                req.note()
        );
    }

    @PostMapping("/{id}/send-approval-link")
    public SubmissionService.ApprovalLinkResponseDto sendApprovalLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return submissionService.generateAndSendApprovalLink(principal.id(), id);
    }

    @PostMapping("/approve-by-token")
    public SubmissionService.SubmissionDto approveByToken(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ApproveByTokenRequest req
    ) {
        requireAuth(principal);
        return submissionService.approveBySingleUseToken(principal.id(), req.token());
    }

    @PostMapping("/{id}/confirm-assisted-applied")
    public SubmissionService.SubmissionDto confirmAssistedApplied(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) QueueActionRequest req
    ) {
        requireAuth(principal);
        return submissionService.confirmAssistedApplied(
                principal.id(),
                id,
                req != null ? req.note() : null
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

