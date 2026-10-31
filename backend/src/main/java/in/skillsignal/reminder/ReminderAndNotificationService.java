package in.skillsignal.reminder;

import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.security.MailSenderService;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ReminderAndNotificationService {

    public static final Set<String> VALID_REMINDER_TYPES = Set.of(
            "FOLLOW_UP",
            "ASSESSMENT",
            "INTERVIEW",
            "OFFER"
    );

    public record FollowUpDraftDto(
            UUID applicationId,
            String jobTitle,
            String company,
            String recipientHint,
            String subject,
            String body,
            String policyNote
    ) {}

    public record DispatchResultDto(
            int dueRemindersCount,
            int newInAppNotificationsCreated,
            boolean dailySummaryEmailSent,
            boolean suppressedByQuietHours,
            String userTimeZone,
            String evaluatedLocalTime
    ) {}

    private final UserNotificationPreferenceRepository preferenceRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSentLogRepository sentLogRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final MailSenderService mailSenderService;

    public ReminderAndNotificationService(
            UserNotificationPreferenceRepository preferenceRepository,
            ReminderRepository reminderRepository,
            NotificationRepository notificationRepository,
            NotificationSentLogRepository sentLogRepository,
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            UserRepository userRepository,
            MailSenderService mailSenderService
    ) {
        this.preferenceRepository = preferenceRepository;
        this.reminderRepository = reminderRepository;
        this.notificationRepository = notificationRepository;
        this.sentLogRepository = sentLogRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.mailSenderService = mailSenderService;
    }

    @Transactional
    public UserNotificationPreference getOrCreatePreferences(UUID userId) {
        return preferenceRepository.findById(userId).orElseGet(() -> {
            UserNotificationPreference pref = new UserNotificationPreference();
            pref.setUserId(userId);
            pref.setTimeZone("Asia/Kolkata");
            pref.setQuietHoursStart("22:00");
            pref.setQuietHoursEnd("08:00");
            pref.setDailySummaryEnabled(true);
            pref.setUpdatedAt(Instant.now());
            return preferenceRepository.save(pref);
        });
    }

    @Transactional
    public UserNotificationPreference updatePreferences(
            UUID userId,
            String timeZone,
            String quietHoursStart,
            String quietHoursEnd,
            Boolean dailySummaryEnabled
    ) {
        UserNotificationPreference pref = getOrCreatePreferences(userId);
        if (timeZone != null && !timeZone.isBlank()) {
            try {
                ZoneId.of(timeZone.trim());
                pref.setTimeZone(timeZone.trim());
            } catch (DateTimeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time zone: " + timeZone);
            }
        }
        if (quietHoursStart != null && !quietHoursStart.isBlank()) {
            parseTime(quietHoursStart.trim());
            pref.setQuietHoursStart(quietHoursStart.trim());
        }
        if (quietHoursEnd != null && !quietHoursEnd.isBlank()) {
            parseTime(quietHoursEnd.trim());
            pref.setQuietHoursEnd(quietHoursEnd.trim());
        }
        if (dailySummaryEnabled != null) {
            pref.setDailySummaryEnabled(dailySummaryEnabled);
        }
        pref.setUpdatedAt(Instant.now());
        return preferenceRepository.save(pref);
    }

    public boolean isWithinQuietHours(UserNotificationPreference pref, Instant instant) {
        ZoneId zone = ZoneId.of(pref.getTimeZone());
        LocalTime localTime = ZonedDateTime.ofInstant(instant, zone).toLocalTime();
        LocalTime start = parseTime(pref.getQuietHoursStart());
        LocalTime end = parseTime(pref.getQuietHoursEnd());

        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            // Same-day window, e.g., 13:00 to 15:00
            return !localTime.isBefore(start) && localTime.isBefore(end);
        } else {
            // Overnight window, e.g., 22:00 to 08:00
            return !localTime.isBefore(start) || localTime.isBefore(end);
        }
    }

    @Transactional
    public Reminder createReminder(
            UUID userId,
            UUID applicationId,
            String reminderType,
            String title,
            Instant dueAt,
            String notes
    ) {
        String normType = reminderType != null ? reminderType.trim().toUpperCase(Locale.ROOT) : "";
        if (!VALID_REMINDER_TYPES.contains(normType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid reminderType. Allowed: FOLLOW_UP, ASSESSMENT, INTERVIEW, OFFER"
            );
        }
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder title is required");
        }
        if (dueAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder dueAt is required");
        }
        if (applicationId != null) {
            applicationRepository.findByIdAndUserId(applicationId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        }

        Reminder r = new Reminder();
        r.setUserId(userId);
        r.setApplicationId(applicationId);
        r.setReminderType(normType);
        r.setTitle(title.trim());
        r.setDueAt(dueAt);
        r.setStatus("PENDING");
        r.setNotes(notes);
        return reminderRepository.save(r);
    }

    public List<Reminder> listReminders(UUID userId) {
        return reminderRepository.findByUserIdOrderByDueAtAsc(userId);
    }

    /**
     * Returns reminders that are due or overdue at {@code asOf}.
     * Boundary rule: a reminder is due/overdue when status != COMPLETED,
     * dueAt <= asOf, and (snoozedUntil == null || snoozedUntil <= asOf).
     * Golden Rule: Overdue reminders or recruiter silence NEVER change an application's status to REJECTED.
     */
    public List<Reminder> listOverdueReminders(UUID userId, Instant asOf) {
        Instant check = asOf != null ? asOf : Instant.now();
        return reminderRepository.findByUserIdOrderByDueAtAsc(userId).stream()
                .filter(r -> !"COMPLETED".equalsIgnoreCase(r.getStatus()))
                .filter(r -> !r.getDueAt().isAfter(check))
                .filter(r -> r.getSnoozedUntil() == null || !r.getSnoozedUntil().isAfter(check))
                .toList();
    }

    @Transactional
    public Reminder snoozeReminder(UUID userId, UUID reminderId, Instant snoozedUntil) {
        if (snoozedUntil == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "snoozedUntil is required");
        }
        Reminder r = reminderRepository.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        r.setStatus("SNOOZED");
        r.setSnoozedUntil(snoozedUntil);
        r.setDueAt(snoozedUntil);
        return reminderRepository.save(r);
    }

    @Transactional
    public Reminder rescheduleReminder(UUID userId, UUID reminderId, Instant newDueAt) {
        if (newDueAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueAt is required");
        }
        Reminder r = reminderRepository.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        r.setStatus("PENDING");
        r.setSnoozedUntil(null);
        r.setDueAt(newDueAt);
        return reminderRepository.save(r);
    }

    @Transactional
    public Reminder completeReminder(UUID userId, UUID reminderId) {
        Reminder r = reminderRepository.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        r.setStatus("COMPLETED");
        r.setCompletedAt(Instant.now());
        return reminderRepository.save(r);
    }

    @Transactional
    public void deleteReminder(UUID userId, UUID reminderId) {
        Reminder r = reminderRepository.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        reminderRepository.delete(r);
    }

    /**
     * Evaluates due/overdue reminders at {@code evaluationTime}, creates deduplicated in-app notifications,
     * and dispatches the daily summary email via {@link MailSenderService} unless suppressed by quiet hours
     * or already sent today according to {@code notification_sent_logs}.
     * Never modifies application status (silence is never treated as rejection).
     */
    @Transactional
    public DispatchResultDto dispatchDueNotificationsAndSummary(UUID userId, Instant evaluationTime) {
        Instant now = evaluationTime != null ? evaluationTime : Instant.now();
        UserNotificationPreference pref = getOrCreatePreferences(userId);
        ZoneId zone = ZoneId.of(pref.getTimeZone());
        ZonedDateTime userZoned = ZonedDateTime.ofInstant(now, zone);
        boolean quietHours = isWithinQuietHours(pref, now);

        List<Reminder> dueList = listOverdueReminders(userId, now);
        int createdInApp = 0;

        for (Reminder r : dueList) {
            String dedupKey = "REMINDER:" + r.getId() + ":DUE:" + r.getDueAt().toEpochMilli();
            if (!sentLogRepository.existsByUserIdAndDedupKeyAndChannel(userId, dedupKey, "IN_APP")) {
                NotificationSentLog logEntry = new NotificationSentLog();
                logEntry.setUserId(userId);
                logEntry.setDedupKey(dedupKey);
                logEntry.setChannel("IN_APP");
                sentLogRepository.save(logEntry);

                Notification notif = new Notification();
                notif.setUserId(userId);
                notif.setReminderId(r.getId());
                notif.setChannel("IN_APP");
                notif.setTitle("[" + r.getReminderType() + "] " + r.getTitle());
                notif.setMessage("Reminder due: " + r.getTitle()
                        + (r.getNotes() != null && !r.getNotes().isBlank() ? " — " + r.getNotes() : ""));
                notif.setSuppressedByQuietHours(quietHours);
                notificationRepository.save(notif);
                createdInApp++;
            }
        }

        boolean summaryEmailSent = false;
        if (pref.isDailySummaryEnabled() && !dueList.isEmpty() && !quietHours) {
            String summaryDedupKey = "DAILY_SUMMARY:" + userZoned.toLocalDate();
            if (!sentLogRepository.existsByUserIdAndDedupKeyAndChannel(userId, summaryDedupKey, "EMAIL")) {
                User user = userRepository.findById(userId).orElse(null);
                String recipient = user != null ? user.getEmail() : "user@skillsignal.in";
                String subject = "SkillSignal Daily Action Summary (" + dueList.size() + " due)";
                StringBuilder body = new StringBuilder("You have ")
                        .append(dueList.size())
                        .append(" reminder(s) due in your time zone (")
                        .append(pref.getTimeZone())
                        .append("):\n");
                for (Reminder r : dueList) {
                    body.append("- [").append(r.getReminderType()).append("] ").append(r.getTitle()).append("\n");
                }

                mailSenderService.sendDailySummaryEmail(recipient, subject, body.toString());

                NotificationSentLog emailLog = new NotificationSentLog();
                emailLog.setUserId(userId);
                emailLog.setDedupKey(summaryDedupKey);
                emailLog.setChannel("EMAIL");
                sentLogRepository.save(emailLog);

                Notification emailNotif = new Notification();
                emailNotif.setUserId(userId);
                emailNotif.setChannel("EMAIL");
                emailNotif.setTitle(subject);
                emailNotif.setMessage(body.toString().trim());
                emailNotif.setSuppressedByQuietHours(false);
                notificationRepository.save(emailNotif);

                summaryEmailSent = true;
            }
        }

        return new DispatchResultDto(
                dueList.size(),
                createdInApp,
                summaryEmailSent,
                quietHours,
                pref.getTimeZone(),
                userZoned.toLocalTime().toString()
        );
    }

    public List<Notification> listNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Notification markNotificationRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
        return notificationRepository.save(n);
    }

    /**
     * Generates an AI/template follow-up email draft for the user to copy and send manually.
     * Never sends the email automatically, and never treats recruiter silence as rejection.
     */
    public FollowUpDraftDto generateFollowUpEmailDraft(UUID userId, UUID applicationId) {
        Application app = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Job job = jobRepository.findByIdAndUserId(app.getJobId(), userId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);

        String jobTitle = job != null ? job.getTitle() : "the open role";
        String company = job != null ? job.getCompany() : "your organization";
        String candidateName = user != null && user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : "Candidate";
        String recipientHint = app.getRecruiterContacts() != null && !app.getRecruiterContacts().isBlank()
                ? app.getRecruiterContacts()
                : "Hiring Team at " + company;

        String subject = "Following up on my application for " + jobTitle + " — " + candidateName;
        String body = "Dear " + recipientHint + ",\n\n"
                + "I hope you are having a productive week. I am writing to politely follow up on my application for the "
                + jobTitle + " position at " + company + " (current status: " + app.getStatus() + ").\n\n"
                + "I remain very interested in contributing to " + company
                + " and would be glad to share any additional details or schedule a conversation at your convenience.\n\n"
                + "Best regards,\n"
                + candidateName;

        return new FollowUpDraftDto(
                app.getId(),
                jobTitle,
                company,
                recipientHint,
                subject,
                body,
                "Draft only — review and send manually from your own email client. SkillSignal never sends follow-up emails automatically and never treats recruiter silence as rejection."
        );
    }

    private LocalTime parseTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time format (expected HH:mm): " + hhmm);
        }
    }
}
