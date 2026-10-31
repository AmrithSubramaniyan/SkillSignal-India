package in.skillsignal.resume;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.audit.AuditService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.profile.CareerProfile;
import in.skillsignal.profile.CareerProfileRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;

@Service
public class ResumeTailoringService {

    private final TailoredResumeRepository tailoredResumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final JobRepository jobRepository;
    private final CareerProfileRepository profileRepository;
    private final TruthfulnessVerifier truthfulnessVerifier;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ResumeTailoringService(
            TailoredResumeRepository tailoredResumeRepository,
            ResumeVersionRepository resumeVersionRepository,
            JobRepository jobRepository,
            CareerProfileRepository profileRepository,
            TruthfulnessVerifier truthfulnessVerifier,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.jobRepository = jobRepository;
        this.profileRepository = profileRepository;
        this.truthfulnessVerifier = truthfulnessVerifier;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public record TailorResumeRequest(
            UUID resumeVersionId,
            UUID jobId,
            String customDraftText
    ) {}

    public record DiffEntry(
            String section,
            String changeType, // REORDERED, REWORDED, UNCHANGED, MODIFIED
            String before,
            String after
    ) {}

    public record TailoredResumeDto(
            UUID id,
            UUID resumeVersionId,
            UUID jobId,
            String jobTitle,
            String jobCompany,
            List<String> keywordGaps,
            List<String> tips,
            String originalText,
            String tailoredText,
            List<DiffEntry> diff,
            boolean truthfulnessPassed,
            List<String> unsupportedClaims,
            List<TruthfulnessVerifier.ClaimTrace> claimTraces,
            boolean userApproved,
            Instant approvedAt,
            Instant createdAt
    ) {}

    @Transactional
    public TailoredResumeDto createTailoredDraft(UUID userId, TailorResumeRequest req) {
        if (req == null || req.jobId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobId is required");
        }
        Job job = jobRepository.findByIdAndUserId(req.jobId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        ResumeVersion rv;
        if (req.resumeVersionId() != null) {
            rv = resumeVersionRepository.findByIdAndUserId(req.resumeVersionId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));
        } else {
            rv = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                    .or(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a resume first"));
        }

        Optional<CareerProfile> profileOpt = profileRepository.findByUserId(userId);
        List<String> verifiedSkills = readStringList(rv.getVerifiedSkillsJson());
        List<String> profileSkills = profileOpt.map(p -> readStringList(p.getSkills())).orElse(List.of());
        Double candidateYears = rv.getYearsOfExperience() != null
                ? rv.getYearsOfExperience()
                : profileOpt.map(CareerProfile::getYearsOfExperience).orElse(null);

        List<String> jobReqSkills = readStringList(job.getRequiredSkillsJson());
        List<String> jobNiceSkills = readStringList(job.getNiceToHaveSkillsJson());
        List<String> allJobSkills = new ArrayList<>();
        allJobSkills.addAll(jobReqSkills);
        allJobSkills.addAll(jobNiceSkills);

        String originalText = rv.getExtractedText() != null ? rv.getExtractedText().trim() : "";
        String origLower = originalText.toLowerCase(Locale.ROOT);
        Set<String> verifiedLower = new HashSet<>();
        verifiedSkills.forEach(s -> verifiedLower.add(s.toLowerCase(Locale.ROOT)));
        profileSkills.forEach(s -> verifiedLower.add(s.toLowerCase(Locale.ROOT)));

        List<String> matchedJobSkills = new ArrayList<>();
        List<String> keywordGaps = new ArrayList<>();
        for (String js : allJobSkills) {
            String jsLower = js.toLowerCase(Locale.ROOT);
            if (verifiedLower.contains(jsLower) || origLower.contains(jsLower)) {
                matchedJobSkills.add(js);
            } else {
                keywordGaps.add(js);
            }
        }

        List<String> tips = buildThreeTips(job, matchedJobSkills, keywordGaps);

        String tailoredText;
        if (req.customDraftText() != null && !req.customDraftText().isBlank()) {
            tailoredText = req.customDraftText().trim();
        } else {
            tailoredText = generateTruthfulReorderedDraft(originalText, matchedJobSkills, verifiedSkills);
        }

        List<DiffEntry> diff = computeBeforeAfterDiff(originalText, tailoredText, matchedJobSkills);
        TruthfulnessVerifier.VerificationReport verification = truthfulnessVerifier.verify(
                originalText,
                verifiedSkills,
                profileSkills,
                candidateYears,
                allJobSkills,
                tailoredText
        );

        TailoredResume entity = new TailoredResume();
        entity.setUserId(userId);
        entity.setResumeVersionId(rv.getId());
        entity.setJobId(job.getId());
        entity.setKeywordGapsJson(writeJson(keywordGaps));
        entity.setTipsJson(writeJson(tips));
        entity.setOriginalText(originalText);
        entity.setTailoredText(tailoredText);
        entity.setDiffJson(writeJson(diff));
        entity.setTruthfulnessPassed(verification.passed());
        entity.setUnsupportedClaimsJson(writeJson(verification.unsupportedClaims()));
        entity.setClaimTracesJson(writeJson(verification.claimTraces()));
        entity.setUserApproved(false);
        entity.setApprovedAt(null);
        entity = tailoredResumeRepository.save(entity);

        return toDto(entity, job);
    }

    @Transactional
    public TailoredResumeDto updateTailoredDraft(UUID userId, UUID id, String updatedText) {
        TailoredResume entity = tailoredResumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tailored resume not found"));
        Job job = jobRepository.findByIdAndUserId(entity.getJobId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        ResumeVersion rv = resumeVersionRepository.findByIdAndUserId(entity.getResumeVersionId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));
        Optional<CareerProfile> profileOpt = profileRepository.findByUserId(userId);

        List<String> verifiedSkills = readStringList(rv.getVerifiedSkillsJson());
        List<String> profileSkills = profileOpt.map(p -> readStringList(p.getSkills())).orElse(List.of());
        Double candidateYears = rv.getYearsOfExperience() != null
                ? rv.getYearsOfExperience()
                : profileOpt.map(CareerProfile::getYearsOfExperience).orElse(null);

        List<String> allJobSkills = new ArrayList<>();
        allJobSkills.addAll(readStringList(job.getRequiredSkillsJson()));
        allJobSkills.addAll(readStringList(job.getNiceToHaveSkillsJson()));

        String trimmedDraft = updatedText != null ? updatedText.trim() : "";
        List<DiffEntry> diff = computeBeforeAfterDiff(entity.getOriginalText(), trimmedDraft, verifiedSkills);
        TruthfulnessVerifier.VerificationReport verification = truthfulnessVerifier.verify(
                entity.getOriginalText(),
                verifiedSkills,
                profileSkills,
                candidateYears,
                allJobSkills,
                trimmedDraft
        );

        entity.setTailoredText(trimmedDraft);
        entity.setDiffJson(writeJson(diff));
        entity.setTruthfulnessPassed(verification.passed());
        entity.setUnsupportedClaimsJson(writeJson(verification.unsupportedClaims()));
        entity.setClaimTracesJson(writeJson(verification.claimTraces()));
        entity.setUserApproved(false);
        entity.setApprovedAt(null);
        entity = tailoredResumeRepository.save(entity);

        return toDto(entity, job);
    }

    @Transactional
    public TailoredResumeDto approveTailoredDraft(UUID userId, UUID id) {
        TailoredResume entity = tailoredResumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tailored resume not found"));
        if (!entity.isTruthfulnessPassed()) {
            List<String> unsupported = readStringList(entity.getUnsupportedClaimsJson());
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Truthfulness check blocked approval due to unsupported claims: " + String.join("; ", unsupported)
            );
        }
        Job job = jobRepository.findByIdAndUserId(entity.getJobId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        entity.setUserApproved(true);
        entity.setApprovedAt(Instant.now());
        entity = tailoredResumeRepository.save(entity);

        auditService.log(userId, "TAILORED_RESUME_APPROVED", "TailoredResume", entity.getId().toString(),
                Map.of("jobId", job.getId().toString(), "resumeVersionId", entity.getResumeVersionId().toString()));
        return toDto(entity, job);
    }

    public List<TailoredResumeDto> listForUser(UUID userId) {
        return tailoredResumeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(entity -> {
                    Job job = jobRepository.findByIdAndUserId(entity.getJobId(), userId).orElse(null);
                    return toDto(entity, job);
                })
                .toList();
    }

    public byte[] exportApprovedDraft(UUID userId, UUID id, String format) {
        TailoredResume entity = tailoredResumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tailored resume not found"));
        if (!entity.isTruthfulnessPassed()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot download tailored resume: truthfulness check blocked unsupported claims."
            );
        }
        if (!entity.isUserApproved()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Please review and approve the tailored resume draft before downloading."
            );
        }

        if ("docx".equalsIgnoreCase(format)) {
            return renderDocx(entity.getTailoredText());
        }
        return renderPdf(entity.getTailoredText());
    }

