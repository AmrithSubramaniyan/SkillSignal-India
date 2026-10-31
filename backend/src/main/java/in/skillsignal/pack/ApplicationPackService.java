package in.skillsignal.pack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.answer.QuestionClassifierAndAnswerService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.match.MatchResultDto;
import in.skillsignal.match.MatchScoringService;
import in.skillsignal.profile.CareerProfile;
import in.skillsignal.profile.CareerProfileRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.resume.TruthfulnessVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ApplicationPackService {

    public record CoverNotesThreeTonesDto(
            String formal,
            String concise,
            String enthusiastic,
            String customOverride
    ) {}

    public record ReadinessBreakdownDto(
            int matchComponent,
            int answerCoverageComponent,
            int resumeFitComponent,
            int freshnessComponent,
            int totalReadinessScore,
            int userThreshold,
            boolean routesToReviewQueue,
            List<String> routingReasons
    ) {}

    public record ApplicationPackDto(
            UUID id,
            UUID jobId,
            UUID resumeVersionId,
            String resumeFilename,
            String resumeHash,
            Map<String, Object> jobSnapshot,
            String jobSnapshotHash,
            CoverNotesThreeTonesDto notesThreeTones,
            List<QuestionClassifierAndAnswerService.QuestionResolutionDto> answers,
            TruthfulnessVerifier.VerificationReport truthfulnessReport,
            boolean truthfulnessPassed,
            int readinessScore,
            ReadinessBreakdownDto readinessBreakdown,
            boolean routesToReviewQueue,
            Instant updatedAt
    ) {}

    private final ApplicationPackRepository packRepository;
    private final JobRepository jobRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final CareerProfileRepository profileRepository;
    private final JobMatchRepository jobMatchRepository;
    private final MatchScoringService matchScoringService;
    private final QuestionClassifierAndAnswerService answerService;
    private final TruthfulnessVerifier truthfulnessVerifier;
    private final ObjectMapper objectMapper;

    public ApplicationPackService(
            ApplicationPackRepository packRepository,
            JobRepository jobRepository,
            ResumeVersionRepository resumeVersionRepository,
            CareerProfileRepository profileRepository,
            JobMatchRepository jobMatchRepository,
            MatchScoringService matchScoringService,
            QuestionClassifierAndAnswerService answerService,
            TruthfulnessVerifier truthfulnessVerifier,
            ObjectMapper objectMapper
    ) {
        this.packRepository = packRepository;
        this.jobRepository = jobRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.profileRepository = profileRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.matchScoringService = matchScoringService;
        this.answerService = answerService;
        this.truthfulnessVerifier = truthfulnessVerifier;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationPackDto getOrBuildPack(
            UUID userId,
            UUID jobId,
            List<String> customQuestions,
            String customNoteOverride,
            Integer userThresholdOverride
    ) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        ResumeVersion resumeVersion = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                .orElseGet(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .findFirst()
                        .orElse(null));

        CareerProfile profile = profileRepository.findByUserId(userId).orElse(null);

        List<String> verifiedSkills = resumeVersion != null
                ? parseStringList(resumeVersion.getVerifiedSkillsJson())
                : List.of();
        List<String> profileSkills = profile != null
                ? parseStringList(profile.getSkills())
                : List.of();
        Double candidateYears = resumeVersion != null && resumeVersion.getYearsOfExperience() != null
                ? resumeVersion.getYearsOfExperience()
                : (profile != null ? profile.getYearsOfExperience() : null);

        List<String> jobReqSkills = parseStringList(job.getRequiredSkillsJson());
        List<String> jobNiceSkills = parseStringList(job.getNiceToHaveSkillsJson());

        // 1. Compute or fetch match score
        int matchScore = jobMatchRepository.findFirstByJobIdAndUserIdOrderByCreatedAtDesc(job.getId(), userId)
                .map(JobMatch::getTotalScore)
                .orElseGet(() -> {
                    List<String> combinedSkills = new ArrayList<>(verifiedSkills);
                    for (String ps : profileSkills) {
                        if (!combinedSkills.contains(ps)) combinedSkills.add(ps);
                    }
                    MatchResultDto mr = matchScoringService.computeMatch(
                            job.getId(),
                            resumeVersion != null ? resumeVersion.getId() : null,
                            combinedSkills,
                            candidateYears,
                            jobReqSkills,
                            jobNiceSkills,
                            job.getMinYearsExperience(),
                            job.getLocation(),
                            job.getWorkMode(),
                            profile != null ? parseStringList(profile.getCities()) : List.of(),
                            profile != null ? parseStringList(profile.getWorkModes()) : List.of()
                    );
                    return mr.score();
                });

        // 2. Job snapshot & SHA-256 hash
        Map<String, Object> jobSnapshot = new LinkedHashMap<>();
        jobSnapshot.put("jobId", job.getId().toString());
        jobSnapshot.put("title", job.getTitle());
        jobSnapshot.put("company", job.getCompany());
        jobSnapshot.put("location", job.getLocation() != null ? job.getLocation() : "");
        jobSnapshot.put("workMode", job.getWorkMode() != null ? job.getWorkMode() : "");
        jobSnapshot.put("source", job.getSource());
        jobSnapshot.put("sourceUrl", job.getSourceUrl() != null ? job.getSourceUrl() : "");
        jobSnapshot.put("stale", job.isStale());
        String jobSnapshotHash = sha256Hex(
                job.getTitle() + "|" + job.getCompany() + "|" + job.getLocation() + "|" + job.getRawDescription()
        );

        String resumeHash = resumeVersion != null
                ? sha256Hex(resumeVersion.getExtractedText())
                : null;

        // 3. Cover notes in 3 tones grounded strictly in candidate's verified skills & experience
        List<String> groundedSkills = !verifiedSkills.isEmpty() ? verifiedSkills : profileSkills;
        String skillsPhrase = groundedSkills.isEmpty()
                ? "my background and experience"
                : String.join(", ", groundedSkills.subList(0, Math.min(4, groundedSkills.size())));
        String yearsPhrase = candidateYears != null
                ? String.format(Locale.US, "%.1f years of experience", candidateYears)
                : "relevant professional experience";

        String formalNote = "Dear Hiring Team at " + job.getCompany() + ", I am submitting my application for the "
                + job.getTitle() + " position. I bring " + yearsPhrase + " with verified hands-on experience in "
                + skillsPhrase + ". I look forward to discussing how my background aligns with your requirements.";
        String conciseNote = "Applying for " + job.getTitle() + " at " + job.getCompany()
                + " — bringing " + yearsPhrase + " and verified proficiency in " + skillsPhrase + ".";
        String enthusiasticNote = "Hello " + job.getCompany() + " Team! I am excited to apply for the "
                + job.getTitle() + " role, where I can apply my " + yearsPhrase + " in " + skillsPhrase + ".";

        CoverNotesThreeTonesDto notesDto = new CoverNotesThreeTonesDto(
                formalNote,
                conciseNote,
                enthusiasticNote,
                customNoteOverride
        );

        // 4. Resolve questions with confidence and source
        List<String> questionsToResolve = (customQuestions != null && !customQuestions.isEmpty())
                ? customQuestions
                : List.of(
                        "What is your current city?",
                        "What is your preferred work mode (remote or hybrid)?"
                );
        List<QuestionClassifierAndAnswerService.QuestionResolutionDto> resolvedAnswers = questionsToResolve.stream()
                .map(q -> answerService.resolveQuestion(userId, q))
                .toList();

        // 5. Truthfulness verification across notes & answers
        StringBuilder textToVerify = new StringBuilder(formalNote)
                .append("\n").append(conciseNote)
                .append("\n").append(enthusiasticNote);
        if (customNoteOverride != null && !customNoteOverride.isBlank()) {
            textToVerify.append("\n").append(customNoteOverride);
        }
        for (var ans : resolvedAnswers) {
            if (ans.approvedAnswer() != null) {
                textToVerify.append("\n").append(ans.approvedAnswer());
            }
        }

        List<String> watchSkills = new ArrayList<>(jobReqSkills);
        watchSkills.addAll(jobNiceSkills);

        TruthfulnessVerifier.VerificationReport truthReport = truthfulnessVerifier.verify(
                resumeVersion != null ? resumeVersion.getExtractedText() : "",
                verifiedSkills,
                profileSkills,
                candidateYears,
                watchSkills,
                textToVerify.toString()
        );

        // 6. Readiness Score (0-100):
        // - Match score component (0-40)
        // - Answer coverage component (0-25)
        // - Resume fit & truthfulness component (0-20)
        // - Freshness component (0-15)
        int matchComponent = Math.min(40, Math.max(0, Math.round(matchScore * 0.40f)));

        long answeredWithoutPause = resolvedAnswers.stream()
                .filter(a -> !a.requiresUserPause() && a.approvedAnswer() != null && !a.approvedAnswer().isBlank())
                .count();
        int answerCoverageComponent = resolvedAnswers.isEmpty()
                ? 25
                : Math.round(25.0f * answeredWithoutPause / resolvedAnswers.size());

        int resumeFitComponent = (resumeVersion != null && truthReport.passed()) ? 20 : 0;

        int freshnessComponent;
        if (job.isStale()) {
            freshnessComponent = 0;
        } else {
            Instant posted = job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt();
            long daysOld = posted != null ? ChronoUnit.DAYS.between(posted, Instant.now()) : 0;
            if (daysOld <= 14) freshnessComponent = 15;
            else if (daysOld <= 30) freshnessComponent = 10;
            else freshnessComponent = 5;
        }

        int totalReadiness = Math.min(100, Math.max(0,
                matchComponent + answerCoverageComponent + resumeFitComponent + freshnessComponent));
        int threshold = userThresholdOverride != null ? userThresholdOverride : 70;

        List<String> routingReasons = new ArrayList<>();
        if (!truthReport.passed()) {
            routingReasons.add("Blocked unsupported claim(s): " + String.join("; ", truthReport.unsupportedClaims()));
        }
        if (resumeVersion == null) {
            routingReasons.add("No verified resume version uploaded yet");
        }
        if (job.isStale()) {
            routingReasons.add("Job listing is flagged stale/closed");
        }
        boolean anyQuestionPaused = resolvedAnswers.stream().anyMatch(QuestionClassifierAndAnswerService.QuestionResolutionDto::requiresUserPause);
        if (anyQuestionPaused) {
            routingReasons.add("One or more application questions require user review/input");
        }
        if (totalReadiness < threshold) {
            routingReasons.add("Readiness score (" + totalReadiness + ") is below threshold (" + threshold + ")");
        }

        boolean routesToReview = !routingReasons.isEmpty();

        ReadinessBreakdownDto breakdownDto = new ReadinessBreakdownDto(
                matchComponent,
                answerCoverageComponent,
                resumeFitComponent,
                freshnessComponent,
                totalReadiness,
                threshold,
                routesToReview,
                routingReasons
        );

        ApplicationPack pack = packRepository.findByUserIdAndJobId(userId, job.getId())
                .orElseGet(() -> {
                    ApplicationPack p = new ApplicationPack();
                    p.setUserId(userId);
                    p.setJobId(job.getId());
                    return p;
                });

        pack.setResumeVersionId(resumeVersion != null ? resumeVersion.getId() : null);
        pack.setResumeHash(resumeHash);
        pack.setJobSnapshotJson(writeJson(jobSnapshot));
        pack.setJobSnapshotHash(jobSnapshotHash);
        pack.setNotesThreeTonesJson(writeJson(notesDto));
        pack.setAnswersJson(writeJson(resolvedAnswers));
        pack.setTruthfulnessReportJson(writeJson(truthReport));
        pack.setTruthfulnessPassed(truthReport.passed());
        pack.setReadinessScore(totalReadiness);
        pack.setReadinessBreakdownJson(writeJson(breakdownDto));
        pack.setRoutesToReviewQueue(routesToReview);
        pack = packRepository.save(pack);

        return new ApplicationPackDto(
                pack.getId(),
                job.getId(),
                pack.getResumeVersionId(),
                resumeVersion != null ? resumeVersion.getOriginalFilename() : null,
                pack.getResumeHash(),
                jobSnapshot,
                pack.getJobSnapshotHash(),
                notesDto,
                resolvedAnswers,
                truthReport,
                pack.isTruthfulnessPassed(),
                pack.getReadinessScore(),
                breakdownDto,
                pack.isRoutesToReviewQueue(),
                pack.getUpdatedAt()
        );
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((input != null ? input : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
