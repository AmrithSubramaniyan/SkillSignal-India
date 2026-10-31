package in.skillsignal.emailapply;

import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationEvent;
import in.skillsignal.application.ApplicationEventRepository;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.audit.AuditService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.session.SessionCheckoutEmailService;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class EmailApplyService {

    public static final String GMAIL_MIN_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    public static final String GMAIL_ACK_READ_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    public static final String OUTLOOK_MIN_SEND_SCOPE = "Mail.Send";

    public record ConnectedMailboxDto(
            UUID id,
            String provider,
            String emailAddress,
            String scopes,
            boolean ackDetectionEnabled,
            String status,
            Instant connectedAt,
            Instant revokedAt
    ) {}

    public record EmailApplyMessageDto(
            UUID id,
            UUID jobId,
            String jobTitle,
            String company,
            UUID applicationId,
            UUID mailboxId,
            String recipientEmail,
            String subject,
            String bodyText,
            UUID resumeVersionId,
            String attachedResumeFilename,
            boolean approvedByUser,
            String providerMessageId,
            String status,
            String confirmationNote,
            Instant ackDetectedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private final ConnectedMailboxRepository mailboxRepository;
    private final EmailApplyMessageRepository messageRepository;
    private final JobRepository jobRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SessionCheckoutEmailService sessionCheckoutEmailService;
    private final byte[] aesKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailApplyService(
            ConnectedMailboxRepository mailboxRepository,
            EmailApplyMessageRepository messageRepository,
            JobRepository jobRepository,
            ResumeVersionRepository resumeVersionRepository,
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            UserRepository userRepository,
            AuditService auditService,
            SessionCheckoutEmailService sessionCheckoutEmailService,
            @Value("${app.mailbox.encryption-key:skillsignal-local-dev-mailbox-key-32b}") String rawEncryptionKey
    ) {
        this.mailboxRepository = mailboxRepository;
        this.messageRepository = messageRepository;
        this.jobRepository = jobRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.sessionCheckoutEmailService = sessionCheckoutEmailService;
        this.aesKeyBytes = deriveAes256Key(rawEncryptionKey);
    }

    @Transactional
    public ConnectedMailboxDto connectMailboxOAuth(
            UUID userId,
            String provider,
            String emailAddress,
            String oauthAccessToken,
            Boolean ackDetectionEnabled,
            String forbiddenPasswordField
    ) {
        if (forbiddenPasswordField != null && !forbiddenPasswordField.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Golden Rule: SkillSignal never accepts or stores email passwords. Use OAuth token exchange only."
            );
        }
        if (emailAddress == null || emailAddress.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "emailAddress is required");
        }
        if (oauthAccessToken == null || oauthAccessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oauthAccessToken is required");
        }

        String normProvider = provider != null ? provider.trim().toUpperCase(Locale.ROOT) : "GMAIL";
        if (!"GMAIL".equals(normProvider) && !"OUTLOOK".equals(normProvider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported OAuth providers: GMAIL, OUTLOOK");
        }

        boolean ackEnabled = Boolean.TRUE.equals(ackDetectionEnabled);
        String scopes;
        if ("GMAIL".equals(normProvider)) {
            scopes = ackEnabled ? (GMAIL_MIN_SEND_SCOPE + " " + GMAIL_ACK_READ_SCOPE) : GMAIL_MIN_SEND_SCOPE;
        } else {
            scopes = ackEnabled ? (OUTLOOK_MIN_SEND_SCOPE + " Mail.Read") : OUTLOOK_MIN_SEND_SCOPE;
        }

        String encryptedRef = encryptAesGcm(oauthAccessToken.trim());

        ConnectedMailbox mb = new ConnectedMailbox();
        mb.setUserId(userId);
        mb.setProvider(normProvider);
        mb.setEmailAddress(emailAddress.trim());
        mb.setScopes(scopes);
        mb.setEncryptedTokenRef(encryptedRef);
        mb.setAckDetectionEnabled(ackEnabled);
        mb.setStatus("CONNECTED");
        mb = mailboxRepository.save(mb);

        // Golden Rule: Never log the OAuth token in audit logs
        auditService.log(userId, "MAILBOX_OAUTH_CONNECTED", "ConnectedMailbox", mb.getId().toString(),
                Map.of("provider", normProvider, "emailAddress", mb.getEmailAddress(), "scopes", scopes));

        return toMailboxDto(mb);
    }

    public List<ConnectedMailboxDto> listMailboxes(UUID userId) {
        return mailboxRepository.findByUserIdOrderByConnectedAtDesc(userId).stream()
                .map(this::toMailboxDto)
                .toList();
    }

    @Transactional
    public ConnectedMailboxDto disconnectMailbox(UUID userId, UUID mailboxId) {
        ConnectedMailbox mb = mailboxRepository.findByIdAndUserId(mailboxId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mailbox not found"));
        mb.setStatus("REVOKED");
        mb.setRevokedAt(Instant.now());
        mb.setEncryptedTokenRef("REVOKED");
        mb = mailboxRepository.save(mb);

        auditService.log(userId, "MAILBOX_OAUTH_REVOKED", "ConnectedMailbox", mb.getId().toString(),
                Map.of("provider", mb.getProvider(), "status", "REVOKED"));

        return toMailboxDto(mb);
    }

    @Transactional
    public EmailApplyMessageDto createDraft(
            UUID userId,
            UUID jobId,
            String recipientEmail,
            String customSubject,
            String customBody,
            UUID resumeVersionIdOverride
    ) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (recipientEmail == null || recipientEmail.isBlank() || !recipientEmail.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid recruiter recipientEmail is required");
        }

        ResumeVersion rv = null;
        if (resumeVersionIdOverride != null) {
            rv = resumeVersionRepository.findByIdAndUserId(resumeVersionIdOverride, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found"));
        } else {
            rv = resumeVersionRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                    .orElseGet(() -> resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                            .findFirst()
                            .orElse(null));
        }

        User user = userRepository.findById(userId).orElse(null);
        String candidateName = user != null && user.getFullName() != null ? user.getFullName() : "Candidate";

        String subject = (customSubject != null && !customSubject.isBlank())
                ? customSubject.trim()
                : ("Application for " + job.getTitle() + " at " + job.getCompany() + " — " + candidateName);

        String body = (customBody != null && !customBody.isBlank())
                ? customBody.trim()
                : ("Dear Hiring Team at " + job.getCompany() + ",\n\n"
                + "Please find attached my resume for the " + job.getTitle() + " role.\n\n"
                + "Best regards,\n" + candidateName);

        EmailApplyMessage msg = new EmailApplyMessage();
        msg.setUserId(userId);
        msg.setJobId(job.getId());
        msg.setRecipientEmail(recipientEmail.trim());
        msg.setSubject(subject);
        msg.setBodyText(body);
        msg.setResumeVersionId(rv != null ? rv.getId() : null);
        msg.setApprovedByUser(false);
        msg.setStatus("DRAFT");
        msg = messageRepository.save(msg);

        return toMessageDto(msg, job, rv);
    }

    @Transactional
    public EmailApplyMessageDto approveDraft(UUID userId, UUID messageId) {
        EmailApplyMessage msg = messageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email draft not found"));
        Job job = jobRepository.findByIdAndUserId(msg.getJobId(), userId).orElse(null);
        ResumeVersion rv = msg.getResumeVersionId() != null
                ? resumeVersionRepository.findByIdAndUserId(msg.getResumeVersionId(), userId).orElse(null)
                : null;

        msg.setApprovedByUser(true);
        msg = messageRepository.save(msg);

        auditService.log(userId, "EMAIL_APPLY_DRAFT_APPROVED", "EmailApplyMessage", msg.getId().toString(),
                Map.of("jobId", msg.getJobId().toString()));

        return toMessageDto(msg, job, rv);
    }

    /**
     * Sends an email application ONLY after user approval and ONLY through a CONNECTED OAuth mailbox.
     * Golden Rule: Status is set to "SENT" (NOT confirmed!) and tracker Application is set to
     * NEEDS_VERIFICATION (UNCONFIRMED) until an acknowledgement is received or user resolves.
     */
    @Transactional
    public EmailApplyMessageDto sendApprovedEmail(UUID userId, UUID messageId) {
        EmailApplyMessage msg = messageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email draft not found"));

        if (!msg.isApprovedByUser()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Email application cannot be sent before explicit user approval"
            );
        }

        ConnectedMailbox mailbox = mailboxRepository
                .findFirstByUserIdAndStatusOrderByConnectedAtDesc(userId, "CONNECTED")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No active connected OAuth mailbox found (mailbox may be disconnected or revoked)"
                ));

        // Verify encrypted token can be decrypted internally without ever logging it
        String decryptedToken = decryptAesGcm(mailbox.getEncryptedTokenRef());
        if (decryptedToken == null || decryptedToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid or revoked OAuth token reference");
        }

        Job job = jobRepository.findByIdAndUserId(msg.getJobId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        ResumeVersion rv = msg.getResumeVersionId() != null
                ? resumeVersionRepository.findByIdAndUserId(msg.getResumeVersionId(), userId).orElse(null)
                : null;

        String providerMessageId = mailbox.getProvider().toLowerCase(Locale.ROOT) + "-msg-" + UUID.randomUUID();
        msg.setMailboxId(mailbox.getId());
        msg.setProviderMessageId(providerMessageId);
        msg.setStatus("SENT"); // Sent (not confirmed until acknowledgement!)

        // Create or update tracker Application in NEEDS_VERIFICATION (UNCONFIRMED) because "Sent" is not confirmed
        Application app = applicationRepository.findByUserIdAndJobId(userId, job.getId())
                .orElseGet(() -> {
                    Application a = new Application();
                    a.setUserId(userId);
                    a.setJobId(job.getId());
                    a.setSource("email_apply");
                    return a;
                });

        String prevStatus = app.getStatus();
        app.setResumeVersionId(msg.getResumeVersionId());
        app.setStatus("NEEDS_VERIFICATION");
        app.setSubState("NEEDS_VERIFICATION");
        app.setApplyMode("EMAIL_APPLY");
        app.setSubmissionMethod("EMAIL_APPLY");
        app.setConfirmationType("UNCONFIRMED");
        app.setRecruiterContacts(msg.getRecipientEmail());
        app.setNotes("Email sent via " + mailbox.getProvider() + " (messageId=" + providerMessageId
                + "). Status is Sent (not confirmed) until recruiter acknowledgement.");
        app = applicationRepository.save(app);

        ApplicationEvent ev = new ApplicationEvent();
        ev.setApplicationId(app.getId());
        ev.setUserId(userId);
        ev.setFromStatus(prevStatus);
        ev.setToStatus("NEEDS_VERIFICATION");
        ev.setNote("Email sent (messageId=" + providerMessageId + "); awaiting acknowledgement");
        applicationEventRepository.save(ev);

        msg.setApplicationId(app.getId());
        msg = messageRepository.save(msg);

        // Sprint 7 Rule 6: SENT (email-apply, unconfirmed) qualifies with truthful UNCONFIRMED label
        sessionCheckoutEmailService.recordQualifyingApplicationApplied(
                userId,
                app.getId(),
                job.getCompany(),
                job.getTitle(),
                Instant.now(),
                "Sent by email",
                "UNCONFIRMED",
                null,
                false
        );

        auditService.log(userId, "EMAIL_APPLY_SENT", "EmailApplyMessage", msg.getId().toString(),
                Map.of("providerMessageId", providerMessageId, "status", "SENT"));

        return toMessageDto(msg, job, rv);
    }

    /**
     * Optional acknowledgement detection in the user's own connected mailbox (requires ackDetectionEnabled=true).
     * Upgrades status from "SENT" to "EXTERNALLY_CONFIRMED" and updates tracker Application accordingly.
     */
    @Transactional
    public EmailApplyMessageDto detectAcknowledgement(UUID userId, UUID messageId, String ackHeaderOrSubject) {
        EmailApplyMessage msg = messageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email message not found"));

        if (!"SENT".equals(msg.getStatus()) && !"EXTERNALLY_CONFIRMED".equals(msg.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email message must be SENT before detecting acknowledgement");
        }

        ConnectedMailbox mailbox = msg.getmailboxId() != null
                ? mailboxRepository.findByIdAndUserId(msg.getmailboxId(), userId).orElse(null)
                : null;
        if (mailbox == null || !"CONNECTED".equals(mailbox.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Connected mailbox is no longer active");
        }
        if (!mailbox.isAckDetectionEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Acknowledgement detection is disabled for this mailbox; user permission is required"
            );
        }

        msg.setStatus("EXTERNALLY_CONFIRMED");
        msg.setAckDetectedAt(Instant.now());
        msg = messageRepository.save(msg);

        Job job = jobRepository.findByIdAndUserId(msg.getJobId(), userId).orElse(null);
        ResumeVersion rv = msg.getResumeVersionId() != null
                ? resumeVersionRepository.findByIdAndUserId(msg.getResumeVersionId(), userId).orElse(null)
                : null;

        if (msg.getApplicationId() != null) {
            applicationRepository.findByIdAndUserId(msg.getApplicationId(), userId).ifPresent(app -> {
                String oldSt = app.getStatus();
                app.setStatus("APPLIED");
                app.setSubState(null);
                app.setConfirmationType("EXTERNALLY_CONFIRMED");
                if (app.getAppliedAt() == null) app.setAppliedAt(Instant.now());
                if (app.getFollowUpDate() == null) app.setFollowUpDate(LocalDate.now().plusDays(7));
                app.setNotes("Upgraded to EXTERNALLY_CONFIRMED via mailbox acknowledgement: "
                        + (ackHeaderOrSubject != null ? ackHeaderOrSubject : "Auto-reply receipt"));
                applicationRepository.save(app);

                sessionCheckoutEmailService.recordQualifyingApplicationApplied(
                        userId,
                        app.getId(),
                        job != null ? job.getCompany() : "Unknown Company",
                        job != null ? job.getTitle() : "Open Role",
                        app.getAppliedAt(),
                        "Sent by email",
                        "EXTERNALLY_CONFIRMED",
                        null,
                        false
                );

                ApplicationEvent ev = new ApplicationEvent();
                ev.setApplicationId(app.getId());
                ev.setUserId(userId);
                ev.setFromStatus(oldSt);
                ev.setToStatus("APPLIED");
                ev.setNote("Upgraded to EXTERNALLY_CONFIRMED after acknowledgement detected in mailbox");
                applicationEventRepository.save(ev);
            });
        }

        auditService.log(userId, "EMAIL_APPLY_ACK_CONFIRMED", "EmailApplyMessage", msg.getId().toString(),
                Map.of("confirmationType", "EXTERNALLY_CONFIRMED"));

        return toMessageDto(msg, job, rv);
    }

    public List<EmailApplyMessageDto> listMessages(UUID userId) {
        Map<UUID, Job> jobs = new HashMap<>();
        jobRepository.findByUserIdOrderByCreatedAtDesc(userId).forEach(j -> jobs.put(j.getId(), j));
        Map<UUID, ResumeVersion> rvs = new HashMap<>();
        resumeVersionRepository.findByUserIdOrderByCreatedAtDesc(userId).forEach(r -> rvs.put(r.getId(), r));

        return messageRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(m -> toMessageDto(m, jobs.get(m.getJobId()), m.getResumeVersionId() != null ? rvs.get(m.getResumeVersionId()) : null))
                .toList();
    }

    private ConnectedMailboxDto toMailboxDto(ConnectedMailbox mb) {
        return new ConnectedMailboxDto(
                mb.getId(),
                mb.getProvider(),
                mb.getEmailAddress(),
                mb.getScopes(),
                mb.isAckDetectionEnabled(),
                mb.getStatus(),
                mb.getConnectedAt(),
                mb.getRevokedAt()
        );
    }

    private EmailApplyMessageDto toMessageDto(EmailApplyMessage m, Job job, ResumeVersion rv) {
        String note = switch (m.getStatus()) {
            case "SENT" -> "Sent (not confirmed) — awaiting recruiter acknowledgement before upgrading to EXTERNALLY_CONFIRMED.";
            case "EXTERNALLY_CONFIRMED" -> "Confirmed via recruiter acknowledgement in your mailbox (EXTERNALLY_CONFIRMED).";
            default -> "Draft — requires explicit user approval before sending.";
        };
        return new EmailApplyMessageDto(
                m.getId(),
                m.getJobId(),
                job != null ? job.getTitle() : "Unknown Job",
                job != null ? job.getCompany() : "",
                m.getApplicationId(),
                m.getmailboxId(),
                m.getRecipientEmail(),
                m.getSubject(),
                m.getBodyText(),
                m.getResumeVersionId(),
                rv != null ? rv.getOriginalFilename() : null,
                m.isApprovedByUser(),
                m.getProviderMessageId(),
                m.getStatus(),
                note,
                m.getAckDetectedAt(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }

    private byte[] deriveAes256Key(String secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest((secret != null ? secret : "default-key").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize AES-256 key", e);
        }
    }

    private String encryptAesGcm(String plaintext) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    private String decryptAesGcm(String encoded) {
        if (encoded == null || "REVOKED".equals(encoded)) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= 12) return null;
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] cipherBytes = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

