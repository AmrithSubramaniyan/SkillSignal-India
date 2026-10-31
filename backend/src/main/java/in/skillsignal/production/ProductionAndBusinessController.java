package in.skillsignal.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/production")
public class ProductionAndBusinessController {

    public record FeedbackSubmitRequest(
            String category,
            Integer rating,
            @NotBlank String message,
            String pageContext
    ) {}

    public record UsageEventSubmitRequest(
            @NotBlank String eventName,
            Map<String, Object> metadata
    ) {}

    private final FeatureFlagRepository featureFlagRepository;
    private final UserFeedbackRepository feedbackRepository;
    private final UsageEventRepository usageEventRepository;
    private final ObjectMapper objectMapper;

    public ProductionAndBusinessController(
            FeatureFlagRepository featureFlagRepository,
            UserFeedbackRepository feedbackRepository,
            UsageEventRepository usageEventRepository,
            ObjectMapper objectMapper
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.feedbackRepository = feedbackRepository;
        this.usageEventRepository = usageEventRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/feature-flags")
    public Map<String, Object> listFeatureFlags(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        List<FeatureFlag> flags = featureFlagRepository.findAllByOrderByFlagKeyAsc();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("paymentsEnabled", false);
        res.put("note", "Feature-flag comparison table for Free vs Paid tiers (no payment gateway active in v1.0.0-lite)");
        res.put("flags", flags);
        return res;
    }

    @PostMapping("/feedback")
    public ResponseEntity<UserFeedback> submitFeedback(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody FeedbackSubmitRequest req
    ) {
        requireAuth(principal);
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback message is required");
        }
        UserFeedback fb = new UserFeedback();
        fb.setUserId(principal.id());
        fb.setCategory(req.category() != null && !req.category().isBlank() ? req.category().trim().toUpperCase(Locale.ROOT) : "GENERAL");
        int rating = req.rating() != null ? Math.max(1, Math.min(5, req.rating())) : 5;
        fb.setRating(rating);
        fb.setMessage(req.message().trim());
        fb.setPageContext(req.pageContext());
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackRepository.save(fb));
    }

    @GetMapping("/feedback")
    public List<UserFeedback> listFeedback(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
    }

    @PostMapping("/usage-events")
    public ResponseEntity<UsageEvent> recordUsageEvent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UsageEventSubmitRequest req
    ) {
        requireAuth(principal);
        if (req == null || req.eventName() == null || req.eventName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventName is required");
        }
        UsageEvent ev = new UsageEvent();
        ev.setUserId(principal.id());
        ev.setEventName(req.eventName().trim().toUpperCase(Locale.ROOT));
        try {
            ev.setMetadataJson(objectMapper.writeValueAsString(req.metadata() != null ? req.metadata() : Map.of()));
        } catch (Exception e) {
            ev.setMetadataJson("{}");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(usageEventRepository.save(ev));
    }

    @GetMapping("/usage-events")
    public List<UsageEvent> listUsageEvents(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return usageEventRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
    }

    @GetMapping("/known-issues")
    public Map<String, Object> getKnownIssues(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        List<Map<String, String>> issues = List.of(
                Map.of(
                        "id", "KI-001",
                        "title", "Third-Party Job Portals (Naukri, LinkedIn, Indeed, Foundit, Instahyre, Wellfound)",
                        "status", "BY_DESIGN_ASSISTED_ONLY",
                        "detail", "No verified public apply APIs exist for these portals; they remain disabled for automated scraping/submission to respect Terms of Service."
                ),
                Map.of(
                        "id", "KI-002",
                        "title", "Email-Apply OAuth Live Provider Credentials (GATE G4)",
                        "status", "REQUIRES_USER_OAUTH_CLIENT_ID",
                        "detail", "Live Gmail/Outlook OAuth requires registering a Google Cloud or Microsoft Entra OAuth client ID before production use."
                ),
                Map.of(
                        "id", "KI-003",
                        "title", "DPDP Act 2023 Legal Counsel Sign-Off (GATE G7)",
                        "status", "OPEN_GATE_G7",
                        "detail", "Before public launch in India, obtain formal legal review of privacy policy, consent notices, and portal Terms of Service."
                )
        );
        return Map.of(
                "version", "v1.0.0-lite",
                "gateG7Reminder", "GATE G7 REMINDER: Obtain formal legal review (DPDP Act 2023 privacy notice, data retention, and portal terms) before public launch.",
                "knownIssues", issues
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

