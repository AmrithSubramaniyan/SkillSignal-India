package in.skillsignal.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.AiClient;
import in.skillsignal.ai.ParsedJobData;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class JobDedupAndStaleService {

    private final JobRepository jobRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public JobDedupAndStaleService(
            JobRepository jobRepository,
            AiClient aiClient,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public record IngestJobInput(
            String title,
            String company,
            String location,
            String workMode,
            String source,
            String externalJobId,
            String sourceUrl,
            String rawDescription,
            List<String> requiredSkills,
            List<String> niceToHaveSkills,
            Double minYearsExperience,
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            String requiredEducation
    ) {}

    public record DedupMergeResult(
            Job job,
            boolean mergedIntoExisting,
            int totalSourceRecords,
            List<Map<String, Object>> sourceRecords
    ) {}

    public record StaleVerificationResult(
            UUID jobId,
            boolean stale,
            String staleReason,
            Instant verifiedAt
    ) {}

    @Transactional
    public DedupMergeResult ingestOrMergeJob(UUID userId, IngestJobInput input) {
        ParsedJobData parsed = aiClient.parseJobDescription(
                input.rawDescription() != null ? input.rawDescription() : "",
                input.title(),
                input.company()
        );

        String title = nonBlankOr(input.title(), parsed.title());
        String company = nonBlankOr(input.company(), parsed.company());
        String location = nonBlankOr(input.location(), parsed.location());
        String workMode = nonBlankOr(input.workMode(), parsed.workMode());
        String source = nonBlankOr(input.source(), "manual").toLowerCase(Locale.ROOT);
        String dedupKey = JobSearchOrchestrator.computeDedupKey(company, title, location);

        List<String> reqSkills = (input.requiredSkills() != null && !input.requiredSkills().isEmpty())
                ? input.requiredSkills()
                : (parsed.requiredSkills() != null ? parsed.requiredSkills() : List.of());
        List<String> niceSkills = (input.niceToHaveSkills() != null && !input.niceToHaveSkills().isEmpty())
                ? input.niceToHaveSkills()
                : (parsed.niceToHaveSkills() != null ? parsed.niceToHaveSkills() : List.of());
        Double minYears = input.minYearsExperience() != null ? input.minYearsExperience() : parsed.minYearsExperience();

        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Optional<Job> duplicateOpt = userJobs.stream()
                .filter(j -> dedupKey.equalsIgnoreCase(j.getDedupKey())
                        || dedupKey.equalsIgnoreCase(JobSearchOrchestrator.computeDedupKey(j.getCompany(), j.getTitle(), j.getLocation())))
                .findFirst();

        Map<String, Object> newSourceRecord = new LinkedHashMap<>();
        newSourceRecord.put("source", source);
        newSourceRecord.put("externalId", input.externalJobId() != null ? input.externalJobId() : "");
        newSourceRecord.put("url", input.sourceUrl() != null ? input.sourceUrl() : "");
        newSourceRecord.put("titleVariant", title);
        newSourceRecord.put("locationVariant", location);
        newSourceRecord.put("observedAt", Instant.now().toString());

        if (duplicateOpt.isPresent()) {
            Job existing = duplicateOpt.get();
            List<Map<String, Object>> sourceRecords = parseSourceRecords(existing.getSourceRecordsJson());
            if (sourceRecords.isEmpty()) {
                // Backfill original record if it wasn't populated yet
                Map<String, Object> initialRecord = new LinkedHashMap<>();
                initialRecord.put("source", existing.getSource() != null ? existing.getSource() : "manual");
                initialRecord.put("externalId", existing.getExternalJobId() != null ? existing.getExternalJobId() : "");
                initialRecord.put("url", existing.getSourceUrl() != null ? existing.getSourceUrl() : "");
                initialRecord.put("titleVariant", existing.getTitle());
                initialRecord.put("locationVariant", existing.getLocation());
                initialRecord.put("observedAt", existing.getCreatedAt() != null ? existing.getCreatedAt().toString() : Instant.now().toString());
                sourceRecords.add(initialRecord);
            }
            sourceRecords.add(newSourceRecord);
            existing.setSourceRecordsJson(writeJson(sourceRecords));
            existing.setDedupKey(dedupKey);

            // Merge skills without losing existing skills
            Set<String> mergedReq = new LinkedHashSet<>(parseStringList(existing.getRequiredSkillsJson()));
            mergedReq.addAll(reqSkills);
            existing.setRequiredSkillsJson(writeJson(new ArrayList<>(mergedReq)));

            Set<String> mergedNice = new LinkedHashSet<>(parseStringList(existing.getNiceToHaveSkillsJson()));
            mergedNice.addAll(niceSkills);
            existing.setNiceToHaveSkillsJson(writeJson(new ArrayList<>(mergedNice)));

            if (existing.getSalaryMin() == null && input.salaryMin() != null) existing.setSalaryMin(input.salaryMin());
            if (existing.getSalaryMax() == null && input.salaryMax() != null) existing.setSalaryMax(input.salaryMax());
            if (existing.getSalaryCurrency() == null && input.salaryCurrency() != null) existing.setSalaryCurrency(input.salaryCurrency());
            if (existing.getSalaryPeriod() == null && input.salaryPeriod() != null) existing.setSalaryPeriod(input.salaryPeriod());
            if (existing.getRequiredEducation() == null && input.requiredEducation() != null) existing.setRequiredEducation(input.requiredEducation());
            if (existing.getMinYearsExperience() == null && minYears != null) existing.setMinYearsExperience(minYears);
            if ((existing.getSourceUrl() == null || existing.getSourceUrl().isBlank()) && input.sourceUrl() != null) {
                existing.setSourceUrl(input.sourceUrl());
            }

            Job saved = jobRepository.save(existing);
            return new DedupMergeResult(saved, true, sourceRecords.size(), sourceRecords);
        }

        Job job = new Job();
        job.setUserId(userId);
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setWorkMode(workMode != null ? workMode : "ONSITE");
        job.setSource(source);
        job.setExternalJobId(input.externalJobId());
        job.setSourceUrl(input.sourceUrl());
        job.setRawDescription(input.rawDescription() != null ? input.rawDescription().trim() : title + " at " + company);
        job.setParsedJson(writeJson(parsed));
        job.setRequiredSkillsJson(writeJson(reqSkills));
        job.setNiceToHaveSkillsJson(writeJson(niceSkills));
        job.setMinYearsExperience(minYears);
        job.setSalaryMin(input.salaryMin());
        job.setSalaryMax(input.salaryMax());
        job.setSalaryCurrency(input.salaryCurrency() != null ? input.salaryCurrency() : "INR");
        job.setSalaryPeriod(input.salaryPeriod() != null ? input.salaryPeriod() : "LPA");
        job.setRequiredEducation(input.requiredEducation());
        job.setDedupKey(dedupKey);
        List<Map<String, Object>> initialRecords = List.of(newSourceRecord);
        job.setSourceRecordsJson(writeJson(initialRecords));
        job.setStale(false);
        job.setSaved(true);
        Job saved = jobRepository.save(job);

        return new DedupMergeResult(saved, false, 1, initialRecords);
    }

    /**
     * Golden Rule (Stage 11): Stale flag is set ONLY on reliable evidence (HTTP 404/410 or explicit closed flag).
     * Never mark a job stale due to age, transient 5xx/timeout errors, or heuristics.
     */
    @Transactional
    public StaleVerificationResult verifyJobStaleness(
            UUID userId,
            UUID jobId,
            Integer observedHttpStatus,
            Boolean explicitClosedFlag
    ) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (Boolean.TRUE.equals(explicitClosedFlag)) {
            job.setStale(true);
            job.setStaleReason("Explicit closed flag reported by source");
        } else if (observedHttpStatus != null && (observedHttpStatus == 404 || observedHttpStatus == 410)) {
            job.setStale(true);
            job.setStaleReason("HTTP " + observedHttpStatus + " Not Found from source endpoint");
        } else if (observedHttpStatus != null && observedHttpStatus == 200 && Boolean.FALSE.equals(explicitClosedFlag)) {
            job.setStale(false);
            job.setStaleReason(null);
        }
        // Note: For 500, 503, 429, null, or old postedAt, we deliberately do NOT set isStale=true.

        job = jobRepository.save(job);
        return new StaleVerificationResult(job.getId(), job.isStale(), job.getStaleReason(), Instant.now());
    }

    @Transactional
    public Job toggleSavedJob(UUID userId, UUID jobId, boolean saved) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        job.setSaved(saved);
        return jobRepository.save(job);
    }

    public List<Job> listSavedJobs(UUID userId) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(Job::isSaved)
                .toList();
    }

    private static String nonBlankOr(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary.trim() : (fallback != null ? fallback.trim() : "");
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> parseSourceRecords(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
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
