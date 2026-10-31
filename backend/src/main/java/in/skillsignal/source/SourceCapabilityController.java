package in.skillsignal.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.AiClient;
import in.skillsignal.ai.ParsedJobData;
import in.skillsignal.audit.AuditService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/sources")
public class SourceCapabilityController {

    public record ToggleSourceRequest(@NotNull Boolean enabled) {}

    public record SyncBoardRequest(String boardToken) {}

    private final SourceCapabilityRepository capabilityRepository;
    private final GreenhousePublicBoardConnector greenhouseConnector;
    private final JobRepository jobRepository;
    private final AiClient aiClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SourceCapabilityController(
            SourceCapabilityRepository capabilityRepository,
            GreenhousePublicBoardConnector greenhouseConnector,
            JobRepository jobRepository,
            AiClient aiClient,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.capabilityRepository = capabilityRepository;
        this.greenhouseConnector = greenhouseConnector;
        this.jobRepository = jobRepository;
        this.aiClient = aiClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/capabilities")
    public List<SourceCapability> getCapabilities(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return capabilityRepository.findAllByOrderByEnabledDescSourceAsc();
    }

    @PutMapping("/capabilities/{source}/toggle")
    @Transactional
    public SourceCapability toggleCapability(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String source,
            @Valid @RequestBody ToggleSourceRequest req
    ) {
        requireAuth(principal);
        if (!"ADMIN".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN users can toggle source capabilities");
        }
        SourceCapability cap = capabilityRepository.findBySourceIgnoreCase(source.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source capability not found: " + source));

        if (Boolean.TRUE.equals(req.enabled()) && "NO_VERIFIED_API_ACCESS".equalsIgnoreCase(cap.getAccessMethod())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot enable source '" + cap.getSource() + "': no verified API access (scraping is strictly forbidden)."
            );
        }

        cap.setEnabled(req.enabled());
        cap.setTermsReviewedAt(Instant.now());
        cap = capabilityRepository.save(cap);

        auditService.log(principal.id(), "SOURCE_CAPABILITY_TOGGLED", "SourceCapability", cap.getSource(),
                Map.of("source", cap.getSource(), "enabled", cap.isEnabled()));
        return cap;
    }

    @PostMapping("/company-ats/sync")
    @Transactional
    public Map<String, Object> syncGreenhouseBoard(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) SyncBoardRequest req
    ) {
        requireAuth(principal);
        SourceCapability cap = capabilityRepository.findBySourceIgnoreCase("company_ats")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "company_ats source not configured"));
        if (!cap.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "company_ats source is currently disabled");
        }

        String boardToken = (req != null && req.boardToken() != null && !req.boardToken().isBlank())
                ? req.boardToken().trim()
                : "demo-india";

        try {
            List<GreenhousePublicBoardConnector.PublicBoardJob> boardJobs =
                    greenhouseConnector.fetchBoardJobs(boardToken, Duration.ofSeconds(5));
            List<Job> existingUserJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
            int imported = 0;

            for (GreenhousePublicBoardConnector.PublicBoardJob bj : boardJobs) {
                boolean alreadyExists = existingUserJobs.stream()
                        .anyMatch(ej -> Objects.equals(ej.getExternalJobId(), bj.externalId())
                                && "company_ats".equalsIgnoreCase(ej.getSource()));
                if (alreadyExists) continue;

                ParsedJobData parsed = aiClient.parseJobDescription(bj.rawDescription(), bj.title(), bj.company());
                Job job = new Job();
                job.setUserId(principal.id());
                job.setTitle(bj.title());
                job.setCompany(bj.company());
                job.setLocation(bj.location());
                job.setWorkMode(bj.workMode());
                job.setSource("company_ats");
                job.setExternalJobId(bj.externalId());
                job.setSourceUrl(bj.sourceUrl());
                job.setRawDescription(bj.rawDescription());
                job.setParsedJson(objectMapper.writeValueAsString(parsed));
                job.setRequiredSkillsJson(objectMapper.writeValueAsString(
                        parsed.requiredSkills() != null ? parsed.requiredSkills() : List.of()
                ));
                job.setNiceToHaveSkillsJson(objectMapper.writeValueAsString(
                        parsed.niceToHaveSkills() != null ? parsed.niceToHaveSkills() : List.of()
                ));
                job.setMinYearsExperience(parsed.minYearsExperience());
                job.setSalaryMin(bj.salaryMin());
                job.setSalaryMax(bj.salaryMax());
                job.setSalaryCurrency(bj.salaryCurrency());
                job.setSalaryPeriod(bj.salaryPeriod());
                job.setPostedAt(bj.postedAt());
                jobRepository.save(job);
                imported++;
            }

            auditService.log(principal.id(), "COMPANY_ATS_SYNCED", "SourceCapability", "company_ats",
                    Map.of("boardToken", boardToken, "importedCount", imported));

            return Map.of(
                    "source", "company_ats",
                    "boardToken", boardToken,
                    "fetchedCount", boardJobs.size(),
                    "importedCount", imported,
                    "allowedActions", cap.getAllowedActions()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync public board: " + e.getMessage());
        }
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

