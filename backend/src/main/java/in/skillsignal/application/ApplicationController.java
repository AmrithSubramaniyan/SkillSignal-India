package in.skillsignal.application;

import in.skillsignal.audit.AuditService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApplicationController {

    public static final Set<String> VALID_STATUSES = Set.of(
            "SAVED",
            "PREPARING",
            "READY_TO_APPLY",
            "APPLIED_CONFIRMED",
            "NEEDS_VERIFICATION",
            "INTERVIEWING",
            "OFFER",
            "REJECTED",
            "WITHDRAWN"
    );

    public record CreateApplicationRequest(
            @NotNull UUID jobId,
            UUID resumeVersionId,
            String status,
            String applyMode,
            String notes,
            LocalDate followUpDate
    ) {}

    public record UpdateApplicationRequest(
            String status,
            String notes,
            LocalDate followUpDate,
            UUID resumeVersionId
    ) {}

    public record ApplicationEventDto(
            UUID id,
            String fromStatus,
            String toStatus,
            String note,
            Instant createdAt
    ) {
        public static ApplicationEventDto from(ApplicationEvent e) {
            return new ApplicationEventDto(e.getId(), e.getFromStatus(), e.getToStatus(), e.getNote(), e.getCreatedAt());
        }
    }

    public record ApplicationDto(
            UUID id,
            UUID jobId,
            String jobTitle,
            String company,
            String location,
            String workMode,
            UUID resumeVersionId,
            String status,
            String applyMode,
            String notes,
            Instant appliedAt,
            LocalDate followUpDate,
            Integer matchScore,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DashboardSummaryDto(
            long totalJobs,
            long totalApplications,
            Map<String, Long> statusCounts,
            int followUpsDueCount,
            List<ApplicationDto> followUpsDue,
            Double estimatedAverageMatchScore,
            String metricsDisclaimer
    ) {}

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final AuditService auditService;

    public ApplicationController(
            ApplicationRepository applicationRepository,
            ApplicationEventRepository eventRepository,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            AuditService auditService
    ) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.auditService = auditService;
    }

    @PostMapping("/applications")
    @Transactional
    public ResponseEntity<ApplicationDto> createApplication(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateApplicationRequest req
    ) {
        requireAuth(principal);
        Job job = jobRepository.findByIdAndUserId(req.jobId(), principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (applicationRepository.existsByUserIdAndJobId(principal.id(), job.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An application for this job already exists in your tracker"
            );
        }

        // Golden Rule: Never mark anything submitted automatically. Default to SAVED.
        String initialStatus = "SAVED";
        if (req.status() != null && !req.status().isBlank()) {
            String normalized = req.status().trim().toUpperCase(Locale.ROOT);
            if (!VALID_STATUSES.contains(normalized)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status: " + req.status());
            }
            initialStatus = normalized;
        }

        Application app = new Application();
        app.setUserId(principal.id());
        app.setJobId(job.getId());
        app.setResumeVersionId(req.resumeVersionId());
        app.setStatus(initialStatus);
        app.setApplyMode(req.applyMode() != null && !req.applyMode().isBlank() ? req.applyMode().trim().toUpperCase(Locale.ROOT) : "ASSISTED");
        app.setNotes(req.notes());
        app.setFollowUpDate(req.followUpDate());
        if ("APPLIED_CONFIRMED".equals(initialStatus)) {
            app.setAppliedAt(Instant.now());
        }
        app = applicationRepository.save(app);

        ApplicationEvent ev = new ApplicationEvent();
        ev.setApplicationId(app.getId());
        ev.setUserId(principal.id());
        ev.setFromStatus(null);
        ev.setToStatus(initialStatus);
        ev.setNote(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : "Application created in tracker");
        eventRepository.save(ev);

        auditService.log(principal.id(), "APPLICATION_CREATED", "Application", app.getId().toString(),
                Map.of("jobId", job.getId().toString(), "status", initialStatus));

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(app, job));
    }

    @GetMapping("/applications")
    public List<ApplicationDto> listApplications(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        Map<UUID, Job> jobsById = new HashMap<>();
        jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id())
                .forEach(j -> jobsById.put(j.getId(), j));

        return applicationRepository.findByUserIdOrderByUpdatedAtDesc(principal.id()).stream()
                .map(app -> toDto(app, jobsById.get(app.getJobId())))
                .toList();
    }

    @GetMapping("/applications/{id}")
    public ApplicationDto getApplication(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        Application app = applicationRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Job job = jobRepository.findByIdAndUserId(app.getJobId(), principal.id()).orElse(null);
        return toDto(app, job);
    }

    @PutMapping("/applications/{id}")
    @Transactional
    public ApplicationDto updateApplication(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationRequest req
    ) {
        requireAuth(principal);
        Application app = applicationRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        String oldStatus = app.getStatus();
        String newStatus = oldStatus;
        if (req.status() != null && !req.status().isBlank()) {
            String normalized = req.status().trim().toUpperCase(Locale.ROOT);
            if (!VALID_STATUSES.contains(normalized)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status: " + req.status());
            }
            newStatus = normalized;
        }

        if (req.notes() != null) {
            app.setNotes(req.notes());
        }
        if (req.followUpDate() != null) {
            app.setFollowUpDate(req.followUpDate());
        }
        if (req.resumeVersionId() != null) {
            app.setResumeVersionId(req.resumeVersionId());
        }

        if (!Objects.equals(oldStatus, newStatus)) {
            app.setStatus(newStatus);
            if ("APPLIED_CONFIRMED".equals(newStatus) && app.getAppliedAt() == null) {
                app.setAppliedAt(Instant.now());
            }
            ApplicationEvent ev = new ApplicationEvent();
            ev.setApplicationId(app.getId());
            ev.setUserId(principal.id());
            ev.setFromStatus(oldStatus);
            ev.setToStatus(newStatus);
            ev.setNote(req.notes() != null && !req.notes().isBlank()
                    ? req.notes().trim()
                    : ("Status updated from " + oldStatus + " to " + newStatus));
            eventRepository.save(ev);

            auditService.log(principal.id(), "APPLICATION_STATUS_UPDATED", "Application", app.getId().toString(),
                    Map.of("fromStatus", oldStatus, "toStatus", newStatus));
        }

        app = applicationRepository.save(app);
        Job job = jobRepository.findByIdAndUserId(app.getJobId(), principal.id()).orElse(null);
        return toDto(app, job);
    }

    @GetMapping("/applications/{id}/events")
    public List<ApplicationEventDto> listEvents(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        Application app = applicationRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        return eventRepository.findByApplicationIdAndUserIdOrderByCreatedAtAsc(app.getId(), principal.id()).stream()
                .map(ApplicationEventDto::from)
                .toList();
    }

    @DeleteMapping("/applications/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteApplication(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        Application app = applicationRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        applicationRepository.delete(app);
        return ResponseEntity.ok(Map.of("message", "Application removed from tracker"));
    }

    @GetMapping("/dashboard")
    public DashboardSummaryDto getDashboard(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
        Map<UUID, Job> jobsById = new HashMap<>();
        userJobs.forEach(j -> jobsById.put(j.getId(), j));

        List<Application> apps = applicationRepository.findByUserIdOrderByUpdatedAtDesc(principal.id());
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (String st : List.of(
                "SAVED", "PREPARING", "READY_TO_APPLY", "APPLIED_CONFIRMED",
                "NEEDS_VERIFICATION", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN"
        )) {
            statusCounts.put(st, 0L);
        }
        for (Application a : apps) {
            statusCounts.put(a.getStatus(), statusCounts.getOrDefault(a.getStatus(), 0L) + 1L);
        }

        LocalDate today = LocalDate.now();
        List<ApplicationDto> dueFollowUps = applicationRepository
                .findByUserIdAndFollowUpDateLessThanEqualOrderByFollowUpDateAsc(principal.id(), today)
                .stream()
                .filter(a -> !"REJECTED".equals(a.getStatus()) && !"WITHDRAWN".equals(a.getStatus()))
                .map(a -> toDto(a, jobsById.get(a.getJobId())))
                .toList();

        List<JobMatch> matches = jobMatchRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
        Double avgScore = matches.isEmpty()
                ? null
                : Math.round(matches.stream().mapToInt(JobMatch::getTotalScore).average().orElse(0.0) * 10.0) / 10.0;

        return new DashboardSummaryDto(
                userJobs.size(),
                apps.size(),
                statusCounts,
                dueFollowUps.size(),
                dueFollowUps,
                avgScore,
                "All scores and metrics are labelled estimates computed strictly from your stored records."
        );
    }

    private ApplicationDto toDto(Application app, Job job) {
        Integer matchScore = jobMatchRepository
                .findFirstByJobIdAndUserIdOrderByCreatedAtDesc(app.getJobId(), app.getUserId())
                .map(JobMatch::getTotalScore)
                .orElse(null);

        return new ApplicationDto(
                app.getId(),
                app.getJobId(),
                job != null ? job.getTitle() : "Deleted Job",
                job != null ? job.getCompany() : "",
                job != null ? job.getLocation() : "",
                job != null ? job.getWorkMode() : "",
                app.getResumeVersionId(),
                app.getStatus(),
                app.getApplyMode(),
                app.getNotes(),
                app.getAppliedAt(),
                app.getFollowUpDate(),
                matchScore,
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

