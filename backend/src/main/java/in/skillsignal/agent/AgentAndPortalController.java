package in.skillsignal.agent;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AgentAndPortalController {

    public record PortalSettingsUpdateRequest(
            Boolean consentEnabled,
            String consentVersion,
            String typedConfirmation,
            String applyMode,
            String loginState,
            Boolean allowMockPortalAutoSubmitOverride
    ) {}

    public record RegisterAgentRequest(String agentName) {}

    public record EnqueueTaskRequest(
            @NotNull UUID jobId,
            String portal,
            String applyMode,
            Map<String, Object> payload
    ) {}

    public record TaskReportRequest(
            String outcome,
            String confirmationSignal,
            Map<String, Object> resultDetails
    ) {}

    public record TaskBlockerRequest(
            String blockerType,
            String blockerMessage
    ) {}

    public record EvidenceRequest(
            String evidenceType,
            String storagePath,
            String summaryNote
    ) {}

    private final AgentAndPortalService service;

    public AgentAndPortalController(AgentAndPortalService service) {
        this.service = service;
    }

    // =========================================================================
    // PORTAL SETTINGS & MODE 3 ENABLEMENT (STAGE 27)
    // =========================================================================

    @GetMapping("/api/portals/settings")
    public List<PortalSetting> listPortalSettings(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listPortalSettings(principal.id());
    }

    @PutMapping("/api/portals/{portal}/settings")
    public Map<String, Object> updatePortalSetting(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String portal,
            @RequestBody PortalSettingsUpdateRequest req
    ) {
        requireAuth(principal);
        return service.updatePortalSetting(
                principal.id(),
                portal,
                req != null ? req.consentEnabled() : null,
                req != null ? req.consentVersion() : null,
                req != null ? req.typedConfirmation() : null,
                req != null ? req.applyMode() : null,
                req != null ? req.loginState() : null,
                req != null ? req.allowMockPortalAutoSubmitOverride() : null
        );
    }

    // =========================================================================
    // APPLY AGENTS & TASKS (STAGE 28)
    // =========================================================================

    @PostMapping("/api/agents/register")
    public ResponseEntity<Map<String, Object>> registerAgent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) RegisterAgentRequest req
    ) {
        requireAuth(principal);
        Map<String, Object> res = service.registerAgent(principal.id(), req != null ? req.agentName() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/api/agents")
    public List<ApplyAgent> listAgents(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listAgents(principal.id());
    }

    @PostMapping("/api/agents/{id}/revoke")
    public ApplyAgent revokeAgent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.revokeAgent(principal.id(), id);
    }

    @PostMapping("/api/agents/tasks")
    public ResponseEntity<ApplyTask> enqueueTask(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody EnqueueTaskRequest req
    ) {
        requireAuth(principal);
        ApplyTask task = service.enqueueTask(
                principal.id(),
                req.jobId(),
                req.portal(),
                req.applyMode(),
                req.payload()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @GetMapping("/api/agents/tasks")
    public List<ApplyTask> listTasks(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listTasks(principal.id());
    }

    @GetMapping("/api/agents/tasks/next")
    public ResponseEntity<ApplyTask> pullNextTask(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Agent-Token", required = false) String agentToken
    ) {
        UUID userId = principal != null ? principal.id() : null;
        return service.pullNextTask(userId, agentToken)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/agents/tasks/{id}/report")
    public ApplyTask reportTask(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Agent-Token", required = false) String agentToken,
            @PathVariable UUID id,
            @RequestBody TaskReportRequest req
    ) {
        UUID userId = principal != null ? principal.id() : null;
        return service.reportTaskOutcome(
                userId,
                agentToken,
                id,
                req != null ? req.outcome() : "UNKNOWN",
                req != null ? req.confirmationSignal() : null,
                req != null ? req.resultDetails() : Map.of()
        );
    }

    @PostMapping("/api/agents/tasks/{id}/blocker")
    public ApplyTask reportBlocker(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = "X-Agent-Token", required = false) String agentToken,
            @PathVariable UUID id,
            @RequestBody TaskBlockerRequest req
    ) {
        UUID userId = principal != null ? principal.id() : null;
        return service.reportTaskBlocker(
                userId,
                agentToken,
                id,
                req != null ? req.blockerType() : "UNKNOWN",
                req != null ? req.blockerMessage() : null
        );
    }

    @PostMapping("/api/agents/tasks/{id}/evidence")
    public ResponseEntity<ApplyEvidence> addEvidence(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody EvidenceRequest req
    ) {
        requireAuth(principal);
        ApplyEvidence ev = service.addEvidence(
                principal.id(),
                id,
                req != null ? req.evidenceType() : null,
                req != null ? req.storagePath() : null,
                req != null ? req.summaryNote() : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ev);
    }

    @GetMapping("/api/agents/evidence")
    public List<ApplyEvidence> listEvidence(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listEvidence(principal.id());
    }

    @DeleteMapping("/api/agents/evidence/{id}")
    public ResponseEntity<Void> deleteEvidence(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        service.deleteEvidence(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
