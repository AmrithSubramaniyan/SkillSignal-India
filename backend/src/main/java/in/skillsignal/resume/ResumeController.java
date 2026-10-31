package in.skillsignal.resume;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.AiClient;
import in.skillsignal.ai.ParsedResumeData;
import in.skillsignal.audit.AuditService;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    public static final int MAX_VERSIONS_PER_USER = 3;

    public record ResumeVersionDto(
            UUID id,
            UUID resumeId,
            int versionNumber,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            String extractedText,
            ParsedResumeData parsedData,
            List<String> verifiedSkills,
            Double yearsOfExperience,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdateVerifiedSkillsRequest(
            @NotNull List<String> verifiedSkills,
            Double yearsOfExperience
    ) {}

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final ResumeTextExtractor textExtractor;
    private final AiClient aiClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Path uploadRootDir;

    public ResumeController(
            ResumeRepository resumeRepository,
            ResumeVersionRepository versionRepository,
            ResumeTextExtractor textExtractor,
            AiClient aiClient,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${app.uploads.dir:./uploads}") String uploadDir
    ) {
        this.resumeRepository = resumeRepository;
        this.versionRepository = versionRepository;
        this.textExtractor = textExtractor;
        this.aiClient = aiClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.uploadRootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/versions")
    public List<ResumeVersionDto> listVersions(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return versionRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/versions/{id}")
    public ResumeVersionDto getVersion(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        ResumeVersion version = versionRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));
        return toDto(version);
    }

    @PostMapping("/upload")
    @Transactional
    public ResponseEntity<ResumeVersionDto> uploadResume(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        requireAuth(principal);
        long currentCount = versionRepository.countByUserId(principal.id());
        if (currentCount >= MAX_VERSIONS_PER_USER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum of 3 resume versions allowed per user. Please delete an existing version first."
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file.");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume.pdf";
        // Sanitize display filename (strip any directory components)
        String safeOriginalName = Paths.get(originalName).getFileName().toString();
        String extractedText = textExtractor.validateAndExtract(safeOriginalName, bytes);

        // Save under ./uploads/{userId}/{uuid}
        UUID storageUuid = UUID.randomUUID();
        Path userDir = uploadRootDir.resolve(principal.id().toString()).normalize();
        Path targetFile = userDir.resolve(storageUuid.toString()).normalize();
        if (!targetFile.startsWith(uploadRootDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage path");
        }
        try {
            Files.createDirectories(userDir);
            Files.write(targetFile, bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file");
        }

        ParsedResumeData parsed = aiClient.parseResume(extractedText);

        Resume parent = resumeRepository.findFirstByUserIdOrderByCreatedAtDesc(principal.id())
                .orElseGet(() -> {
                    Resume r = new Resume();
                    r.setUserId(principal.id());
                    r.setTitle(title != null && !title.isBlank() ? title.trim() : safeOriginalName);
                    r.setDefault(true);
                    r.setCurrentVersionNumber(1);
                    return resumeRepository.save(r);
                });

        List<ResumeVersion> existingVersions = versionRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
        int nextVersionNum = existingVersions.stream().mapToInt(ResumeVersion::getVersionNumber).max().orElse(0) + 1;
        boolean makeDefault = existingVersions.isEmpty();

        if (makeDefault) {
            for (ResumeVersion ev : existingVersions) {
                ev.setDefault(false);
            }
            versionRepository.saveAll(existingVersions);
        }

        ResumeVersion version = new ResumeVersion();
        version.setResumeId(parent.getId());
        version.setUserId(principal.id());
        version.setVersionNumber(nextVersionNum);
        version.setOriginalFilename(safeOriginalName);
        version.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        version.setFileSizeBytes(bytes.length);
        version.setStoragePath(targetFile.toString());
        version.setExtractedText(extractedText);
        version.setParsedJson(writeJson(parsed, "{}"));
        version.setVerifiedSkillsJson(writeJson(parsed.skills() != null ? parsed.skills() : List.of(), "[]"));
        version.setYearsOfExperience(parsed.yearsOfExperience() != null ? parsed.yearsOfExperience() : 0.0);
        version.setDefault(makeDefault);
        version = versionRepository.save(version);

        parent.setCurrentVersionNumber(nextVersionNum);
        resumeRepository.save(parent);

        auditService.log(
                principal.id(),
                "RESUME_UPLOADED",
                "ResumeVersion",
                version.getId().toString(),
                Map.of("versionNumber", nextVersionNum, "fileSizeBytes", bytes.length)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(version));
    }

    @PutMapping("/versions/{id}/skills")
    @Transactional
    public ResumeVersionDto updateVerifiedSkills(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVerifiedSkillsRequest req
    ) {
        requireAuth(principal);
        ResumeVersion version = versionRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));

        List<String> cleaned = req.verifiedSkills().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        version.setVerifiedSkillsJson(writeJson(cleaned, "[]"));
        if (req.yearsOfExperience() != null && req.yearsOfExperience() >= 0) {
            version.setYearsOfExperience(req.yearsOfExperience());
        }
        version = versionRepository.save(version);

        auditService.log(
                principal.id(),
                "RESUME_SKILLS_VERIFIED",
                "ResumeVersion",
                version.getId().toString(),
                Map.of("verifiedSkillCount", cleaned.size())
        );

        return toDto(version);
    }

    @PutMapping("/versions/{id}/default")
    @Transactional
    public ResumeVersionDto setDefaultVersion(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        ResumeVersion target = versionRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));

        List<ResumeVersion> all = versionRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
        for (ResumeVersion v : all) {
            v.setDefault(v.getId().equals(target.getId()));
        }
        versionRepository.saveAll(all);
        return toDto(target);
    }

    @DeleteMapping("/versions/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteVersion(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        ResumeVersion target = versionRepository.findByIdAndUserId(id, principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));

        if (target.getStoragePath() != null) {
            try {
                Path p = Paths.get(target.getStoragePath()).toAbsolutePath().normalize();
                if (p.startsWith(uploadRootDir)) {
                    Files.deleteIfExists(p);
                }
            } catch (IOException ignored) {
            }
        }

        boolean wasDefault = target.isDefault();
        versionRepository.delete(target);

        if (wasDefault) {
            List<ResumeVersion> remaining = versionRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
            if (!remaining.isEmpty()) {
                ResumeVersion promoted = remaining.get(0);
                promoted.setDefault(true);
                versionRepository.save(promoted);
            }
        }

        auditService.log(principal.id(), "RESUME_VERSION_DELETED", "ResumeVersion", id.toString(), Map.of());
        return ResponseEntity.ok(Map.of("message", "Resume version deleted"));
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }

    private ResumeVersionDto toDto(ResumeVersion v) {
        ParsedResumeData parsed;
        try {
            parsed = objectMapper.readValue(v.getParsedJson(), ParsedResumeData.class);
        } catch (Exception e) {
            parsed = new ParsedResumeData(List.of(), v.getYearsOfExperience(), List.of(), List.of());
        }
        List<String> verified;
        try {
            verified = objectMapper.readValue(v.getVerifiedSkillsJson(), new TypeReference<>() {});
        } catch (Exception e) {
            verified = List.of();
        }
        return new ResumeVersionDto(
                v.getId(),
                v.getResumeId(),
                v.getVersionNumber(),
                v.getOriginalFilename(),
                v.getContentType(),
                v.getFileSizeBytes(),
                v.getExtractedText(),
                parsed,
                verified,
                v.getYearsOfExperience(),
                v.isDefault(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }

    private String writeJson(Object val, String fallback) {
        try {
            return objectMapper.writeValueAsString(val);
        } catch (Exception e) {
            return fallback;
        }
    }
}

