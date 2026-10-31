package in.skillsignal.eligibility;

import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auto-apply")
public class AutoApplyPreferenceController {

    public record AutoApplyPreferenceDto(
            UUID userId,
            int minMatchedSkills,
            boolean require50PercentSkillCoverage,
            String overQualifiedPolicy,
            String noExperienceStatedPolicy,
            int readinessThreshold,
            int dailyCap,
            int weeklyCap,
            int duplicateCooldownDays,
            String globalState,
            List<String> enabledSources,
            StrictEligibilityService.ExclusionListsDto exclusions,
            Instant updatedAt
    ) {}

    public record UpdateAutoApplyPreferenceRequest(
            Integer minMatchedSkills,
            Boolean require50PercentSkillCoverage,
            String overQualifiedPolicy,
            String noExperienceStatedPolicy,
            Integer readinessThreshold,
            Integer dailyCap,
            Integer weeklyCap,
            Integer duplicateCooldownDays,
            List<String> enabledSources,
            StrictEligibilityService.ExclusionListsDto exclusions
    ) {}

    public record GlobalStateRequest(
            String state,
            String confirmationText
    ) {}

    public record EvaluateRequest(
            List<String> questions
    ) {}

    private final StrictEligibilityService service;

    public AutoApplyPreferenceController(StrictEligibilityService service) {
        this.service = service;
    }

    @GetMapping("/preferences")
    public AutoApplyPreferenceDto getPreferences(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return toDto(service.getOrCreatePreferences(principal.id()));
    }

    @PutMapping("/preferences")
    public AutoApplyPreferenceDto updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UpdateAutoApplyPreferenceRequest req
    ) {
        requireAuth(principal);
        AutoApplyPreference updated = service.updatePreferences(
                principal.id(),
                req.minMatchedSkills(),
                req.require50PercentSkillCoverage(),
                req.overQualifiedPolicy(),
                req.noExperienceStatedPolicy(),
                req.readinessThreshold(),
                req.dailyCap(),
                req.weeklyCap(),
                req.duplicateCooldownDays(),
                req.enabledSources(),
                req.exclusions()
        );
        return toDto(updated);
    }

    @PostMapping({"/global-state", "/preferences/global-state"})
    public AutoApplyPreferenceDto updateGlobalState(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody GlobalStateRequest req
    ) {
        requireAuth(principal);
        AutoApplyPreference updated = service.updateGlobalState(
                principal.id(),
                req != null ? req.state() : null,
                req != null ? req.confirmationText() : null
        );
        return toDto(updated);
    }

    @PostMapping("/evaluate/{jobId}")
    public StrictEligibilityService.StrictEligibilityResultDto evaluateJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID jobId,
            @RequestBody(required = false) EvaluateRequest req
    ) {
        requireAuth(principal);
        return service.evaluateForUserAndJob(
                principal.id(),
                jobId,
                req != null ? req.questions() : null,
                Instant.now()
        );
    }

    private AutoApplyPreferenceDto toDto(AutoApplyPreference p) {
        return new AutoApplyPreferenceDto(
                p.getUserId(),
                p.getMinMatchedSkills(),
                p.isRequire50PercentSkillCoverage(),
                p.getOverQualifiedPolicy(),
                p.getNoExperienceStatedPolicy(),
                p.getReadinessThreshold(),
                p.getDailyCap(),
                p.getWeeklyCap(),
                p.getDuplicateCooldownDays(),
                p.getGlobalState(),
                service.parseStringList(p.getEnabledSourcesJson()),
                service.parseExclusions(p.getExclusionsJson()),
                p.getUpdatedAt()
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

