package in.skillsignal.security;

public interface MailSenderService {
    void sendPasswordResetEmail(String recipientEmail, String resetLinkUrl);

    default void sendDailySummaryEmail(String recipientEmail, String subject, String summaryBody) {}

    default void sendApprovalLinkEmail(String recipientEmail, String jobTitle, String company, String approvalLinkUrl) {}
}


