package in.skillsignal.search;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final NaturalLanguageQueryParser queryParser;
    private final JobSearchOrchestrator searchOrchestrator;

    public JobSearchController(
            NaturalLanguageQueryParser queryParser,
            JobSearchOrchestrator searchOrchestrator
    ) {
        this.queryParser = queryParser;
        this.searchOrchestrator = searchOrchestrator;
    }

    public record ParseQueryRequest(@NotBlank String query) {}

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
}

