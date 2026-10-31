package in.skillsignal.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.security.ConsoleMailSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 3,
        topics = {
                KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED,
                KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT,
                KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED,
                KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED_DLT,
                KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT_DLT,
                KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED_DLT
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "app.session.checkout-grace-ms=50"
})
class StageS7SessionCheckoutEmailKafkaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionCheckoutEmailService sessionService;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private SessionAppliedItemRepository appliedItemRepository;

    @Autowired
    private SessionSummaryEmailRepository summaryEmailRepository;

    @Autowired
    private SessionKafkaConsumer sessionKafkaConsumer;

    @Autowired
    private KafkaTopicsConfig kafkaTopicsConfig;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsoleMailSenderService mailSenderService;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        kafkaTopicsConfig.setKafkaDownSimulated(false);
        sessionService.setCheckoutGraceMs(50L);
        mailSenderService.clearSentSessionEmails();
        sessionKafkaConsumer.clearDltRecords();
        outboxRepository.deleteAll();
    }

    private Job createTestJob(UUID userId, String company, String title) {
        Job job = new Job();
        job.setUserId(userId);
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation("Bengaluru");
        job.setWorkMode("HYBRID");
        job.setSource("company_ats");
        job.setRawDescription("Java 17, Spring Boot, Apache Kafka, PostgreSQL");
        job.setParsedJson("{}");
        job.setRequiredSkillsJson("[\"Java\",\"Spring Boot\",\"Apache Kafka\"]");
        job.setNiceToHaveSkillsJson("[]");
        return jobRepository.saveAndFlush(job);
    }

    private record AuthSessionInfo(
            UUID userId,
            String email,
            String token,
            UUID sessionId,
            String beaconToken
    ) {}

    private AuthSessionInfo registerAndEnableSummaryEmail(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@skillsignal.in";
        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "SecurePass#2026",
                                "fullName", "Aarav Sharma"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(regRes.getResponse().getContentAsString());
        String token = root.get("token").asText();
        UUID sessionId = UUID.fromString(root.get("sessionId").asText());
        String beaconToken = root.get("beaconToken").asText();
        UUID userId = UUID.fromString(root.get("user").get("id").asText());

        // Grant EMAIL_NOTIFICATIONS consent -> enables session_summary_email_enabled by default
        mockMvc.perform(put("/api/consents/EMAIL_NOTIFICATIONS")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "granted", true,
                                "consentVersion", "v1.0"
                        ))))
                .andExpect(status().isOk());

        return new AuthSessionInfo(userId, email, token, sessionId, beaconToken);
    }

    private void awaitCondition(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    // =========================================================================
    // 1. Login creates a session; logout closes it; double checkout is idempotent
    // =========================================================================
    @Test
    void test01_loginCreatesSession_logoutClosesIt_doubleCheckoutIsIdempotent() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t1");

        // Verify session exists and is open
        UserSession openSession = sessionRepository.findById(auth.sessionId()).orElseThrow();
        assertThat(openSession.getUserId()).isEqualTo(auth.userId());
        assertThat(openSession.getCheckedInAt()).isNotNull();
        assertThat(openSession.getCheckedOutAt()).isNull();
        assertThat(openSession.getTimeZone()).isEqualTo("Asia/Kolkata");

        // Explicit checkout (1st call)
        MvcResult firstCheckout = mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstJson = objectMapper.readTree(firstCheckout.getResponse().getContentAsString());
        assertThat(firstJson.get("checkedOutAt").asText()).isNotBlank();
        assertThat(firstJson.get("checkoutReason").asText()).isEqualTo("LOGOUT");

        long checkoutOutboxCountAfterFirst = outboxRepository.findAll().stream()
                .filter(e -> auth.userId().equals(e.getUserId())
                        && KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT.equals(e.getTopic()))
                .count();
        assertThat(checkoutOutboxCountAfterFirst).isEqualTo(1L);

        // Explicit checkout (2nd call -> idempotent, does NOT emit a second session.checked-out event)
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        long checkoutOutboxCountAfterSecond = outboxRepository.findAll().stream()
                .filter(e -> auth.userId().equals(e.getUserId())
                        && KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT.equals(e.getTopic()))
                .count();
        assertThat(checkoutOutboxCountAfterSecond).isEqualTo(1L);
    }

    // =========================================================================
    // 2. Zero applications -> check-out: NO email sent, SKIPPED_NO_APPLICATIONS
    // =========================================================================
    @Test
    void test02_zeroApplications_checkoutSendsNoEmailAndMarksSkippedNoApplications() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t2");

        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(8), () -> {
            UserSession s = sessionRepository.findById(auth.sessionId()).orElseThrow();
            return "SKIPPED_NO_APPLICATIONS".equals(s.getSummaryEmailStatus());
        });

        assertThat(summaryEmailRepository.findBySessionId(auth.sessionId())).isEmpty();
        assertThat(mailSenderService.getSentSessionEmails().stream()
                .filter(m -> auth.email().equals(m.recipientEmail()))
                .count()).isZero();
    }

    // =========================================================================
    // 3. One application -> check-out: 1 email with singular subject & truthful status
    // =========================================================================
    @Test
    void test03_oneApplication_checkoutSendsSingleEmailWithSingularSubject() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t3");
        Job razorpayJob = createTestJob(auth.userId(), "Razorpay", "Senior Backend Engineer (Java/Kafka)");

        // Record 1 qualifying user-confirmed application via /api/applications
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + auth.token())
                        .header("X-Session-Id", auth.sessionId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", razorpayJob.getId().toString(),
                                "source", "company_ats",
                                "status", "APPLIED",
                                "submissionMethod", "Applied on company page",
                                "confirmationType", "USER_CONFIRMED"
                        ))))
                .andExpect(status().isCreated());

        // Check out
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        // Relay outbox events through Kafka
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );

        SessionSummaryEmail email = summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow();
        assertThat(email.getSubject()).isEqualTo("Your SkillSignal session: you applied to 1 company");
        assertThat(email.getBodyHtml()).contains("Razorpay");
        assertThat(email.getBodyHtml()).contains("Senior Backend Engineer (Java/Kafka)");
        assertThat(email.getBodyHtml()).contains("Applied on company page");
        assertThat(email.getBodyHtml()).contains("User confirmed");
        assertThat(email.getBodyText()).contains("Statuses show what SkillSignal could confirm. Check each company's portal or inbox for the final result.");
    }

    // =========================================================================
    // 4. Three applications across session (company page, email-apply, agent) ->
    //    1 email listing all 3 ordered by time with truthful status labels
    // =========================================================================
    @Test
    void test04_threeApplicationsAcrossMethods_singleEmailOrderedByTimeWithTruthfulStatuses() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t4");

        Instant t1 = Instant.now().minusSeconds(180);
        Instant t2 = Instant.now().minusSeconds(120);
        Instant t3 = Instant.now().minusSeconds(60);

        // 1. Applied on company page -> USER_CONFIRMED
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "Zerodha", "Backend Systems Engineer",
                t1, "Applied on company page", "USER_CONFIRMED", auth.sessionId(), false
        );
        // 2. Sent by email -> UNCONFIRMED ("Sent, not yet confirmed")
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "PhonePe", "Distributed Systems SDE-2",
                t2, "Sent by email", "UNCONFIRMED", auth.sessionId(), false
        );
        // 3. Submitted by agent -> EXTERNALLY_CONFIRMED ("Externally confirmed")
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "Swiggy", "Platform Engineer",
                t3, "Submitted by agent", "EXTERNALLY_CONFIRMED", auth.sessionId(), false
        );

        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );

        SessionSummaryEmail email = summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow();
        assertThat(email.getSubject()).isEqualTo("Your SkillSignal session: you applied to 3 companies");

        String html = email.getBodyHtml();
        int idxZerodha = html.indexOf("Zerodha");
        int idxPhonePe = html.indexOf("PhonePe");
        int idxSwiggy = html.indexOf("Swiggy");
        assertThat(idxZerodha).isGreaterThan(0);
        assertThat(idxPhonePe).isGreaterThan(idxZerodha);
        assertThat(idxSwiggy).isGreaterThan(idxPhonePe);

        // Truthful status labels (Rule 7: never claim confirmed when only sent)
        assertThat(html).contains("User confirmed");
        assertThat(html).contains("Sent, not yet confirmed");
        assertThat(html).contains("Externally confirmed");
    }

    // =========================================================================
    // 5. User opted out: no email. No email consent: no email. Unsubscribe token works without login.
    // =========================================================================
    @Test
    void test05_optOutAndMissingConsentSkipEmail_andSignedUnsubscribeWorksWithoutLogin() throws Exception {
        // Part A: User registers but NEVER grants EMAIL_NOTIFICATIONS consent
        String rawEmail = "s7-t5-noconsent-" + UUID.randomUUID() + "@skillsignal.in";
        MvcResult regNoConsent = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", rawEmail,
                                "password", "SecurePass#2026",
                                "fullName", "No Consent User"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode rootNoConsent = objectMapper.readTree(regNoConsent.getResponse().getContentAsString());
        String tokenNoConsent = rootNoConsent.get("token").asText();
        UUID sessionNoConsent = UUID.fromString(rootNoConsent.get("sessionId").asText());
        UUID userNoConsent = UUID.fromString(rootNoConsent.get("user").get("id").asText());

        sessionService.recordQualifyingApplicationApplied(
                userNoConsent, UUID.randomUUID(), "Cred", "Java Developer",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", sessionNoConsent, false
        );
        mockMvc.perform(post("/api/sessions/" + sessionNoConsent + "/checkout")
                        .header("Authorization", "Bearer " + tokenNoConsent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(8), () -> {
            UserSession s = sessionRepository.findById(sessionNoConsent).orElseThrow();
            return "SKIPPED_USER_OPTOUT".equals(s.getSummaryEmailStatus());
        });
        assertThat(summaryEmailRepository.findBySessionId(sessionNoConsent)).isEmpty();

        // Part B: User with consent sends 1 session email, then clicks signed unsubscribe link WITHOUT login
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t5-unsub");
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "Flipkart", "SDE-2",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", auth.sessionId(), false
        );
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );
        SessionSummaryEmail sentEmail = summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow();
        String bodyText = sentEmail.getBodyText();
        int tokenIdx = bodyText.indexOf("/api/sessions/summary-email/unsubscribe?token=");
        assertThat(tokenIdx).isGreaterThan(0);
        String unsubToken = bodyText.substring(tokenIdx + "/api/sessions/summary-email/unsubscribe?token=".length()).trim().split("\\s+")[0];

        // Call unsubscribe endpoint WITHOUT Authorization header
        mockMvc.perform(get("/api/sessions/summary-email/unsubscribe")
                        .param("token", unsubToken))
                .andExpect(status().isOk());

        // Start a 2nd session for this user, apply to a company, and check out -> should be SKIPPED_USER_OPTOUT
        SessionCheckoutEmailService.CheckInResultDto secondSession = sessionService.checkIn(auth.userId());
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "Meesho", "Backend Engineer",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", secondSession.sessionId(), false
        );
        sessionService.checkOut(auth.userId(), secondSession.sessionId(), "LOGOUT");
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(8), () -> {
            UserSession s2 = sessionRepository.findById(secondSession.sessionId()).orElseThrow();
            return "SKIPPED_USER_OPTOUT".equals(s2.getSummaryEmailStatus());
        });
        assertThat(summaryEmailRepository.findBySessionId(secondSession.sessionId())).isEmpty();
    }

    // =========================================================================
    // 6. NEEDS_VERIFICATION, SUBMISSION_FAILED, and INTERESTED are never listed as applied
    // =========================================================================
    @Test
    void test06_nonQualifyingStatusesAreNeverListedAsApplied() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t6");
        Job interestedJob = createTestJob(auth.userId(), "InterestedCorp", "SDE-1");

        // Create INTERESTED application
        MvcResult interestedRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + auth.token())
                        .header("X-Session-Id", auth.sessionId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", interestedJob.getId().toString(),
                                "source", "manual",
                                "status", "INTERESTED"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String interestedAppId = objectMapper.readTree(interestedRes.getResponse().getContentAsString()).get("id").asText();

        // Transition INTERESTED -> READY_TO_APPLY -> AWAITING_APPROVAL -> SUBMISSION_FAILED (with subState=NEEDS_VERIFICATION)
        mockMvc.perform(put("/api/applications/" + interestedAppId)
                        .header("Authorization", "Bearer " + auth.token())
                        .header("X-Session-Id", auth.sessionId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "READY_TO_APPLY"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/applications/" + interestedAppId)
                        .header("Authorization", "Bearer " + auth.token())
                        .header("X-Session-Id", auth.sessionId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "AWAITING_APPROVAL"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/applications/" + interestedAppId)
                        .header("Authorization", "Bearer " + auth.token())
                        .header("X-Session-Id", auth.sessionId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "SUBMISSION_FAILED",
                                "subState", "NEEDS_VERIFICATION"
                        ))))
                .andExpect(status().isOk());

        // Check out -> zero qualifying applications, so NO email is sent
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(8), () -> {
            UserSession s = sessionRepository.findById(auth.sessionId()).orElseThrow();
            return "SKIPPED_NO_APPLICATIONS".equals(s.getSummaryEmailStatus());
        });
        assertThat(appliedItemRepository.findBySessionIdOrderByAppliedAtAsc(auth.sessionId())).isEmpty();
        assertThat(summaryEmailRepository.findBySessionId(auth.sessionId())).isEmpty();
    }

    // =========================================================================
    // 7. Idle timeout closes session & triggers summary; heartbeat within 30s cancels beacon checkout
    // =========================================================================
    @Test
    void test07_idleTimeoutClosesSessionAndSendsSummary_andHeartbeatWithinGraceCancelsBeacon() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t7");

        // Part A: Send checkout beacon (page reload), then send heartbeat within 30s -> session stays OPEN
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout-beacon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("beaconToken", auth.beaconToken()))))
                .andExpect(status().isAccepted());

        UserSession afterBeacon = sessionRepository.findById(auth.sessionId()).orElseThrow();
        assertThat(afterBeacon.getBeaconReceivedAt()).isNotNull();
        assertThat(afterBeacon.getCheckedOutAt()).isNull();

        // Heartbeat arrives within 30s -> cancels pending beacon checkout
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/heartbeat")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        UserSession afterHeartbeat = sessionRepository.findById(auth.sessionId()).orElseThrow();
        assertThat(afterHeartbeat.getBeaconReceivedAt()).isNull();
        assertThat(afterHeartbeat.getCheckedOutAt()).isNull();

        // Part B: Record an application, then simulate 16 minutes of idle inactivity
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "Zomato", "Backend Engineer",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", auth.sessionId(), false
        );
        sessionService.relayPendingEvents();

        Instant futureEvalTime = Instant.now().plus(16, ChronoUnit.MINUTES);
        int closed = sessionService.closeExpiredIdleAndBeaconSessionsAt(futureEvalTime);
        assertThat(closed).isGreaterThanOrEqualTo(1);

        UserSession idleClosed = sessionRepository.findById(auth.sessionId()).orElseThrow();
        assertThat(idleClosed.getCheckoutReason()).isEqualTo("IDLE_TIMEOUT");
        assertThat(idleClosed.getCheckedOutAt()).isNotNull();

        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );
        assertThat(summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow().getBodyHtml())
                .contains("Zomato");
    }

    // =========================================================================
    // 8. Duplicate Kafka delivery (same eventId twice): 1 item, 1 email
    // =========================================================================
    @Test
    void test08_duplicateKafkaDelivery_producesOneItemAndOneEmail() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t8");

        OutboxEvent event = sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "Freshworks", "Staff Engineer",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", auth.sessionId(), false
        );

        // Deliver the exact same payloadJson twice to the consumer
        sessionService.handleApplicationAppliedMessage(event.getPayloadJson());
        sessionService.handleApplicationAppliedMessage(event.getPayloadJson());

        List<SessionAppliedItem> items = appliedItemRepository.findBySessionIdOrderByAppliedAtAsc(auth.sessionId());
        assertThat(items).hasSize(1);

        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );

        // Even if renderAndSendSummaryEmailForSession is invoked a second time, only 1 email is ever sent
        long beforeCount = mailSenderService.getSentSessionEmails().stream()
                .filter(m -> auth.email().equals(m.recipientEmail()))
                .count();
        sessionService.renderAndSendSummaryEmailForSession(auth.sessionId());
        long afterCount = mailSenderService.getSentSessionEmails().stream()
                .filter(m -> auth.email().equals(m.recipientEmail()))
                .count();
        assertThat(beforeCount).isEqualTo(1L);
        assertThat(afterCount).isEqualTo(1L);
    }

    // =========================================================================
    // 9. Kafka down during application: app still works, event buffered in outbox_events;
    //    after Kafka returns, relay publishes and email arrives once
    // =========================================================================
    @Test
    void test09_kafkaDownDuringApplication_bufferedInOutboxAndDeliveredOnceKafkaRecovers() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t9");
        Job juspayJob = createTestJob(auth.userId(), "Juspay", "Core Payments Engineer");

        // Simulate Kafka broker down
        kafkaTopicsConfig.setKafkaDownSimulated(true);

        // Application creation still succeeds (201 Created) because it writes to outbox_events in DB!
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + auth.token())
                        .header("X-Session-Id", auth.sessionId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", juspayJob.getId().toString(),
                                "source", "company_ats",
                                "status", "APPLIED",
                                "submissionMethod", "Applied on company page",
                                "confirmationType", "USER_CONFIRMED"
                        ))))
                .andExpect(status().isCreated());

        // Relay attempt while Kafka is down increments attempts and keeps publishedAt == null
        int publishedWhileDown = sessionService.relayPendingEvents();
        assertThat(publishedWhileDown).isZero();

        List<OutboxEvent> pendingForUser = outboxRepository.findAll().stream()
                .filter(e -> auth.userId().equals(e.getUserId()) && e.getPublishedAt() == null)
                .toList();
        assertThat(pendingForUser).isNotEmpty();
        assertThat(pendingForUser.get(0).getAttempts()).isGreaterThanOrEqualTo(1);

        // Check out while Kafka is still down
        mockMvc.perform(post("/api/sessions/" + auth.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isOk());

        // Now Kafka recovers!
        kafkaTopicsConfig.setKafkaDownSimulated(false);
        int publishedAfterRecovery = sessionService.relayPendingEvents();
        assertThat(publishedAfterRecovery).isGreaterThanOrEqualTo(2);

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );

        SessionSummaryEmail email = summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow();
        assertThat(email.getBodyHtml()).contains("Juspay");
    }

    // =========================================================================
    // 10. Late application.applied before grace period ends is included;
    //     poison message goes to .DLT after 3 retries without blocking valid messages;
    //     late event after summary email was already sent never sends a second email
    // =========================================================================
    @Test
    void test10_lateEventIncludedWithinGrace_poisonMessageRoutedToDlt_andPostSendLateEventNeverSendsSecondEmail() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t10");

        // Set grace period to 250ms so a late application.applied event arriving before grace ends is included
        sessionService.setCheckoutGraceMs(250L);

        // Publish a poison message to application.applied -> should be retried 3 times and routed to application.applied.DLT
        kafkaTemplate.send(KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED, auth.userId().toString(), "{not-valid-json-poison")
                .get(5, TimeUnit.SECONDS);

        awaitCondition(Duration.ofSeconds(12), () ->
                sessionKafkaConsumer.getDltRecords().stream()
                        .anyMatch(r -> KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED_DLT.equals(r.topic()))
        );

        // Now record a valid application and check out -> valid message is NOT blocked by the poison message
        sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "BrowserStack", "Senior Infrastructure Engineer",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", auth.sessionId(), false
        );
        sessionService.checkOut(auth.userId(), auth.sessionId(), "LOGOUT");
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );
        assertThat(summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow().getBodyHtml())
                .contains("BrowserStack");

        // Now send a late application.applied event for the ALREADY-SENT session -> must NOT send a second email!
        OutboxEvent veryLateEvent = sessionService.recordQualifyingApplicationApplied(
                auth.userId(), UUID.randomUUID(), "LateArrivalCorp", "Late Role",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", auth.sessionId(), false
        );
        sessionService.handleApplicationAppliedMessage(veryLateEvent.getPayloadJson());

        long emailCountForUser = mailSenderService.getSentSessionEmails().stream()
                .filter(m -> auth.email().equals(m.recipientEmail()))
                .count();
        assertThat(emailCountForUser).isEqualTo(1L);
    }

    // =========================================================================
    // 11. Two tabs/devices & user isolation: each session handled separately,
    //     User A cannot close User B's session (404) and never receives User B's companies
    // =========================================================================
    @Test
    void test11_twoSessionsAndUserIsolation_noCrossMixingAndCrossUserCheckoutReturns404() throws Exception {
        AuthSessionInfo userA = registerAndEnableSummaryEmail("s7-t11-userA");
        AuthSessionInfo userB = registerAndEnableSummaryEmail("s7-t11-userB");

        // User A opens a second tab/device session (sessionA2)
        SessionCheckoutEmailService.CheckInResultDto sessionA2 = sessionService.checkIn(userA.userId());

        // Apply to CompanyA1 in sessionA1, CompanyA2 in sessionA2, and CompanyB in userB's session
        sessionService.recordQualifyingApplicationApplied(
                userA.userId(), UUID.randomUUID(), "CompanyTabOne", "Role 1",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", userA.sessionId(), false
        );
        sessionService.recordQualifyingApplicationApplied(
                userA.userId(), UUID.randomUUID(), "CompanyTabTwo", "Role 2",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", sessionA2.sessionId(), false
        );
        sessionService.recordQualifyingApplicationApplied(
                userB.userId(), UUID.randomUUID(), "SecretUserBCompany", "Confidential Role",
                Instant.now(), "Applied on company page", "USER_CONFIRMED", userB.sessionId(), false
        );

        // User A attempts to check out User B's session -> 404 Not Found
        mockMvc.perform(post("/api/sessions/" + userB.sessionId() + "/checkout")
                        .header("Authorization", "Bearer " + userA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"LOGOUT\"}"))
                .andExpect(status().isNotFound());

        // Close User A's Tab 1 session while Tab 2 remains open
        sessionService.checkOut(userA.userId(), userA.sessionId(), "LOGOUT");
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(userA.sessionId()).isPresent()
        );

        SessionSummaryEmail emailA1 = summaryEmailRepository.findBySessionId(userA.sessionId()).orElseThrow();
        assertThat(emailA1.getBodyHtml()).contains("CompanyTabOne");
        assertThat(emailA1.getBodyHtml()).doesNotContain("CompanyTabTwo");
        assertThat(emailA1.getBodyHtml()).doesNotContain("SecretUserBCompany");

        // Tab 2 is still open; now close Tab 2
        assertThat(sessionRepository.findById(sessionA2.sessionId()).orElseThrow().getCheckedOutAt()).isNull();
        sessionService.checkOut(userA.userId(), sessionA2.sessionId(), "LOGOUT");
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(sessionA2.sessionId()).isPresent()
        );
        SessionSummaryEmail emailA2 = summaryEmailRepository.findBySessionId(sessionA2.sessionId()).orElseThrow();
        assertThat(emailA2.getBodyHtml()).contains("CompanyTabTwo");
        assertThat(emailA2.getBodyHtml()).doesNotContain("CompanyTabOne");
        assertThat(emailA2.getBodyHtml()).doesNotContain("SecretUserBCompany");
    }

    // =========================================================================
    // 12. Time zone display (Asia/Kolkata IST) and HTML escaping of <script> in company name
    // =========================================================================
    @Test
    void test12_timeZoneDisplayAndHtmlEscapingOfScriptTags() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t12");

        sessionService.recordQualifyingApplicationApplied(
                auth.userId(),
                UUID.randomUUID(),
                "EvilCorp <script>alert('xss')</script> & Co",
                "Security Engineer <img src=x onerror=alert(1)>",
                Instant.now(),
                "Applied on company page",
                "USER_CONFIRMED",
                auth.sessionId(),
                false
        );

        sessionService.checkOut(auth.userId(), auth.sessionId(), "LOGOUT");
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );

        SessionSummaryEmail email = summaryEmailRepository.findBySessionId(auth.sessionId()).orElseThrow();
        String html = email.getBodyHtml();
        assertThat(html).contains("Asia/Kolkata");
        assertThat(html).contains("IST");
        assertThat(html).contains("EvilCorp &lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt; &amp; Co");
        assertThat(html).doesNotContain("<script>alert('xss')</script>");
        assertThat(html).doesNotContain("<img src=x");
    }

    // =========================================================================
    // 13. Email rate limit (20/day) and 50-row truncation ("...and 5 more")
    // =========================================================================
    @Test
    void test13_fiftyRowTruncationAndDailyEmailRateLimit() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t13");

        // Part A: 55 applications in 1 session -> email shows first 50 and "...and 5 more."
        for (int i = 1; i <= 55; i++) {
            SessionAppliedItem item = new SessionAppliedItem();
            item.setSessionId(auth.sessionId());
            item.setUserId(auth.userId());
            item.setApplicationId(UUID.randomUUID());
            item.setCompany("BulkCompany-" + String.format("%02d", i));
            item.setJobTitle("Engineer " + i);
            item.setAppliedAt(Instant.now().plusMillis(i * 10L));
            item.setMethod("Applied on company page");
            item.setConfirmationType("USER_CONFIRMED");
            item.setWhileAway(false);
            item.setEventId(UUID.randomUUID());
            appliedItemRepository.saveAndFlush(item);
        }

        sessionService.checkOut(auth.userId(), auth.sessionId(), "LOGOUT");
        Optional<SessionSummaryEmail> truncatedEmailOpt = sessionService.renderAndSendSummaryEmailForSession(auth.sessionId());
        assertThat(truncatedEmailOpt).isPresent();
        SessionSummaryEmail truncatedEmail = truncatedEmailOpt.get();
        assertThat(truncatedEmail.getSubject()).isEqualTo("Your SkillSignal session: you applied to 55 companies");
        assertThat(truncatedEmail.getBodyHtml()).contains("BulkCompany-01");
        assertThat(truncatedEmail.getBodyHtml()).contains("BulkCompany-50");
        assertThat(truncatedEmail.getBodyHtml()).doesNotContain("BulkCompany-55");
        assertThat(truncatedEmail.getBodyHtml()).contains("...and 5 more.");

        // Part B: Simulate 19 more sent summary emails in the last 24h (total 20) -> 21st session is rate-limited
        for (int i = 0; i < 19; i++) {
            UserSession dummySession = new UserSession();
            dummySession.setUserId(auth.userId());
            dummySession.setBeaconTokenHash("hash-" + i);
            dummySession.setCheckedInAt(Instant.now().minusSeconds(600));
            dummySession.setLastSeenAt(Instant.now().minusSeconds(300));
            dummySession.setCheckedOutAt(Instant.now().minusSeconds(300));
            dummySession.setCheckoutReason("LOGOUT");
            dummySession.setTimeZone("Asia/Kolkata");
            dummySession.setSummaryEmailStatus("SENT");
            dummySession = sessionRepository.saveAndFlush(dummySession);

            SessionSummaryEmail histEmail = new SessionSummaryEmail();
            histEmail.setSessionId(dummySession.getId());
            histEmail.setUserId(auth.userId());
            histEmail.setSentAt(Instant.now().minusSeconds(60));
            histEmail.setProviderMessageId("msg-rate-" + i);
            histEmail.setStatus("SENT");
            histEmail.setSubject("Historical summary");
            histEmail.setBodyHtml("<p>Summary</p>");
            histEmail.setBodyText("Summary");
            summaryEmailRepository.saveAndFlush(histEmail);
        }

        // 21st session within 24h -> blocked by rate limit (max 20/day)
        SessionCheckoutEmailService.CheckInResultDto session21 = sessionService.checkIn(auth.userId());
        SessionAppliedItem item21 = new SessionAppliedItem();
        item21.setSessionId(session21.sessionId());
        item21.setUserId(auth.userId());
        item21.setApplicationId(UUID.randomUUID());
        item21.setCompany("RateLimitedCompany");
        item21.setJobTitle("SDE");
        item21.setAppliedAt(Instant.now());
        item21.setMethod("Applied on company page");
        item21.setConfirmationType("USER_CONFIRMED");
        item21.setWhileAway(false);
        item21.setEventId(UUID.randomUUID());
        appliedItemRepository.saveAndFlush(item21);

        sessionService.checkOut(auth.userId(), session21.sessionId(), "LOGOUT");
        Optional<SessionSummaryEmail> blockedOpt = sessionService.renderAndSendSummaryEmailForSession(session21.sessionId());
        assertThat(blockedOpt).isEmpty();
        assertThat(sessionRepository.findById(session21.sessionId()).orElseThrow().getSummaryEmailStatus())
                .isEqualTo("FAILED");
    }

    // =========================================================================
    // 14. Privacy & security scan: no resume text, screening answers, salary data,
    //     or tokens in outbox_events or session_summary_emails
    // =========================================================================
    @Test
    void test14_privacyScan_noResumeTextAnswersSalaryOrTokensInEventsOrEmails() throws Exception {
        AuthSessionInfo auth = registerAndEnableSummaryEmail("s7-t14");

        sessionService.recordQualifyingApplicationApplied(
                auth.userId(),
                UUID.randomUUID(),
                "Infosys",
                "Lead Java Microservices Architect",
                Instant.now(),
                "Applied on company page",
                "USER_CONFIRMED",
                auth.sessionId(),
                false
        );
        sessionService.checkOut(auth.userId(), auth.sessionId(), "LOGOUT");
        sessionService.relayPendingEvents();

        awaitCondition(Duration.ofSeconds(10), () ->
                summaryEmailRepository.findBySessionId(auth.sessionId()).isPresent()
        );

        List<OutboxEvent> allOutbox = outboxRepository.findAll();
        assertThat(allOutbox).isNotEmpty();
        for (OutboxEvent event : allOutbox) {
            String json = event.getPayloadJson();
            assertThat(json).doesNotContain("parsedText");
            assertThat(json).doesNotContain("resumeText");
            assertThat(json).doesNotContain("screeningAnswer");
            assertThat(json).doesNotContain("expectedCtc");
            assertThat(json).doesNotContain("currentCtc");
            assertThat(json).doesNotContain("passwordHash");
            assertThat(json).doesNotContain(auth.token());
            assertThat(json).doesNotContain(auth.beaconToken());
        }

        List<SessionSummaryEmail> allEmails = summaryEmailRepository.findAll();
        assertThat(allEmails).isNotEmpty();
        for (SessionSummaryEmail email : allEmails) {
            assertThat(email.getBodyHtml()).doesNotContain(auth.token());
            assertThat(email.getBodyHtml()).doesNotContain(auth.beaconToken());
            assertThat(email.getBodyHtml()).doesNotContain("parsedText");
            assertThat(email.getBodyHtml()).doesNotContain("expectedCtc");
        }
    }
}
