package in.skillsignal.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleMailSenderService implements MailSenderService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailSenderService.class);

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetLinkUrl) {
        // Never log raw secrets or tokens in production logs; only log dispatch status
        log.info("[DEV-MAIL] Dispatched password reset link to recipient {}", recipientEmail);
    }

    @Override
    public void sendDailySummaryEmail(String recipientEmail, String subject, String summaryBody) {
        log.info("[DEV-MAIL] Dispatched daily summary email '{}' to recipient {}", subject, recipientEmail);
    }
}

