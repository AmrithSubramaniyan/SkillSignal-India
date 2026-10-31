package in.skillsignal.privacy;

import in.skillsignal.application.ApplicationEventRepository;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.audit.AuditLog;
import in.skillsignal.audit.AuditLogRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.consent.ConsentRecordRepository;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.profile.CareerProfileRepository;
import in.skillsignal.resume.ResumeRepository;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.security.AuthController;
import in.skillsignal.security.AuthenticatedUser;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    private final UserRepository userRepository;
    private final CareerProfileRepository profileRepository;
    private final ConsentRecordRepository consentRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final Path uploadRootDir;

    public PrivacyController(
            UserRepository userRepository,
            CareerProfileRepository profileRepository,
            ConsentRecordRepository consentRepository,
            ResumeRepository resumeRepository,
            ResumeVersionRepository resumeVersionRepository,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            @Value("${app.uploads.dir:./uploads}") String uploadDir
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.consentRepository = consentRepository;
        this.resumeRepository = resumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.uploadRootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> getAuditLogs(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
    }

    @GetMapping("/export")
    public Map<String, Object> exportUserData(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        auditService.log(user.getId(), "PRIVACY_DATA_EXPORTED", "User", user.getId().toString(), Map.of());

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now().toString());
        export.put("user", AuthController.UserDto.from(user));
        export.put("careerProfile", profileRepository.findByUserId(user.getId()).orElse(null));
        export.put("consents", consentRepository.findByUserIdOrderByConsentTypeAsc(user.getId()));
        export.put("resumes", resumeRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("resumeVersions", resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("jobs", jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("jobMatches", jobMatchRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("applications", applicationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()));
        export.put("applicationEvents", applicationEventRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        export.put("auditLogs", auditLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        return export;
    }

    @DeleteMapping("/account")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 1. Delete user's uploaded files directory safely
        Path userUploadDir = uploadRootDir.resolve(user.getId().toString()).normalize();
        if (userUploadDir.startsWith(uploadRootDir) && Files.exists(userUploadDir)) {
            try (Stream<Path> walk = Files.walk(userUploadDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }

        // 2. Delete all user records
        applicationEventRepository.deleteByUserId(user.getId());
        applicationRepository.deleteByUserId(user.getId());
        jobMatchRepository.deleteByUserId(user.getId());
        jobRepository.deleteByUserId(user.getId());
        resumeVersionRepository.deleteByUserId(user.getId());
        resumeRepository.deleteByUserId(user.getId());
        consentRepository.deleteByUserId(user.getId());
        profileRepository.deleteByUserId(user.getId());
        auditLogRepository.deleteByUserId(user.getId());
        userRepository.delete(user);

        return ResponseEntity.ok(Map.of("message", "Account, records, and uploaded files have been permanently erased."));
    }
}
