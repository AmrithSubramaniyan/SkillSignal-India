package in.skillsignal.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SavedSearchService {

    private static final Logger log = LoggerFactory.getLogger(SavedSearchService.class);

    private final SavedSearchRepository savedSearchRepository;
    private final NaturalLanguageQueryParser queryParser;
    private final JobSearchOrchestrator searchOrchestrator;
    private final ObjectMapper objectMapper;

    public SavedSearchService(
            SavedSearchRepository savedSearchRepository,
            NaturalLanguageQueryParser queryParser,
            JobSearchOrchestrator searchOrchestrator,
            ObjectMapper objectMapper
    ) {
        this.savedSearchRepository = savedSearchRepository;
        this.queryParser = queryParser;
        this.searchOrchestrator = searchOrchestrator;
        this.objectMapper = objectMapper;
    }

    public record CreateSavedSearchRequest(
            String name,
            String rawQuery,
            JobSearchFilters filters,
            Boolean dailyRefreshEnabled
    ) {}

    public record SavedSearchDto(
            UUID id,
            String name,
            String rawQuery,
            JobSearchFilters filters,
            boolean dailyRefreshEnabled,
            Instant lastRefreshedAt,
            int lastMatchCount,
            Instant createdAt
    ) {}

    @Transactional
    public SavedSearchDto createSavedSearch(UUID userId, CreateSavedSearchRequest req) {
        JobSearchFilters filters = req.filters();
        if (filters == null && req.rawQuery() != null && !req.rawQuery().isBlank()) {
            filters = queryParser.parse(req.rawQuery()).filters();
        }
        if (filters == null) {
            filters = new JobSearchFilters();
        }

        JobSearchOrchestrator.JobSearchResponse initialRun = searchOrchestrator.search(userId, filters);

        SavedSearch entity = new SavedSearch();
        entity.setUserId(userId);
        entity.setName(req.name() != null && !req.name().isBlank() ? req.name().trim() : "Saved Job Search");
        entity.setRawQuery(req.rawQuery());
        entity.setFiltersJson(writeFiltersJson(initialRun.appliedFilters()));
        entity.setDailyRefreshEnabled(req.dailyRefreshEnabled() == null || req.dailyRefreshEnabled());
        entity.setLastRefreshedAt(Instant.now());
        entity.setLastMatchCount(initialRun.jobs().size());

        SavedSearch saved = savedSearchRepository.save(entity);
        return toDto(saved);
    }

    public List<SavedSearchDto> listSavedSearches(UUID userId) {
        return savedSearchRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedSearchDto refreshSavedSearch(UUID userId, UUID savedSearchId) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(savedSearchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        JobSearchFilters filters = readFiltersJson(entity.getFiltersJson());
        JobSearchOrchestrator.JobSearchResponse response = searchOrchestrator.search(userId, filters);
        entity.setLastRefreshedAt(Instant.now());
        entity.setLastMatchCount(response.jobs().size());
        return toDto(savedSearchRepository.save(entity));
    }

    @Transactional
    public SavedSearchDto toggleDailyRefresh(UUID userId, UUID savedSearchId, boolean enabled) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(savedSearchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        entity.setDailyRefreshEnabled(enabled);
        return toDto(savedSearchRepository.save(entity));
    }

    @Transactional
    public void deleteSavedSearch(UUID userId, UUID savedSearchId) {
        SavedSearch entity = savedSearchRepository.findByIdAndUserId(savedSearchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        savedSearchRepository.delete(entity);
    }

    /**
     * Scheduled daily refresh for all saved searches with dailyRefreshEnabled = true.
     */
    @Scheduled(cron = "${app.search.daily-refresh-cron:0 0 6 * * *}")
    @Transactional
    public int runScheduledDailyRefresh() {
        List<SavedSearch> activeSearches = savedSearchRepository.findByDailyRefreshEnabledTrue();
        int refreshed = 0;
        for (SavedSearch s : activeSearches) {
            try {
                JobSearchFilters filters = readFiltersJson(s.getFiltersJson());
                JobSearchOrchestrator.JobSearchResponse res = searchOrchestrator.search(s.getUserId(), filters);
                s.setLastRefreshedAt(Instant.now());
                s.setLastMatchCount(res.jobs().size());
                savedSearchRepository.save(s);
                refreshed++;
            } catch (Exception e) {
                log.warn("Daily refresh failed for savedSearchId={}: {}", s.getId(), e.getMessage());
            }
        }
        return refreshed;
    }

    private SavedSearchDto toDto(SavedSearch s) {
        return new SavedSearchDto(
                s.getId(),
                s.getName(),
                s.getRawQuery(),
                readFiltersJson(s.getFiltersJson()),
                s.isDailyRefreshEnabled(),
                s.getLastRefreshedAt(),
                s.getLastMatchCount(),
                s.getCreatedAt()
        );
    }

    private String writeFiltersJson(JobSearchFilters filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JobSearchFilters readFiltersJson(String json) {
        if (json == null || json.isBlank()) return new JobSearchFilters();
        try {
            return objectMapper.readValue(json, JobSearchFilters.class);
        } catch (Exception e) {
            return new JobSearchFilters();
        }
    }
}
