package in.skillsignal.emailapply;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.audit.AuditLog;
import in.skillsignal.audit.AuditLogRepository;
import in.skillsignal.resume.Resume;
import in.skillsignal.resume.ResumeRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage25EmailApplyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConnectedMailboxRepository mailboxRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeVersionRepository resumeVersionRepository;

    @Test
    void oauthMailboxEncryptionNoPasswordSendOnlyAfterApprovalSentNotConfirmedAckUpgradeAndDisconnectRevokes() throws Exception {
        // 1. Register user
        String email = "stage25." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage25Pass!123",
                                "fullName", "Priya Sharma"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        // Seed verified default resume
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setTitle("Priya Backend Resume");
        resume = resumeRepository.save(resume);

        ResumeVersion rv = new ResumeVersion();
        rv.setResumeId(resume.getId());
        rv.setUserId(userId);
        rv.setVersionNumber(1);
        rv.setOriginalFilename("priya_resume.pdf");
        rv.setContentType("application/pdf");
        rv.setFileSizeBytes(2048);
        rv.setStoragePath("./uploads/test/priya.pdf");
        rv.setExtractedText("Priya Sharma. 5 years Java, Spring Boot, PostgreSQL.");
        rv.setParsedJson("{\"skills\":[\"Java\",\"Spring Boot\",\"PostgreSQL\"],\"yearsOfExperience\":5.0}");
        rv.setVerifiedSkillsJson("[\"Java\",\"Spring Boot\",\"PostgreSQL\"]");
        rv.setYearsOfExperience(5.0);
        rv.setDefault(true);
        rv = resumeVersionRepository.save(rv);

        // 2. Reject any attempt to pass an email password
        mockMvc.perform(post("/api/email-apply/mailboxes/connect")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "provider", "GMAIL",
                                "emailAddress", "priya@gmail.com",
                                "oauthAccessToken", "ya29.mock-secret-oauth-token-999",
                                "password", "MyRawGmailPassword!"
                        ))))
                .andExpect(status().isBadRequest());

        // 3. Connect Gmail OAuth with ackDetectionEnabled=true -> stores AES-GCM encrypted token ref, never raw token
        String rawSecretOAuthToken = "ya29.secret-gmail-oauth-token-do-not-log-777";
        MvcResult mbRes = mockMvc.perform(post("/api/email-apply/mailboxes/connect")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "provider", "GMAIL",
                                "emailAddress", "priya@gmail.com",
                                "oauthAccessToken", rawSecretOAuthToken,
                                "ackDetectionEnabled", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("GMAIL"))
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.scopes").value("https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.readonly"))
                .andReturn();
        String responseBody = mbRes.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(rawSecretOAuthToken);

        String mailboxId = objectMapper.readTree(responseBody).get("id").asText();
        ConnectedMailbox storedMb = mailboxRepository.findById(UUID.fromString(mailboxId)).orElseThrow();
        assertThat(storedMb.getEncryptedTokenRef()).isNotBlank();
        assertThat(storedMb.getEncryptedTokenRef()).doesNotContain(rawSecretOAuthToken);
        assertThat(storedMb.toString()).doesNotContain(rawSecretOAuthToken);

        // Verify audit logs never contain the raw OAuth token
        List<AuditLog> logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (AuditLog log : logs) {
            assertThat(log.getMetadataJson()).doesNotContain(rawSecretOAuthToken);
        }

        // 4. Create a job and draft recruiter email with resume attached
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Lead Backend Engineer",
                                "company", "PayStream India",
                                "rawDescription", "Java, Spring Boot, PostgreSQL."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        MvcResult draftRes = mockMvc.perform(post("/api/email-apply/messages/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "recipientEmail", "careers@paystream.in"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.approvedByUser").value(false))
                .andExpect(jsonPath("$.attachedResumeFilename").value("priya_resume.pdf"))
                .andReturn();
        String msgId = objectMapper.readTree(draftRes.getResponse().getContentAsString()).get("id").asText();

        // 5. Sending before user approval is rejected with 422
        mockMvc.perform(post("/api/email-apply/messages/" + msgId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // 6. Approve and send -> status is "SENT" (not confirmed!), providerMessageId stored, tracker Application is NEEDS_VERIFICATION (UNCONFIRMED)
        mockMvc.perform(post("/api/email-apply/messages/" + msgId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedByUser").value(true));

        MvcResult sendRes = mockMvc.perform(post("/api/email-apply/messages/" + msgId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.providerMessageId").isNotEmpty())
                .andReturn();
        String appId = objectMapper.readTree(sendRes.getResponse().getContentAsString()).get("applicationId").asText();

        mockMvc.perform(get("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_VERIFICATION"))
                .andExpect(jsonPath("$.confirmationType").value("UNCONFIRMED"));

        // 7. Optional acknowledgement detection upgrades message and tracker Application to EXTERNALLY_CONFIRMED
        mockMvc.perform(post("/api/email-apply/messages/" + msgId + "/detect-ack")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ackSubjectOrSnippet", "Thank you for applying to PayStream India — we received your application"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTERNALLY_CONFIRMED"))
                .andExpect(jsonPath("$.ackDetectedAt").isNotEmpty());

        mockMvc.perform(get("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.confirmationType").value("EXTERNALLY_CONFIRMED"));

        // 8. Disconnect mailbox -> revokes token and blocks sending new drafts
        mockMvc.perform(post("/api/email-apply/mailboxes/" + mailboxId + "/disconnect")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }
}
