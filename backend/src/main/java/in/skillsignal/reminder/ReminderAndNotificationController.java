package in.skillsignal.reminder;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReminderAndNotificationController {

    public record CreateReminderRequest(
            UUID applicationId,
            @NotBlank String reminderType,
            @NotBlank String title,
            @NotNull Instant dueAt,
            String notes
    ) {}

    public record SnoozeReminderRequest(
            @NotNull Instant snoozedUntil
    ) {}

    public record RescheduleReminderRequest(
            @NotNull Instant dueAt
    ) {}

    public record UpdatePreferencesRequest(
            String timeZone,
            String quietHoursStart,
            String quietHoursEnd,
            Boolean dailySummaryEnabled
    ) {}

    public record DispatchRequest(
            Instant evaluationTime
    ) {}

    public record FollowUpDraftRequest(
            @NotNull UUID applicationId
    ) {}

    private final ReminderAndNotificationService service;

    public ReminderAndNotificationController(ReminderAndNotificationService service) {
        this.service = service;
    }

    @GetMapping("/reminders")
    public List<Reminder> listReminders(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listReminders(principal.id());
    }

    @GetMapping("/reminders/overdue")
    public List<Reminder> listOverdueReminders(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Instant asOf
    ) {
        requireAuth(principal);
        return service.listOverdueReminders(principal.id(), asOf);
    }

    @PostMapping("/reminders")
    public ResponseEntity<Reminder> createReminder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateReminderRequest req
    ) {
        requireAuth(principal);
        Reminder created = service.createReminder(
                principal.id(),
                req.applicationId(),
                req.reminderType(),
                req.title(),
                req.dueAt(),
                req.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/reminders/{id}/snooze")
    public Reminder snoozeReminder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody SnoozeReminderRequest req
    ) {
        requireAuth(principal);
        return service.snoozeReminder(principal.id(), id, req.snoozedUntil());
    }

    @PutMapping("/reminders/{id}/reschedule")
    public Reminder rescheduleReminder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleReminderRequest req
    ) {
        requireAuth(principal);
        return service.rescheduleReminder(principal.id(), id, req.dueAt());
    }

    @PutMapping("/reminders/{id}/complete")
    public Reminder completeReminder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.completeReminder(principal.id(), id);
    }

    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<Map<String, String>> deleteReminder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        service.deleteReminder(principal.id(), id);
        return ResponseEntity.ok(Map.of("message", "Reminder deleted"));
    }

    @PostMapping("/reminders/follow-up-draft")
    public ReminderAndNotificationService.FollowUpDraftDto generateFollowUpDraft(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody FollowUpDraftRequest req
    ) {
        requireAuth(principal);
        return service.generateFollowUpEmailDraft(principal.id(), req.applicationId());
    }

    @GetMapping("/notifications")
    public List<Notification> listNotifications(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listNotifications(principal.id());
    }

    @PutMapping("/notifications/{id}/read")
    public Notification markRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        return service.markNotificationRead(principal.id(), id);
    }

    @PostMapping("/notifications/dispatch")
    public ReminderAndNotificationService.DispatchResultDto dispatchNotifications(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) DispatchRequest req
    ) {
        requireAuth(principal);
        Instant evalTime = req != null ? req.evaluationTime() : null;
        return service.dispatchDueNotificationsAndSummary(principal.id(), evalTime);
    }

    @GetMapping("/notification-preferences")
    public UserNotificationPreference getPreferences(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.getOrCreatePreferences(principal.id());
    }

    @PutMapping("/notification-preferences")
    public UserNotificationPreference updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UpdatePreferencesRequest req
    ) {
        requireAuth(principal);
        return service.updatePreferences(
                principal.id(),
                req.timeZone(),
                req.quietHoursStart(),
                req.quietHoursEnd(),
                req.dailySummaryEnabled()
        );
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

