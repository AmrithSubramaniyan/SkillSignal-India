package in.skillsignal.emailapply;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/email-apply")
public class EmailApplyController {

    public record ConnectMailboxRequest(
            String provider,
            @NotBlank String emailAddress,
            @NotBlank String oauthAccessToken,
            Boolean ackDetectionEnabled,
            String password
    ) {}

    public record CreateEmailDraftRequest(
            @NotNull UUID jobId,
            @NotBlank String recipientEmail,
            String customSubject,
            String customBody,
            UUID resumeVersionId
    ) {}

    public record AckDetectionRequest(
            String ackSubjectOrSnippet
    ) {}

    private final EmailApplyService service;

    public EmailApplyController(EmailApplyService service) {
        this.service = service;
    }

    @PostMapping("/mailboxes/connect")
    public ResponseEntity<EmailApplyService.ConnectedMailboxDto> connectMailbox(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ConnectMailboxRequest req
    ) {
        requireAuth(principal);
        var dto = service.connectMailboxOAuth(
                principal.id(),
                req.provider(),
                req.emailAddress(),
                req.oauthAccessToken(),
                req.ackDetectionEnabled(),
                req.password()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/mailboxes")
    public List<EmailApplyService.ConnectedMailboxDto> listMailboxes(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return service.listMailboxes(principal.id());
    }

    @PostMapping("/mailboxes/{id}/disconnect")
    public EmailApplyService.ConnectedMailboxDto disconnectMailbox(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.disconnectMailbox(principal.id(), id);
    }

    @PostMapping("/messages/draft")
    public ResponseEntity<EmailApplyService.EmailApplyMessageDto> createDraft(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateEmailDraftRequest req
    ) {
        requireAuth(principal);
        var dto = service.createDraft(
                principal.id(),
                req.jobId(),
                req.recipientEmail(),
                req.customSubject(),
                req.customBody(),
                req.resumeVersionId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/messages")
    public List<EmailApplyService.EmailApplyMessageDto> listMessages(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return service.listMessages(principal.id());
    }

    @PostMapping("/messages/{id}/approve")
    public EmailApplyService.EmailApplyMessageDto approveDraft(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.approveDraft(principal.id(), id);
    }

    @PostMapping("/messages/{id}/send")
    public EmailApplyService.EmailApplyMessageDto sendEmail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.sendApprovedEmail(principal.id(), id);
    }

    @PostMapping("/messages/{id}/detect-ack")
    public EmailApplyService.EmailApplyMessageDto detectAck(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) AckDetectionRequest req
    ) {
        requireAuth(principal);
        return service.detectAcknowledgement(
                principal.id(),
                id,
                req != null ? req.ackSubjectOrSnippet() : null
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

