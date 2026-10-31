package in.skillsignal.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ConsoleMailSenderService implements MailSenderService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailSenderService.class);

    public record DispatchedEmailRecord(
            String messageId,
            String recipientEmail,
            String subject,
            String bodyHtml,
            String bodyText,
            Instant sentAt) {
    }

    private final List<DispatchedEmailRecord> sentSessionEmails = new CopyOnWriteArrayList<>();

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetLinkUrl) {
        // Never log raw secrets or tokens in production logs; only log dispatch status
        log.info("[DEV-MAIL] Dispatched password reset link to recipient {}", recipientEmail);
    }

    @Override
    public void sendDailySummaryEmail(String recipientEmail, String subject, String summaryBody) {
        log.info("[DEV-MAIL] Dispatched daily summary email '{}' to recipient {}", subject, recipientEmail);
    }

    @Override
    public String sendSessionSummaryEmail(String recipientEmail, String subject, String bodyHtml, String bodyText) {
        String messageId = "console-ses-" + UUID.randomUUID();
        sentSessionEmails.add(new DispatchedEmailRecord(
                messageId,
                recipientEmail,
                subject,
                bodyHtml,
                bodyText,
                Instant.now()));
        log.info("[DEV-MAIL] Dispatched session check-out summary email messageId={} subject='{}' to recipient={}",
                messageId, subject, recipientEmail);
        return messageId;
    }

    public List<DispatchedEmailRecord> getSentSessionEmails() {
        return List.copyOf(sentSessionEmails);
    }

    public void clearSentSessionEmails() {
        sentSessionEmails.clear();
    }
}
