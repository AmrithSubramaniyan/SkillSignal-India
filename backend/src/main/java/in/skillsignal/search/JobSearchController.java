package in.skillsignal.search;

import in.skillsignal.job.Job;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final NaturalLanguageQueryParser queryParser;
    private final JobSearchOrchestrator searchOrchestrator;
    private final JobDedupAndStaleService dedupAndStaleService;

    public JobSearchController(
            NaturalLanguageQueryParser queryParser,
            JobSearchOrchestrator searchOrchestrator,
            JobDedupAndStaleService dedupAndStaleService
    ) {
        this.queryParser = queryParser;
        this.searchOrchestrator = searchOrchestrator;
        this.dedupAndStaleService = dedupAndStaleService;
    }

    public record ParseQueryRequest(@NotBlank String query) {}
    public record VerifyLivenessRequest(Integer observedHttpStatus, Boolean explicitClosedFlag) {}
    public record SaveJobRequest(boolean saved) {}

    @PostMapping("/parse-query")
    public NaturalLanguageQueryParser.ParseQueryResult parseQuery(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ParseQueryRequest request
    ) {
        return queryParser.parse(request.query());
    }

    @PostMapping("/search")
    public JobSearchOrchestrator.JobSearchResponse searchJobs(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) JobSearchFilters filters
    ) {
        return searchOrchestrator.search(user.id(), filters != null ? filters : new JobSearchFilters());
    }

    @PostMapping("/dedup-ingest")
    public Map<String, Object> ingestWithDedup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody JobDedupAndStaleService.IngestJobInput input
    ) {
        JobDedupAndStaleService.DedupMergeResult res = dedupAndStaleService.ingestOrMergeJob(user.id(), input);
        return Map.of(
                "jobId", res.job().getId(),
                "title", res.job().getTitle(),
                "company", res.job().getCompany(),
                "location", res.job().getLocation(),
                "dedupKey", res.job().getDedupKey(),
                "mergedIntoExisting", res.mergedIntoExisting(),
                "totalSourceRecords", res.totalSourceRecords(),
                "sourceRecords", res.sourceRecords()
        );
    }

    @PostMapping("/{id}/verify-liveness")
    public JobDedupAndStaleService.StaleVerificationResult verifyLiveness(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody VerifyLivenessRequest request
    ) {
        return dedupAndStaleService.verifyJobStaleness(
                user.id(),
                id,
                request != null ? request.observedHttpStatus() : null,
                request != null ? request.explicitClosedFlag() : null
        );
    }

    @PutMapping("/{id}/save")
    public Map<String, Object> toggleSaveJob(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody SaveJobRequest request
    ) {
        Job updated = dedupAndStaleService.toggleSavedJob(user.id(), id, request.saved());
        return Map.of(
                "jobId", updated.getId(),
                "saved", updated.isSaved()
        );
    }

    @GetMapping("/saved")
    public List<UUID> listSavedJobIds(@AuthenticationPrincipal AuthenticatedUser user) {
        return dedupAndStaleService.listSavedJobs(user.id()).stream()
                .map(Job::getId)
                .toList();
    }
}
