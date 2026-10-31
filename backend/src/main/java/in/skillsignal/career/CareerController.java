package in.skillsignal.career;

import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/career")
public class CareerController {

    public record GenerateRoadmapRequest(List<String> missingSkills) {}

    public record ToggleRoadmapRequest(Boolean completed) {}

    public record MockInterviewRequest(
            UUID jobId,
            String topic,
            String question,
            String userAnswer
    ) {}

    private final CareerService service;

    public CareerController(CareerService service) {
        this.service = service;
    }

    @GetMapping("/roadmap")
    public Map<String, Object> getRoadmap(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.getRoadmap(principal.id());
    }

    @PostMapping("/roadmap/generate")
    public List<SkillRoadmapItem> generateRoadmap(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) GenerateRoadmapRequest req
    ) {
        requireAuth(principal);
        return service.generateWeeklyRoadmapFromMissingSkills(
                principal.id(),
                req != null ? req.missingSkills() : null
        );
    }

    @PutMapping("/roadmap/{id}/toggle")
    public SkillRoadmapItem toggleRoadmap(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ToggleRoadmapRequest req
    ) {
        requireAuth(principal);
        return service.toggleRoadmapCheckbox(principal.id(), id, req != null ? req.completed() : null);
    }

    @GetMapping("/interview-prep/topics")
    public Map<String, Object> getTopics(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String customTopic
    ) {
        requireAuth(principal);
        return service.getTopicSets(customTopic);
    }

    @PostMapping("/interview-prep/from-job/{jobId}")
    public Map<String, Object> prepFromJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID jobId
    ) {
        requireAuth(principal);
        return service.generatePrepFromJob(principal.id(), jobId);
    }

    @PostMapping("/mock-interview")
    public ResponseEntity<Map<String, Object>> submitMockInterview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody MockInterviewRequest req
    ) {
        requireAuth(principal);
        Map<String, Object> res = service.evaluateTextMockInterview(
                principal.id(),
                req != null ? req.jobId() : null,
                req != null ? req.topic() : "Java",
                req != null ? req.question() : null,
                req != null ? req.userAnswer() : ""
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/mock-interview/sessions")
    public Map<String, Object> getMockSessions(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.getWeakTopicsAndSessions(principal.id());
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

