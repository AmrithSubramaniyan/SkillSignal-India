package in.skillsignal.rule;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auto-apply")
public class AutoApplyRuleController {

    public record CreateOrUpdateRuleRequest(
            @NotBlank String ruleName,
            Integer priorityOrder,
            Boolean enabled,
            List<AutoApplyRuleAndDryRunService.RuleConditionDto> conditions,
            String tierT1Policy,
            String tierT2Policy,
            String tierT3Policy,
            String timeWindowStart,
            String timeWindowEnd,
            Integer companyCooldownDays,
            Boolean overrideCompanyCooldownIfApproved,
            Integer minSpreadMinutes
    ) {}

    public record RunRequest(
            Instant evaluationTime
    ) {}

    private final AutoApplyRuleAndDryRunService service;

    public AutoApplyRuleController(AutoApplyRuleAndDryRunService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<AutoApplyRuleAndDryRunService.AutoApplyRuleDto> listRules(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return service.listRules(principal.id());
    }

    @PostMapping("/rules")
    public ResponseEntity<AutoApplyRuleAndDryRunService.AutoApplyRuleDto> createRule(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateOrUpdateRuleRequest req
    ) {
        requireAuth(principal);
        var created = service.createRule(
                principal.id(),
                req.ruleName(),
                req.priorityOrder(),
                req.enabled(),
                req.conditions(),
                req.tierT1Policy(),
                req.tierT2Policy(),
                req.tierT3Policy(),
                req.timeWindowStart(),
                req.timeWindowEnd(),
                req.companyCooldownDays(),
                req.overrideCompanyCooldownIfApproved(),
                req.minSpreadMinutes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/rules/{id}")
    public AutoApplyRuleAndDryRunService.AutoApplyRuleDto updateRule(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody CreateOrUpdateRuleRequest req
    ) {
        requireAuth(principal);
        return service.updateRule(
                principal.id(),
                id,
                req.ruleName(),
                req.priorityOrder(),
                req.enabled(),
                req.conditions(),
                req.tierT1Policy(),
                req.tierT2Policy(),
                req.tierT3Policy(),
                req.timeWindowStart(),
                req.timeWindowEnd(),
                req.companyCooldownDays(),
                req.overrideCompanyCooldownIfApproved(),
                req.minSpreadMinutes()
        );
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, String>> deleteRule(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        service.deleteRule(principal.id(), id);
        return ResponseEntity.ok(Map.of("message", "Rule deleted"));
    }

    @PostMapping("/rules/{id}/simulate")
    public AutoApplyRuleAndDryRunService.AutoApplyRunDto simulateRule(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) RunRequest req
    ) {
        requireAuth(principal);
        return service.simulateSingleRule(
                principal.id(),
                id,
                req != null ? req.evaluationTime() : null
        );
    }

    @PostMapping("/runs")
    public AutoApplyRuleAndDryRunService.AutoApplyRunDto executeRun(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "dry_run", defaultValue = "true") boolean dryRun,
            @RequestBody(required = false) RunRequest req
    ) {
        requireAuth(principal);
        return service.executeRun(
                principal.id(),
                dryRun,
                req != null ? req.evaluationTime() : null
        );
    }

    @GetMapping("/runs/{id}")
    public AutoApplyRuleAndDryRunService.AutoApplyRunDto getRun(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.getRun(principal.id(), id);
    }

    @GetMapping("/runs")
    public List<AutoApplyRuleAndDryRunService.AutoApplyRunDto> listRuns(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        return service.listRuns(principal.id());
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

