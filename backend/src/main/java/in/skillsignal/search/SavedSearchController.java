package in.skillsignal.search;

import in.skillsignal.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    public SavedSearchController(SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    public record ToggleRefreshRequest(boolean dailyRefreshEnabled) {}

    @GetMapping
    public List<SavedSearchService.SavedSearchDto> listSavedSearches(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return savedSearchService.listSavedSearches(user.id());
    }

    @PostMapping
    public ResponseEntity<SavedSearchService.SavedSearchDto> createSavedSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody SavedSearchService.CreateSavedSearchRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedSearchService.createSavedSearch(user.id(), request));
    }

    @PostMapping("/{id}/refresh")
    public SavedSearchService.SavedSearchDto refreshSavedSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return savedSearchService.refreshSavedSearch(user.id(), id);
    }

    @PutMapping("/{id}/daily-refresh")
    public SavedSearchService.SavedSearchDto toggleDailyRefresh(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRefreshRequest request
    ) {
        return savedSearchService.toggleDailyRefresh(user.id(), id, request.dailyRefreshEnabled());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteSavedSearch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        savedSearchService.deleteSavedSearch(user.id(), id);
        return Map.of("message", "Saved search deleted");
    }
}
