package in.skillsignal.pack;

import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications/packs")
public class ApplicationPackController {

    public record BuildPackRequest(
            List<String> questions,
            String customNoteOverride,
            Integer readinessThreshold
    ) {}

    private final ApplicationPackService packService;

    public ApplicationPackController(ApplicationPackService packService) {
        this.packService = packService;
    }

    @GetMapping("/{jobId}")
    public ApplicationPackService.ApplicationPackDto getPack(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID jobId,
            @RequestParam(required = false) Integer threshold
    ) {
        requireAuth(principal);
        return packService.getOrBuildPack(principal.id(), jobId, null, null, threshold);
    }

    @PostMapping("/{jobId}")
    public ApplicationPackService.ApplicationPackDto buildCustomPack(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID jobId,
            @RequestBody(required = false) BuildPackRequest req
    ) {
        requireAuth(principal);
        return packService.getOrBuildPack(
                principal.id(),
                jobId,
                req != null ? req.questions() : null,
                req != null ? req.customNoteOverride() : null,
                req != null ? req.readinessThreshold() : null
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

