package in.skillsignal.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.AiClient;
import in.skillsignal.ai.ParsedJobData;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.source.GreenhousePublicBoardConnector;
import in.skillsignal.source.SourceCapability;
import in.skillsignal.source.SourceCapabilityRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class JobSearchOrchestrator {

    public static final String COVERAGE_DISCLAIMER =
            "Search results reflect only enabled, permitted sources queried during this run and never claim full market coverage.";

    private final JobRepository jobRepository;
    private final SourceCapabilityRepository capabilityRepository;
    private final GreenhousePublicBoardConnector greenhouseConnector;
    private final ApplicationRepository applicationRepository;
    private final NaturalLanguageQueryParser queryParser;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService searchExecutor = Executors.newCachedThreadPool();

    public JobSearchOrchestrator(
            JobRepository jobRepository,
            SourceCapabilityRepository capabilityRepository,
            GreenhousePublicBoardConnector greenhouseConnector,
            ApplicationRepository applicationRepository,
            NaturalLanguageQueryParser queryParser,
            AiClient aiClient,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.capabilityRepository = capabilityRepository;
        this.greenhouseConnector = greenhouseConnector;
        this.applicationRepository = applicationRepository;
        this.queryParser = queryParser;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public record SourceExecutionReport(
            String source,
            String status,
            int jobsFound,
            long durationMs,
            String errorReason
    ) {}

    public record JobSearchResultItem(
            UUID id,
            String title,
            String company,
            String location,
            String workMode,
            String source,
            String sourceUrl,
            String employmentType,
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            Double minYearsExperience,
            List<String> requiredSkills,
            List<String> niceToHaveSkills,
            List<Map<String, Object>> sourceRecords,
            boolean saved,
            boolean stale,
            String staleReason,
            boolean applied,
            Instant postedAt
    ) {}

    public record JobSearchResponse(
            JobSearchFilters appliedFilters,
            List<JobSearchFilters.FilterChip> filterChips,
            List<JobSearchResultItem> jobs,
            List<SourceExecutionReport> sourcesSearched,
            List<SourceExecutionReport> sourcesFailed,
            long totalDurationMs,
            String coverageDisclaimer
    ) {}

    public JobSearchResponse search(UUID userId, JobSearchFilters inputFilters) {
        long overallStart = System.currentTimeMillis();
        JobSearchFilters filters = inputFilters != null ? inputFilters : new JobSearchFilters();

        if (filters.getRawQuery() != null && !filters.getRawQuery().isBlank()
                && filters.getKeywords().isEmpty()
                && filters.getSkills().isEmpty()
                && filters.getLocations().isEmpty()
                && filters.getWorkMode() == null
                && filters.getMinExperienceYears() == null
                && filters.getMinSalaryLpa() == null
                && filters.getPostedWithinDays() == null
                && filters.getCompany() == null
                && filters.getEmploymentType() == null) {
            NaturalLanguageQueryParser.ParseQueryResult parsed = queryParser.parse(filters.getRawQuery());
            JobSearchFilters pf = parsed.filters();
            pf.setGreenhouseBoards(filters.getGreenhouseBoards());
            pf.setPerSourceTimeoutMs(filters.getPerSourceTimeoutMs());
            if (!filters.getSources().isEmpty()) {
                pf.setSources(filters.getSources());
            }
            filters = pf;
        }

        long timeoutMs = filters.getPerSourceTimeoutMs() != null && filters.getPerSourceTimeoutMs() > 0
                ? filters.getPerSourceTimeoutMs()
                : 2500L;

        List<String> requestedSources = filters.getSources() != null && !filters.getSources().isEmpty()
                ? new ArrayList<>(filters.getSources())
                : List.of("manual", "company_ats");

        List<SourceExecutionReport> sourcesSearched = Collections.synchronizedList(new ArrayList<>());
        List<SourceExecutionReport> sourcesFailed = Collections.synchronizedList(new ArrayList<>());
        List<GreenhousePublicBoardConnector.PublicBoardJob> discoveredAtsPostings = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (String sourceName : requestedSources) {
            String normalizedSource = sourceName.trim().toLowerCase(Locale.ROOT);
            Optional<SourceCapability> capOpt = capabilityRepository.findBySourceIgnoreCase(normalizedSource);
            if (capOpt.isEmpty() || !capOpt.get().isEnabled()) {
                String reason = capOpt.map(c -> "Source disabled in capability registry: no verified API access (" + c.getAccessMethod() + ")")
                        .orElse("Unknown source: " + normalizedSource);
                sourcesFailed.add(new SourceExecutionReport(normalizedSource, "FAILED", 0, 0L, reason));
                continue;
            }

            if ("company_ats".equals(normalizedSource)) {
                List<String> boards = filters.getGreenhouseBoards() != null && !filters.getGreenhouseBoards().isEmpty()
                        ? filters.getGreenhouseBoards()
                        : List.of("demo-india");
                for (String board : boards) {
                    String taskLabel = "company_ats:" + board;
                    long taskStart = System.currentTimeMillis();
                    CompletableFuture<Void> cf = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return greenhouseConnector.fetchBoardJobs(board, Duration.ofMillis(timeoutMs));
                                } catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                            }, searchExecutor)
                            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .thenAccept(postings -> {
                                long dur = System.currentTimeMillis() - taskStart;
                                discoveredAtsPostings.addAll(postings);
                                sourcesSearched.add(new SourceExecutionReport(taskLabel, "SUCCESS", postings.size(), dur, null));
                            })
                            .exceptionally(ex -> {
                                long dur = System.currentTimeMillis() - taskStart;
                                Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                                String msg = cause instanceof TimeoutException
                                        ? "Timed out after " + timeoutMs + "ms"
                                        : (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                                sourcesFailed.add(new SourceExecutionReport(taskLabel, "FAILED", 0, dur, msg));
                                return null;
                            });
                    tasks.add(cf);
                }
            } else if ("manual".equals(normalizedSource) || "email_apply".equals(normalizedSource)) {
                long taskStart = System.currentTimeMillis();
                CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
                            int count = (int) jobRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                                    .filter(j -> normalizedSource.equalsIgnoreCase(j.getSource()))
                                    .count();
                            long dur = System.currentTimeMillis() - taskStart;
                            sourcesSearched.add(new SourceExecutionReport(normalizedSource, "SUCCESS", count, dur, null));
                        }, searchExecutor)
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            long dur = System.currentTimeMillis() - taskStart;
                            sourcesFailed.add(new SourceExecutionReport(normalizedSource, "FAILED", 0, dur, ex.getMessage()));
                            return null;
                        });
                tasks.add(cf);
            }
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

        if (!discoveredAtsPostings.isEmpty()) {
            ingestPublicPostingsForUser(userId, discoveredAtsPostings);
        }

        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<UUID> appliedJobIds = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(Application::getJobId)
                .collect(Collectors.toSet());

        final JobSearchFilters activeFilters = filters;
        List<JobSearchResultItem> filtered = userJobs.stream()
                .filter(job -> matchesFilters(job, activeFilters, appliedJobIds))
                .map(job -> toResultItem(job, appliedJobIds.contains(job.getId())))
                .toList();

        long totalDurationMs = System.currentTimeMillis() - overallStart;
        return new JobSearchResponse(
                filters,
                queryParser.buildChips(filters),
                filtered,
                new ArrayList<>(sourcesSearched),
                new ArrayList<>(sourcesFailed),
                totalDurationMs,
                COVERAGE_DISCLAIMER
        );
    }

    public synchronized void ingestPublicPostingsForUser(
            UUID userId,
            List<GreenhousePublicBoardConnector.PublicBoardJob> postings
    ) {
        List<Job> existingJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);

        for (GreenhousePublicBoardConnector.PublicBoardJob p : postings) {
            String dedupKey = computeDedupKey(p.company(), p.title(), p.location());

            Optional<Job> matchOpt = existingJobs.stream()
                    .filter(j -> (p.externalId() != null && p.externalId().equalsIgnoreCase(j.getExternalJobId()))
                            || (dedupKey.equalsIgnoreCase(j.getDedupKey()))
                            || (dedupKey.equalsIgnoreCase(computeDedupKey(j.getCompany(), j.getTitle(), j.getLocation()))))
                    .findFirst();

            Map<String, Object> sourceEntry = new LinkedHashMap<>();
            sourceEntry.put("source", "company_ats");
            sourceEntry.put("externalId", p.externalId());
            sourceEntry.put("url", p.sourceUrl());
            sourceEntry.put("updatedAt", p.postedAt() != null ? p.postedAt().toString() : Instant.now().toString());

            if (matchOpt.isPresent()) {
                Job existing = matchOpt.get();
                List<Map<String, Object>> records = parseSourceRecords(existing.getSourceRecordsJson());
                boolean alreadyRecorded = records.stream()
                        .anyMatch(r -> Objects.equals(r.get("externalId"), p.externalId())
                                && Objects.equals(r.get("source"), "company_ats"));
                if (!alreadyRecorded) {
                    records.add(sourceEntry);
                    existing.setSourceRecordsJson(writeJson(records));
                }
                if (existing.getDedupKey() == null) {
                    existing.setDedupKey(dedupKey);
                }
                existing.setUpdatedAt(Instant.now());
                jobRepository.save(existing);
            } else {
                ParsedJobData parsed = aiClient.parseJobDescription(p.rawDescription(), p.title(), p.company());
                Job job = new Job();
                job.setUserId(userId);
                job.setTitle(p.title());
                job.setCompany(p.company());
                job.setLocation(p.location());
                job.setWorkMode(p.workMode() != null ? p.workMode() : (parsed.workMode() != null ? parsed.workMode() : "ONSITE"));
                job.setSource("company_ats");
                job.setSourceUrl(p.sourceUrl());
                job.setExternalJobId(p.externalId());
                job.setRawDescription(p.rawDescription());
                job.setParsedJson(writeJson(parsed));
                job.setRequiredSkillsJson(writeJson(parsed.requiredSkills()));
                job.setNiceToHaveSkillsJson(writeJson(parsed.niceToHaveSkills()));
                job.setMinYearsExperience(parsed.minYearsExperience());
                job.setEmploymentType(extractEmploymentType(p.rawDescription()));
                job.setSalaryMin(p.salaryMin());
                job.setSalaryMax(p.salaryMax());
                job.setSalaryCurrency(p.salaryCurrency() != null ? p.salaryCurrency() : "INR");
                job.setSalaryPeriod(p.salaryPeriod() != null ? p.salaryPeriod() : "LPA");
                job.setPostedAt(p.postedAt() != null ? p.postedAt() : Instant.now());
                job.setDedupKey(dedupKey);
                job.setSourceRecordsJson(writeJson(List.of(sourceEntry)));
                job.setStale(false);
                job.setSaved(false);
                Job saved = jobRepository.save(job);
                existingJobs.add(saved);
            }
        }
    }

    public static String computeDedupKey(String company, String title, String location) {
        String normCompany = normalizeText(company);
        String normTitle = normalizeFuzzyTitle(title);
        String normLocation = normalizeCity(location);
        return normCompany + "|" + normTitle + "|" + normLocation;
    }

    private static String normalizeText(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(pvt|ltd|private|limited|inc|corp|llp|india|technologies|solutions)\\b", "")
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }

    private static String normalizeFuzzyTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("\\bsr\\.?\\b", "senior")
                .replaceAll("\\bjr\\.?\\b", "junior")
                .replaceAll("\\bsde\\b", "softwaredevelopmentengineer")
                .replaceAll("\\bswe\\b", "softwareengineer")
                .replaceAll("\\bdev\\b", "developer")
                .replaceAll("\\beng\\b", "engineer")
                .replaceAll("\\b(i|ii|iii|1|2|3)\\b", "")
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }

    public static String normalizeCity(String location) {
        if (location == null || location.isBlank()) return "unspecified";
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.contains("bengaluru") || lower.contains("bangalore")) return "bengaluru";
        if (lower.contains("gurugram") || lower.contains("gurgaon")) return "gurugram";
        if (lower.contains("mumbai") || lower.contains("bombay")) return "mumbai";
        if (lower.contains("chennai") || lower.contains("madras")) return "chennai";
        if (lower.contains("hyderabad")) return "hyderabad";
        if (lower.contains("pune")) return "pune";
        if (lower.contains("noida") || lower.contains("delhi") || lower.contains("ncr")) return "delhincr";
        if (lower.contains("remote")) return "remote";
        return lower.replaceAll("[^a-z0-9]+", "");
    }

    private boolean matchesFilters(Job job, JobSearchFilters f, Set<UUID> appliedJobIds) {
        String combinedText = ((job.getTitle() != null ? job.getTitle() : "") + " "
                + (job.getCompany() != null ? job.getCompany() : "") + " "
                + (job.getRawDescription() != null ? job.getRawDescription() : "")).toLowerCase(Locale.ROOT);

        if (f.getKeywords() != null && !f.getKeywords().isEmpty()) {
            boolean anyKeyword = f.getKeywords().stream()
                    .anyMatch(kw -> combinedText.contains(kw.toLowerCase(Locale.ROOT)));
            if (!anyKeyword) return false;
        }

        if (f.getSkills() != null && !f.getSkills().isEmpty()) {
            List<String> jobSkills = new ArrayList<>();
            jobSkills.addAll(parseStringList(job.getRequiredSkillsJson()));
            jobSkills.addAll(parseStringList(job.getNiceToHaveSkillsJson()));
            Set<String> jobSkillsLower = jobSkills.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            boolean anySkill = f.getSkills().stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .anyMatch(reqSkill -> jobSkillsLower.contains(reqSkill) || combinedText.contains(reqSkill));
            if (!anySkill) return false;
        }

        if (f.getMaxExperienceYears() != null && job.getMinYearsExperience() != null) {
            if (job.getMinYearsExperience() > f.getMaxExperienceYears()) {
                return false;
            }
        }
        if (f.getMinExperienceYears() != null && f.getMaxExperienceYears() == null && job.getMinYearsExperience() != null) {
            if (job.getMinYearsExperience() < Math.max(0, f.getMinExperienceYears() - 2)) {
                return false;
            }
        }

        if (f.getLocations() != null && !f.getLocations().isEmpty()) {
            String jobLocNorm = normalizeCity(job.getLocation());
            boolean anyLoc = f.getLocations().stream()
                    .anyMatch(loc -> normalizeCity(loc).equals(jobLocNorm)
                            || (job.getLocation() != null && job.getLocation().toLowerCase(Locale.ROOT).contains(loc.toLowerCase(Locale.ROOT))));
            if (!anyLoc) return false;
        }

        if (f.getWorkMode() != null && !f.getWorkMode().isBlank()) {
            if (job.getWorkMode() == null || !job.getWorkMode().equalsIgnoreCase(f.getWorkMode())) {
                return false;
            }
        }

        if (Boolean.TRUE.equals(f.getDisclosedSalaryOnly())) {
            if (job.getSalaryMin() == null && job.getSalaryMax() == null) {
                return false;
            }
        }
        if (f.getMinSalaryLpa() != null) {
            Double compareSal = job.getSalaryMax() != null ? job.getSalaryMax() : job.getSalaryMin();
            if (compareSal == null || compareSal < f.getMinSalaryLpa()) {
                return false;
            }
        }

        if (f.getPostedWithinDays() != null && f.getPostedWithinDays() > 0) {
            Instant cutoff = Instant.now().minus(f.getPostedWithinDays(), ChronoUnit.DAYS);
            Instant jobTime = job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt();
            if (jobTime != null && jobTime.isBefore(cutoff)) {
                return false;
            }
        }

        if (f.getCompany() != null && !f.getCompany().isBlank()) {
            if (job.getCompany() == null || !job.getCompany().toLowerCase(Locale.ROOT).contains(f.getCompany().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        if (f.getSources() != null && !f.getSources().isEmpty()) {
            Set<String> reqSources = f.getSources().stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            String jSource = job.getSource() != null ? job.getSource().toLowerCase(Locale.ROOT) : "manual";
            boolean inRecords = parseSourceRecords(job.getSourceRecordsJson()).stream()
                    .map(r -> String.valueOf(r.getOrDefault("source", "")).toLowerCase(Locale.ROOT))
                    .anyMatch(reqSources::contains);
            if (!reqSources.contains(jSource) && !inRecords) {
                return false;
            }
        }

        if (f.getEmploymentType() != null && !f.getEmploymentType().isBlank()) {
            String jType = job.getEmploymentType() != null ? job.getEmploymentType() : "FULL_TIME";
            if (!jType.equalsIgnoreCase(f.getEmploymentType())) {
                return false;
            }
        }

        if (Boolean.TRUE.equals(f.getSavedOnly()) && !job.isSaved()) {
            return false;
        }

        if (f.getAppliedStatus() != null && !f.getAppliedStatus().isBlank()) {
            boolean isApplied = appliedJobIds.contains(job.getId());
            if ("APPLIED".equalsIgnoreCase(f.getAppliedStatus()) && !isApplied) return false;
            if ("NOT_APPLIED".equalsIgnoreCase(f.getAppliedStatus()) && isApplied) return false;
        }

        return true;
    }

    private JobSearchResultItem toResultItem(Job job, boolean isApplied) {
        return new JobSearchResultItem(
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getWorkMode(),
                job.getSource(),
                job.getSourceUrl(),
                job.getEmploymentType(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getSalaryPeriod(),
                job.getMinYearsExperience(),
                parseStringList(job.getRequiredSkillsJson()),
                parseStringList(job.getNiceToHaveSkillsJson()),
                parseSourceRecords(job.getSourceRecordsJson()),
                job.isSaved(),
                job.isStale(),
                job.getStaleReason(),
                isApplied,
                job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt()
        );
    }

    private static String extractEmploymentType(String text) {
        if (text == null) return "FULL_TIME";
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("internship") || lower.contains("intern ")) return "INTERNSHIP";
        if (lower.contains("contract") || lower.contains("freelance")) return "CONTRACT";
        if (lower.contains("part-time") || lower.contains("part time")) return "PART_TIME";
        return "FULL_TIME";
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseSourceRecords(String json) {
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
