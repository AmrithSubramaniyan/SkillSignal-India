package in.skillsignal.security;

public interface MailSenderService {
    void sendPasswordResetEmail(String recipientEmail, String resetLinkUrl);
}