    private String generateTruthfulReorderedDraft(
            String originalText,
            List<String> matchedJobSkills,
            List<String> verifiedSkills
    ) {
        String[] sentences = originalText.split("(?<=[.!?\\n])\\s+");
        List<String> prioritized = new ArrayList<>();
        List<String> remaining = new ArrayList<>();

        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean mentionsMatched = matchedJobSkills.stream()
                    .anyMatch(ms -> lower.contains(ms.toLowerCase(Locale.ROOT)));
            if (mentionsMatched) {
                prioritized.add(trimmed);
            } else {
                remaining.add(trimmed);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!matchedJobSkills.isEmpty()) {
            sb.append("VERIFIED RELEVANT SKILLS: ").append(String.join(", ", matchedJobSkills)).append("\n\n");
        } else if (!verifiedSkills.isEmpty()) {
            sb.append("VERIFIED SKILLS: ").append(String.join(", ", verifiedSkills)).append("\n\n");
        }
        if (!prioritized.isEmpty()) {
            sb.append("REORDERED ROLE-RELEVANT HIGHLIGHTS:\n");
            for (String p : prioritized) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }
        if (!remaining.isEmpty()) {
            sb.append("ADDITIONAL VERIFIED EXPERIENCE & EDUCATION:\n");
            for (String r : remaining) {
                sb.append("- ").append(r).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private List<String> buildThreeTips(Job job, List<String> matchedJobSkills, List<String> keywordGaps) {
        List<String> tips = new ArrayList<>(3);
        if (!matchedJobSkills.isEmpty()) {
            tips.add("Lead your summary with verified achievements in "
                    + String.join(", ", matchedJobSkills.subList(0, Math.min(3, matchedJobSkills.size())))
                    + " since they directly match " + job.getTitle() + " at " + job.getCompany() + ".");
        } else {
            tips.add("Place your most relevant verified project outcomes at the top of your experience section for "
                    + job.getTitle() + ".");
        }

        if (!keywordGaps.isEmpty()) {
            tips.add("Keyword gap alert: " + String.join(", ", keywordGaps.subList(0, Math.min(3, keywordGaps.size())))
                    + " appear in the JD but not in your resume. Only add them to your profile if you genuinely possess hands-on experience.");
        } else {
            tips.add("Your verified skills cover all extracted keywords for this role; focus on quantifying impact metrics from your existing projects.");
        }

        tips.add("Ensure your location (" + (job.getLocation() != null ? job.getLocation() : "India")
                + ") and work mode (" + (job.getWorkMode() != null ? job.getWorkMode() : "ONSITE")
                + ") preferences are clearly stated in your header.");
        return tips;
    }

    private List<DiffEntry> computeBeforeAfterDiff(
            String originalText,
            String tailoredText,
            List<String> matchedSkills
    ) {
        List<DiffEntry> diffs = new ArrayList<>();
        diffs.add(new DiffEntry(
                "Skills & Summary Ordering",
                "REORDERED",
                originalText.length() > 160 ? originalText.substring(0, 160) + "..." : originalText,
                tailoredText.length() > 160 ? tailoredText.substring(0, 160) + "..." : tailoredText
        ));
        if (!matchedSkills.isEmpty()) {
            diffs.add(new DiffEntry(
                    "Highlighted Verified Skills",
                    "REWORDED",
                    "Original inline skill mentions",
                    "Front-loaded verified skills: " + String.join(", ", matchedSkills)
            ));
        }
        return diffs;
    }

    private byte[] renderPdf(String text) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(45, 740);
                cs.setLeading(14.0f);
                for (String rawLine : text.split("\\r?\\n")) {
                    String safeLine = rawLine.replaceAll("[^\\x20-\\x7E]", " ");
                    while (safeLine.length() > 90) {
                        cs.showText(safeLine.substring(0, 90));
                        cs.newLine();
                        safeLine = safeLine.substring(90);
                    }
                    cs.showText(safeLine);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF");
        }
    }

    private byte[] renderDocx(String text) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : text.split("\\r?\\n")) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(line);
                run.setFontSize(11);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate DOCX");
        }
    }

    private TailoredResumeDto toDto(TailoredResume e, Job job) {
        return new TailoredResumeDto(
                e.getId(),
                e.getResumeVersionId(),
                e.getJobId(),
                job != null ? job.getTitle() : "Job",
                job != null ? job.getCompany() : "",
                readStringList(e.getKeywordGapsJson()),
                readStringList(e.getTipsJson()),
                e.getOriginalText(),
                e.getTailoredText(),
                readDiffList(e.getDiffJson()),
                e.isTruthfulnessPassed(),
                readStringList(e.getUnsupportedClaimsJson()),
                readClaimTraces(e.getClaimTracesJson()),
                e.isUserApproved(),
                e.getApprovedAt(),
                e.getCreatedAt()
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<DiffEntry> readDiffList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<DiffEntry>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<TruthfulnessVerifier.ClaimTrace> readClaimTraces(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<TruthfulnessVerifier.ClaimTrace>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
