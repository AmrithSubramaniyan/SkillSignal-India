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
import java.util.*;

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
            Double minYearsExperience,
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            String requiredEducation
    ) {}

    public record ComputeMatchRequest(UUID resumeVersionId) {}

    public record MatchFeedbackRequest(
            @NotBlank String feedback,
            String note
    ) {}

    public record CompareJobsRequest(List<UUID> jobIds) {}

    public record MissingSkillSummaryItem(
            String skill,
            int jobCount,
            List<String> jobTitles
    ) {}

    public record JobComparisonItem(
            UUID jobId,
            String title,
            String company,
            String location,
            String workMode,
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            Double minYearsExperience,
            String requiredEducation,
            List<String> requiredSkills,
            MatchResultDto match
    ) {}

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
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            String requiredEducation,
            boolean saved,
            boolean stale,
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
        job.setSalaryMin(req.salaryMin());
        job.setSalaryMax(req.salaryMax());
        job.setSalaryCurrency(req.salaryCurrency() != null ? req.salaryCurrency() : "INR");
        job.setSalaryPeriod(req.salaryPeriod() != null ? req.salaryPeriod() : "LPA");
        job.setRequiredEducation(req.requiredEducation());
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

    @GetMapping("/missing-skills-summary")
    public List<MissingSkillSummaryItem> getMissingSkillsAcrossSavedJobs(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        requireAuth(principal);
        Optional<ResumeVersion> rvOpt = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(principal.id())
                .or(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream().findFirst());
        Set<String> candidateSkillsLower = new HashSet<>();
        if (rvOpt.isPresent()) {
            for (String s : readList(rvOpt.get().getVerifiedSkillsJson())) {
                candidateSkillsLower.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<Job> savedJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
                .filter(Job::isSaved)
                .toList();

        Map<String, List<String>> skillToJobTitles = new LinkedHashMap<>();
        for (Job job : savedJobs) {
            for (String req : readList(job.getRequiredSkillsJson())) {
                if (req == null || req.isBlank()) continue;
                if (!candidateSkillsLower.contains(req.trim().toLowerCase(Locale.ROOT))) {
                    skillToJobTitles.computeIfAbsent(req.trim(), k -> new ArrayList<>())
                            .add(job.getTitle() + " @ " + job.getCompany());
                }
            }
        }

        return skillToJobTitles.entrySet().stream()
                .map(e -> new MissingSkillSummaryItem(e.getKey(), e.getValue().size(), e.getValue()))
                .sorted(Comparator.comparingInt(MissingSkillSummaryItem::jobCount).reversed())
                .toList();
    }

    @PostMapping("/compare")
    @Transactional
    public List<JobComparisonItem> compareJobs(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody CompareJobsRequest req
    ) {
        requireAuth(principal);
        if (req == null || req.jobIds() == null || req.jobIds().size() < 2 || req.jobIds().size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please select 2 or 3 jobs to compare.");
        }

        List<JobComparisonItem> results = new ArrayList<>();
        for (UUID jobId : req.jobIds()) {
            Job job = jobRepository.findByIdAndUserId(jobId, principal.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
            MatchResultDto match = computeMatchForJob(principal.id(), job, null);
            results.add(new JobComparisonItem(
                    job.getId(),
                    job.getTitle(),
                    job.getCompany(),
                    job.getLocation(),
                    job.getWorkMode(),
                    job.getSalaryMin(),
                    job.getSalaryMax(),
                    job.getSalaryCurrency(),
                    job.getSalaryPeriod(),
                    job.getMinYearsExperience(),
                    job.getRequiredEducation(),
                    readList(job.getRequiredSkillsJson()),
                    match
            ));
        }
        return results;
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
        return computeMatchForJob(principal.id(), job, req != null ? req.resumeVersionId() : null);
    }

    @PutMapping("/{id}/match/feedback")
    @Transactional
    public MatchResultDto submitMatchFeedback(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody MatchFeedbackRequest req
    ) {
        requireAuth(principal);
        Job job = jobRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        String fb = req.feedback().trim().toUpperCase(Locale.ROOT);
        if (!"THUMBS_UP".equals(fb) && !"THUMBS_DOWN".equals(fb)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feedback must be THUMBS_UP or THUMBS_DOWN");
        }

        JobMatch match = jobMatchRepository.findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), principal.id())
                .orElseGet(() -> {
                    computeMatchForJob(principal.id(), job, null);
                    return jobMatchRepository.findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), principal.id())
                            .orElseThrow();
                });

        match.setUserFeedback(fb);
        match.setFeedbackNote(req.note());
        match = jobMatchRepository.save(match);
        return toMatchDto(match);
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

        return toMatchDto(match);
    }

    private MatchResultDto computeMatchForJob(UUID userId, Job job, UUID requestedResumeVersionId) {
        ResumeVersion resumeVersion;
        if (requestedResumeVersionId != null) {
            resumeVersion = resumeVersionRepository.findByIdAndUserId(requestedResumeVersionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));
        } else {
            resumeVersion = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                    .or(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Please upload a resume and verify your skills before computing a job match score."
                    ));
        }

        Optional<CareerProfile> profileOpt = profileRepository.findByUserId(userId);
        List<String> preferredCities = profileOpt.map(p -> readList(p.getCities())).orElse(List.of());
        List<String> preferredWorkModes = profileOpt.map(p -> readList(p.getWorkModes())).orElse(List.of());
        Double resumeYears = resumeVersion.getYearsOfExperience();
        if (resumeYears == null && profileOpt.isPresent()) {
            resumeYears = profileOpt.get().getYearsOfExperience();
        }
        Double expectedSalary = profileOpt.map(CareerProfile::getExpectedSalary).orElse(null);

        MatchResultDto computed = matchScoringService.computeFullMatch(
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
                preferredWorkModes,
                expectedSalary,
                job.getSalaryMin(),
                job.getSalaryMax(),
                resumeVersion.getExtractedText(),
                job.getRequiredEducation()
        );

        JobMatch entity = jobMatchRepository
                .findFirstByJobIdAndResumeVersionIdAndUserId(job.getId(), resumeVersion.getId(), userId)
                .orElseGet(JobMatch::new);
        entity.setUserId(userId);
        entity.setJobId(job.getId());
        entity.setResumeVersionId(resumeVersion.getId());
        entity.setTotalScore(computed.score());
        entity.setRequiredSkillsScore(computed.requiredSkillsScore());
        entity.setNiceToHaveScore(computed.niceToHaveScore());
        entity.setExperienceScore(computed.experienceScore());
        entity.setLocationScore(computed.locationScore());
        entity.setSalaryFit(computed.salaryFit());
        entity.setQualificationFit(computed.qualificationFit());
        entity.setResultJson(writeJson(computed, "{}"));
        entity = jobMatchRepository.save(entity);

        return toMatchDto(entity);
    }

    private MatchResultDto toMatchDto(JobMatch match) {
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
                    match.getSalaryFit() != null ? match.getSalaryFit() : (stored.salaryFit() != null ? stored.salaryFit() : MatchResultDto.NOT_ENOUGH_DATA),
                    match.getQualificationFit() != null ? match.getQualificationFit() : (stored.qualificationFit() != null ? stored.qualificationFit() : MatchResultDto.NOT_ENOUGH_DATA),
                    match.getUserFeedback(),
                    match.getFeedbackNote(),
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
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getSalaryCurrency(),
                j.getSalaryPeriod(),
                j.getRequiredEducation(),
                j.isSaved(),
                j.isStale(),
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
