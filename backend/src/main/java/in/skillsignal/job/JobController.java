package in.skillsignal.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.AiClient;
import in.skillsignal.ai.ParsedJobData;
import in.skillsignal.audit.AuditService;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.match.MatchResultDto;
import in.skillsignal.match.MatchScoringService;
import in.skillsignal.profile.CareerProfile;
import in.skillsignal.profile.CareerProfileRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    public record CreateJobRequest(
            String title,
            String company,
            String location,
            String workMode,
            String sourceUrl,
            @NotBlank String rawDescription,
            List<String> requiredSkills,
            List<String> niceToHaveSkills,
            Double minYearsExperience
    ) {}

    public record ComputeMatchRequest(UUID resumeVersionId) {}

    public record JobDto(
            UUID id,
            String title,
            String company,
            String location,
            String workMode,
            String sourceUrl,
            String rawDescription,
            List<String> requiredSkills,
            List<String> niceToHaveSkills,
            Double minYearsExperience,
            Integer latestMatchScore,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final CareerProfileRepository profileRepository;
    private final AiClient aiClient;
    private final MatchScoringService matchScoringService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public JobController(
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            ResumeVersionRepository resumeVersionRepository,
            CareerProfileRepository profileRepository,
            AiClient aiClient,
            MatchScoringService matchScoringService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.profileRepository = profileRepository;
        this.aiClient = aiClient;
        this.matchScoringService = matchScoringService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<JobDto> createJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateJobRequest req
    ) {
        requireAuth(principal);
        ParsedJobData parsed = aiClient.parseJobDescription(req.rawDescription(), req.title(), req.company());

        String finalTitle = (req.title() != null && !req.title().isBlank()) ? req.title().trim() : parsed.title();
        String finalCompany = (req.company() != null && !req.company().isBlank()) ? req.company().trim() : parsed.company();
        String finalLocation = (req.location() != null && !req.location().isBlank()) ? req.location().trim() : parsed.location();
        String finalWorkMode = (req.workMode() != null && !req.workMode().isBlank()) ? req.workMode().trim().toUpperCase() : parsed.workMode();
        List<String> finalReqSkills = (req.requiredSkills() != null && !req.requiredSkills().isEmpty())
                ? req.requiredSkills()
                : (parsed.requiredSkills() != null ? parsed.requiredSkills() : List.of());
        List<String> finalNiceSkills = (req.niceToHaveSkills() != null && !req.niceToHaveSkills().isEmpty())
                ? req.niceToHaveSkills()
                : (parsed.niceToHaveSkills() != null ? parsed.niceToHaveSkills() : List.of());
        Double finalMinYears = req.minYearsExperience() != null ? req.minYearsExperience() : parsed.minYearsExperience();

        Job job = new Job();
        job.setUserId(principal.id());
        job.setTitle(finalTitle);
        job.setCompany(finalCompany);
        job.setLocation(finalLocation);
        job.setWorkMode(finalWorkMode);
        job.setSourceUrl(req.sourceUrl());
        job.setRawDescription(req.rawDescription().trim());
        job.setParsedJson(writeJson(parsed, "{}"));
        job.setRequiredSkillsJson(writeJson(finalReqSkills, "[]"));
        job.setNiceToHaveSkillsJson(writeJson(finalNiceSkills, "[]"));
        job.setMinYearsExperience(finalMinYears);
        job = jobRepository.save(job);

        auditService.log(principal.id(), "JOB_ADDED", "Job", job.getId().toString(), Map.of("title", job.getTitle(), "company", job.getCompany()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(job, null));
    }

    @GetMapping
    public List<JobDto> listJobs(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
                .map(job -> {
                    Integer latestScore = jobMatchRepository
                            .findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), principal.id())
                            .map(JobMatch::getTotalScore)
                            .orElse(null);
                    return toDto(job, latestScore);
                })
                .toList();
    }

    @GetMapping("/{id}")
    public JobDto getJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        Job job = jobRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        Integer latestScore = jobMatchRepository
                .findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), principal.id())
                .map(JobMatch::getTotalScore)
                .orElse(null);
        return toDto(job, latestScore);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        Job job = jobRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        jobRepository.delete(job);
        auditService.log(principal.id(), "JOB_DELETED", "Job", id.toString(), Map.of());
        return ResponseEntity.ok(Map.of("message", "Job deleted"));
    }

    @PostMapping("/{id}/match")
    @Transactional
    public MatchResultDto computeMatch(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ComputeMatchRequest req
    ) {
        requireAuth(principal);
        Job job = jobRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        ResumeVersion resumeVersion;
        if (req != null && req.resumeVersionId() != null) {
            resumeVersion = resumeVersionRepository.findByIdAndUserId(req.resumeVersionId(), principal.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));
        } else {
            resumeVersion = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(principal.id())
                    .or(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream().findFirst())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Please upload a resume and verify your skills before computing a job match score."
                    ));
        }

        Optional<CareerProfile> profileOpt = profileRepository.findByUserId(principal.id());
        List<String> preferredCities = profileOpt.map(p -> readList(p.getCities())).orElse(List.of());
        List<String> preferredWorkModes = profileOpt.map(p -> readList(p.getWorkModes())).orElse(List.of());
        Double resumeYears = resumeVersion.getYearsOfExperience();
        if (resumeYears == null && profileOpt.isPresent()) {
            resumeYears = profileOpt.get().getYearsOfExperience();
        }

        MatchResultDto computed = matchScoringService.computeMatch(
                job.getId(),
                resumeVersion.getId(),
                readList(resumeVersion.getVerifiedSkillsJson()),
                resumeYears,
                readList(job.getRequiredSkillsJson()),
                readList(job.getNiceToHaveSkillsJson()),
                job.getMinYearsExperience(),
                job.getLocation(),
                job.getWorkMode(),
                preferredCities,
                preferredWorkModes
        );

        JobMatch entity = jobMatchRepository
                .findFirstByJobIdAndResumeVersionIdAndUserId(job.getId(), resumeVersion.getId(), principal.id())
                .orElseGet(JobMatch::new);
        entity.setUserId(principal.id());
        entity.setJobId(job.getId());
        entity.setResumeVersionId(resumeVersion.getId());
        entity.setTotalScore(computed.score());
        entity.setRequiredSkillsScore(computed.requiredSkillsScore());
        entity.setNiceToHaveScore(computed.niceToHaveScore());
        entity.setExperienceScore(computed.experienceScore());
        entity.setLocationScore(computed.locationScore());
        entity.setResultJson(writeJson(computed, "{}"));
        entity = jobMatchRepository.save(entity);

        return new MatchResultDto(
                entity.getId(),
                computed.jobId(),
                computed.resumeVersionId(),
                computed.score(),
                computed.requiredSkillsScore(),
                computed.niceToHaveScore(),
                computed.experienceScore(),
                computed.locationScore(),
                computed.matchedRequired(),
                computed.missingRequired(),
                computed.matchedNiceToHave(),
                computed.missingNiceToHave(),
                computed.experienceFit(),
                computed.locationFit(),
                computed.suggestions(),
                computed.disclaimer(),
                entity.getUpdatedAt()
        );
    }

    @GetMapping("/{id}/match")
    public MatchResultDto getLatestMatch(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        Job job = jobRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        JobMatch match = jobMatchRepository.findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No match computed yet for this job"));

        try {
            MatchResultDto stored = objectMapper.readValue(match.getResultJson(), MatchResultDto.class);
            return new MatchResultDto(
                    match.getId(),
                    stored.jobId(),
                    stored.resumeVersionId(),
                    stored.score(),
                    stored.requiredSkillsScore(),
                    stored.niceToHaveScore(),
                    stored.experienceScore(),
                    stored.locationScore(),
                    stored.matchedRequired(),
                    stored.missingRequired(),
                    stored.matchedNiceToHave(),
                    stored.missingNiceToHave(),
                    stored.experienceFit(),
                    stored.locationFit(),
                    stored.suggestions(),
                    stored.disclaimer(),
                    match.getUpdatedAt()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize match result");
        }
    }

    private JobDto toDto(Job j, Integer latestScore) {
        return new JobDto(
                j.getId(),
                j.getTitle(),
                j.getCompany(),
                j.getLocation(),
                j.getWorkMode(),
                j.getSourceUrl(),
                j.getRawDescription(),
                readList(j.getRequiredSkillsJson()),
                readList(j.getNiceToHaveSkillsJson()),
                j.getMinYearsExperience(),
                latestScore,
                j.getCreatedAt(),
                j.getUpdatedAt()
        );
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object val, String fallback) {
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

