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

    public record CreateApplicationRequest(
            @NotNull UUID jobId,
            UUID resumeVersionId,
            String status,
            String subState,
            String applyMode,
            String submissionMethod,
            String confirmationType,
            String notes,
            String recruiterContacts,
            LocalDate assessmentDate,
            LocalDate interviewDate,
            String nextAction,
            LocalDate followUpDate,
            String source,
            String applicationUrl
    ) {}

    public record UpdateApplicationRequest(
            String status,
            String subState,
            String submissionMethod,
            String confirmationType,
            String notes,
            String recruiterContacts,
            LocalDate assessmentDate,
            LocalDate interviewDate,
            String nextAction,
            LocalDate followUpDate,
            UUID resumeVersionId,
            String source,
            String applicationUrl
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
            String source,
            String applicationUrl,
            UUID resumeVersionId,
            String status,
            String subState,
            String applyMode,
            String submissionMethod,
            String confirmationType,
            String notes,
            String recruiterContacts,
            Instant appliedAt,
            LocalDate assessmentDate,
            LocalDate interviewDate,
            String nextAction,
            LocalDate followUpDate,
            Integer matchScore,
            Set<String> allowedNextStatuses,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DashboardSummaryDto(
            long totalJobs,
            long totalApplications,
            Map<String, Long> statusCounts,
            int followUpsDueCount,
            List<ApplicationDto> followUpsDue,
            List<ApplicationDto> upcomingInterviews,
            List<ApplicationDto> awaitingApproval,
            List<Map<String, Object>> todaysActions,
            List<Map<String, Object>> highMatchJobs,
            String autoApplyStatus,
            Double estimatedAverageMatchScore,
            String metricsDisclaimer
    ) {}

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final ApplicationStateMachine stateMachine;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final AuditService auditService;

    public ApplicationController(
            ApplicationRepository applicationRepository,
            ApplicationEventRepository eventRepository,
            ApplicationStateMachine stateMachine,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            AuditService auditService
    ) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.stateMachine = stateMachine;
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

        String requestedStatus = (req.status() != null && !req.status().isBlank())
                ? req.status().trim().toUpperCase(Locale.ROOT)
                : "SAVED";
        if (!stateMachine.isValidStatus(requestedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status: " + req.status());
        }

        ApplicationStateMachine.TransitionOutcome outcome = stateMachine.resolveEffectiveOutcome(
                requestedStatus,
                req.subState(),
                req.confirmationType()
        );

        String method = req.submissionMethod() != null && !req.submissionMethod().isBlank()
                ? req.submissionMethod().trim().toUpperCase(Locale.ROOT)
                : (req.applyMode() != null && !req.applyMode().isBlank() ? req.applyMode().trim().toUpperCase(Locale.ROOT) : "ASSISTED");

        Application app = new Application();
        app.setUserId(principal.id());
        app.setJobId(job.getId());
        app.setResumeVersionId(req.resumeVersionId());
        app.setStatus(outcome.effectiveStatus());
        app.setSubState(outcome.effectiveSubState());
        app.setApplyMode(method);
        app.setSubmissionMethod(method);
        app.setConfirmationType(outcome.effectiveConfirmationType());
        app.setNotes(req.notes());
        app.setRecruiterContacts(req.recruiterContacts());
        app.setAssessmentDate(req.assessmentDate());
        app.setInterviewDate(req.interviewDate());
        app.setNextAction(req.nextAction());
        app.setFollowUpDate(req.followUpDate());
        app.setSource(req.source() != null && !req.source().isBlank() ? req.source() : job.getSource());
        app.setApplicationUrl(req.applicationUrl() != null && !req.applicationUrl().isBlank() ? req.applicationUrl() : job.getSourceUrl());

        if (("APPLIED".equals(outcome.effectiveStatus()) || "APPLIED_CONFIRMED".equals(outcome.effectiveStatus()))
                && outcome.effectiveSubState() == null) {
            app.setAppliedAt(Instant.now());
        }
        app = applicationRepository.save(app);

        ApplicationEvent ev = new ApplicationEvent();
        ev.setApplicationId(app.getId());
        ev.setUserId(principal.id());
        ev.setFromStatus(null);
        ev.setToStatus(outcome.effectiveStatus());
        ev.setNote(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : "Application created in tracker");
        eventRepository.save(ev);

        auditService.log(principal.id(), "APPLICATION_CREATED", "Application", app.getId().toString(),
                Map.of("jobId", job.getId().toString(), "status", outcome.effectiveStatus()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(app, job));
    }

    @GetMapping("/applications")
    public List<ApplicationDto> listApplications(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean needsVerificationOnly
    ) {
        requireAuth(principal);
        Map<UUID, Job> jobsById = new HashMap<>();
        jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id())
                .forEach(j -> jobsById.put(j.getId(), j));

        return applicationRepository.findByUserIdOrderByUpdatedAtDesc(principal.id()).stream()
                .map(app -> toDto(app, jobsById.get(app.getJobId())))
                .filter(dto -> status == null || status.isBlank() || dto.status().equalsIgnoreCase(status))
                .filter(dto -> company == null || company.isBlank() || dto.company().toLowerCase(Locale.ROOT).contains(company.toLowerCase(Locale.ROOT)))
                .filter(dto -> source == null || source.isBlank() || (dto.source() != null && dto.source().equalsIgnoreCase(source)))
                .filter(dto -> !Boolean.TRUE.equals(needsVerificationOnly)
                        || "NEEDS_VERIFICATION".equalsIgnoreCase(dto.status())
                        || "NEEDS_VERIFICATION".equalsIgnoreCase(dto.subState()))
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
        String oldSubState = app.getSubState();
        String requestedStatus = oldStatus;
        if (req.status() != null && !req.status().isBlank()) {
            requestedStatus = req.status().trim().toUpperCase(Locale.ROOT);
            stateMachine.validateTransition(oldStatus, requestedStatus);
        }

        ApplicationStateMachine.TransitionOutcome outcome = stateMachine.resolveEffectiveOutcome(
                requestedStatus,
                req.subState(),
                req.confirmationType() != null ? req.confirmationType() : app.getConfirmationType()
        );

        if (req.notes() != null) app.setNotes(req.notes());
        if (req.recruiterContacts() != null) app.setRecruiterContacts(req.recruiterContacts());
        if (req.assessmentDate() != null) app.setAssessmentDate(req.assessmentDate());
        if (req.interviewDate() != null) app.setInterviewDate(req.interviewDate());
        if (req.nextAction() != null) app.setNextAction(req.nextAction());
        if (req.followUpDate() != null) app.setFollowUpDate(req.followUpDate());
        if (req.resumeVersionId() != null) app.setResumeVersionId(req.resumeVersionId());
        if (req.submissionMethod() != null && !req.submissionMethod().isBlank()) {
            String m = req.submissionMethod().trim().toUpperCase(Locale.ROOT);
            app.setSubmissionMethod(m);
            app.setApplyMode(m);
        }
        if (req.source() != null) app.setSource(req.source());
        if (req.applicationUrl() != null) app.setApplicationUrl(req.applicationUrl());

        app.setSubState(outcome.effectiveSubState());
        app.setConfirmationType(outcome.effectiveConfirmationType());

        String newStatus = outcome.effectiveStatus();
        if (("APPLIED".equals(newStatus) || "APPLIED_CONFIRMED".equals(newStatus))
                && outcome.effectiveSubState() == null
                && app.getAppliedAt() == null) {
            app.setAppliedAt(Instant.now());
        }
        if (!Objects.equals(oldStatus, newStatus) || !Objects.equals(oldSubState, outcome.effectiveSubState())) {
            app.setStatus(newStatus);
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
                "SAVED", "INTERESTED", "PREPARING", "READY_TO_APPLY", "AWAITING_APPROVAL",
                "APPLIED", "APPLIED_CONFIRMED", "SUBMISSION_FAILED", "NEEDS_VERIFICATION",
                "ASSESSMENT", "INTERVIEW", "INTERVIEWING", "ON_HOLD", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"
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
                .filter(a -> !"REJECTED".equals(a.getStatus()) && !"WITHDRAWN".equals(a.getStatus()) && !"CLOSED".equals(a.getStatus()))
                .map(a -> toDto(a, jobsById.get(a.getJobId())))
                .toList();

        List<ApplicationDto> upcomingInterviews = apps.stream()
                .filter(a -> "INTERVIEW".equals(a.getStatus()) || "INTERVIEWING".equals(a.getStatus())
                        || "ASSESSMENT".equals(a.getStatus())
                        || (a.getInterviewDate() != null && !a.getInterviewDate().isBefore(today))
                        || (a.getAssessmentDate() != null && !a.getAssessmentDate().isBefore(today)))
                .map(a -> toDto(a, jobsById.get(a.getJobId())))
                .toList();

        List<ApplicationDto> awaitingApproval = apps.stream()
                .filter(a -> "AWAITING_APPROVAL".equals(a.getStatus())
                        || "NEEDS_VERIFICATION".equals(a.getStatus())
                        || "NEEDS_VERIFICATION".equals(a.getSubState()))
                .map(a -> toDto(a, jobsById.get(a.getJobId())))
                .toList();

        List<Map<String, Object>> todaysActions = new ArrayList<>();
        for (ApplicationDto fu : dueFollowUps) {
            todaysActions.add(Map.of(
                    "type", "FOLLOW_UP_DUE",
                    "applicationId", fu.id(),
                    "title", "Follow up on " + fu.jobTitle() + " @ " + fu.company(),
                    "dueDate", fu.followUpDate() != null ? fu.followUpDate().toString() : today.toString()
            ));
        }
        for (ApplicationDto aw : awaitingApproval) {
            todaysActions.add(Map.of(
                    "type", "ACTION_REQUIRED",
                    "applicationId", aw.id(),
                    "title", aw.status() + ": " + aw.jobTitle() + " @ " + aw.company(),
                    "dueDate", today.toString()
            ));
        }

        List<JobMatch> matches = jobMatchRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
        Double avgScore = matches.isEmpty()
                ? null
                : Math.round(matches.stream().mapToInt(JobMatch::getTotalScore).average().orElse(0.0) * 10.0) / 10.0;

        List<Map<String, Object>> highMatchJobs = new ArrayList<>();
        Set<UUID> seenHighJobs = new HashSet<>();
        for (JobMatch jm : matches) {
            if (jm.getTotalScore() >= 75 && seenHighJobs.add(jm.getJobId())) {
                Job j = jobsById.get(jm.getJobId());
                if (j != null) {
                    highMatchJobs.add(Map.of(
                            "jobId", j.getId(),
                            "title", j.getTitle(),
                            "company", j.getCompany(),
                            "score", jm.getTotalScore()
                    ));
                }
            }
        }

        return new DashboardSummaryDto(
                userJobs.size(),
                apps.size(),
                statusCounts,
                dueFollowUps.size(),
                dueFollowUps,
                upcomingInterviews,
                awaitingApproval,
                todaysActions,
                highMatchJobs,
                "Assisted Apply (Default — No unapproved auto-submission)",
                avgScore,
                "All scores and metrics are labelled estimates computed strictly from your stored records."
        );
    }

    private ApplicationDto toDto(Application app, Job job) {
        Integer matchScore = jobMatchRepository
                .findFirstByJobIdAndUserIdOrderByCreatedAtDesc(app.getJobId(), app.getUserId())
                .map(JobMatch::getTotalScore)
                .orElse(null);

        String src = app.getSource() != null && !app.getSource().isBlank()
                ? app.getSource()
                : (job != null ? job.getSource() : "manual");
        String url = app.getApplicationUrl() != null && !app.getApplicationUrl().isBlank()
                ? app.getApplicationUrl()
                : (job != null ? job.getSourceUrl() : null);

        return new ApplicationDto(
                app.getId(),
                app.getJobId(),
                job != null ? job.getTitle() : "Deleted Job",
                job != null ? job.getCompany() : "",
                job != null ? job.getLocation() : "",
                job != null ? job.getWorkMode() : "",
                src,
                url,
                app.getResumeVersionId(),
                app.getStatus(),
                app.getSubState(),
                app.getApplyMode(),
                app.getSubmissionMethod() != null ? app.getSubmissionMethod() : app.getApplyMode(),
                app.getConfirmationType() != null ? app.getConfirmationType() : "UNCONFIRMED",
                app.getNotes(),
                app.getRecruiterContacts(),
                app.getAppliedAt(),
                app.getAssessmentDate(),
                app.getInterviewDate(),
                app.getNextAction(),
                app.getFollowUpDate(),
                matchScore,
                stateMachine.getAllowedNextStatuses(app.getStatus()),
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
