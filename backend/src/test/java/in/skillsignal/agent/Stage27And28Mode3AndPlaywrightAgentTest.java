package in.skillsignal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.consent.ConsentRecord;
import in.skillsignal.consent.ConsentRecordRepository;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.AutoApplyPreferenceRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage27And28Mode3AndPlaywrightAgentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplyAgentRepository agentRepository;

    @Autowired
    private ApplyTaskRepository taskRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private AutoApplyPreferenceRepository preferenceRepository;

    @Test
    void mode3EnablementDryRunTypedConsentRevocationAndPlaywrightAgentScenarios() throws Exception {
        String email = "stage2728." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage2728Pass!123",
                                "fullName", "Vikram Nair"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        // Clear mock portal submit clicks
        mockMvc.perform(delete("/mock-portal/submit-clicks"))
                .andExpect(status().isOk());

        // 1. STAGE 27: Attempt to enable Mode 3 on LinkedIn -> blocked by capability record (422)
        mockMvc.perform(put("/api/portals/linkedin/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "consentEnabled", true,
                                "applyMode", "AUTO_SUBMIT",
                                "typedConfirmation", AgentAndPortalService.REQUIRED_TYPED_CONFIRMATION
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // 2. Attempt to enable Mode 3 on mock_portal BEFORE mandatory dry run -> blocked (422)
        mockMvc.perform(put("/api/portals/mock_portal/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "consentEnabled", true,
                                "applyMode", "AUTO_SUBMIT",
                                "typedConfirmation", AgentAndPortalService.REQUIRED_TYPED_CONFIRMATION,
                                "allowMockPortalAutoSubmitOverride", true
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // 3. Run mandatory dry run (POST /api/auto-apply/runs?dry_run=true) -> creates ZERO submissions
        mockMvc.perform(post("/api/auto-apply/runs?dry_run=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true));

        // 4. Attempt Mode 3 with wrong typed confirmation -> blocked (422)
        mockMvc.perform(put("/api/portals/mock_portal/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "consentEnabled", true,
                                "applyMode", "AUTO_SUBMIT",
                                "typedConfirmation", "I AGREE",
                                "allowMockPortalAutoSubmitOverride", true
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // 5. Enable Mode 3 on mock_portal with dry run + exact typed confirmation + versioned consent -> succeeds & lowers caps to <=3/day, <=10/week
        mockMvc.perform(put("/api/portals/mock_portal/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "consentEnabled", true,
                                "consentVersion", "v2.1-mode3",
                                "applyMode", "AUTO_SUBMIT",
                                "typedConfirmation", AgentAndPortalService.REQUIRED_TYPED_CONFIRMATION,
                                "allowMockPortalAutoSubmitOverride", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setting.consentEnabled").value(true))
                .andExpect(jsonPath("$.setting.applyMode").value("AUTO_SUBMIT"))
                .andExpect(jsonPath("$.setting.consentVersion").value("v2.1-mode3"));

        AutoApplyPreference pref = preferenceRepository.findById(userId).orElseThrow();
        assertThat(pref.getDailyCap()).isEqualTo(3);
        assertThat(pref.getWeeklyCap()).isEqualTo(10);

        ConsentRecord consent = consentRecordRepository
                .findByUserIdAndConsentType(userId, "PORTAL_AUTOMATION_MOCK_PORTAL")
                .orElseThrow();
        assertThat(consent.isGranted()).isTrue();
        assertThat(consent.getConsentVersion()).isEqualTo("v2.1-mode3");

        // 6. STAGE 28: Register apply-agent -> returns raw deviceToken once, stores only SHA-256 hash
        MvcResult agentReg = mockMvc.perform(post("/api/agents/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("agentName", "Vikram Desktop Agent"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceToken").isNotEmpty())
                .andReturn();
        String rawDeviceToken = objectMapper.readTree(agentReg.getResponse().getContentAsString()).get("deviceToken").asText();
        String agentId = objectMapper.readTree(agentReg.getResponse().getContentAsString()).get("agent").get("id").asText();

        ApplyAgent savedAgent = agentRepository.findById(UUID.fromString(agentId)).orElseThrow();
        assertThat(savedAgent.getDeviceTokenHash()).doesNotContain(rawDeviceToken);

        // Create 8 jobs for testing all scenarios
        String[] jobIds = new String[8];
        for (int i = 0; i < 8; i++) {
            MvcResult jr = mockMvc.perform(post("/api/jobs")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", "Software Engineer #" + i,
                                    "company", "PortalCorp #" + i,
                                    "rawDescription", "Java, Spring Boot, SQL"
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            jobIds[i] = objectMapper.readTree(jr.getResponse().getContentAsString()).get("id").asText();
        }

        // Raise dailyCap to 7 temporarily so we can run 7 distinct tasks, then test cap reached on the 8th
        pref.setDailyCap(7);
        pref.setWeeklyCap(10);
        preferenceRepository.save(pref);

        // 7. Scenario A: FILL_ONLY task -> never clicks submit on mock portal!
        MvcResult tFillRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[0],
                                "portal", "mock_portal",
                                "applyMode", "FILL_ONLY"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applyMode").value("FILL_ONLY"))
                .andReturn();
        String taskFillId = objectMapper.readTree(tFillRes.getResponse().getContentAsString()).get("id").asText();

        // Double-submit prevention: queueing another task for jobIds[0] returns 409 Conflict
        mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[0],
                                "portal", "mock_portal",
                                "applyMode", "FILL_ONLY"
                        ))))
                .andExpect(status().isConflict());

        // Agent pulls next task using X-Agent-Token
        mockMvc.perform(get("/api/agents/tasks/next")
                        .header("X-Agent-Token", rawDeviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskFillId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Reject any report containing password or cookie with 400 Bad Request
        mockMvc.perform(post("/api/agents/tasks/" + taskFillId + "/report")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "outcome", "FILL_ONLY",
                                "resultDetails", Map.of("password", "leaked123", "cookie", "session=abc")
                        ))))
                .andExpect(status().isBadRequest());

        // Report clean FILL_ONLY completion -> status FILL_ONLY_COMPLETED, mock portal submit-clicks = 0
        mockMvc.perform(post("/api/agents/tasks/" + taskFillId + "/report")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "outcome", "FILL_ONLY",
                                "resultDetails", Map.of("filledFields", 2, "submitClicked", false)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILL_ONLY_COMPLETED"));

        mockMvc.perform(get("/mock-portal/submit-clicks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // 8. Scenario B: Mode 3 AUTO_SUBMIT with confirmation signal -> SUBMITTED_CONFIRMED
        MvcResult tConfRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[1],
                                "portal", "mock_portal",
                                "applyMode", "AUTO_SUBMIT"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskConfId = objectMapper.readTree(tConfRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/agents/tasks/" + taskConfId + "/report")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "outcome", "CONFIRMED",
                                "confirmationSignal", "MOCK-CONF-1001",
                                "resultDetails", Map.of("submitClicked", true)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED_CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSignal").value("MOCK-CONF-1001"));

        // Attach and delete evidence
        MvcResult evRes = mockMvc.perform(post("/api/agents/tasks/" + taskConfId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evidenceType", "CONFIRMATION_SCREENSHOT",
                                "storagePath", "./uploads/evidence/conf-1001.png",
                                "summaryNote", "Confirmation banner MOCK-CONF-1001"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String evId = objectMapper.readTree(evRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/agents/evidence/" + evId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 9. Scenario C: No confirmation signal -> NEEDS_VERIFICATION (never assumes Applied)
        MvcResult tNoConfRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[2],
                                "portal", "mock_portal",
                                "applyMode", "AUTO_SUBMIT"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskNoConfId = objectMapper.readTree(tNoConfRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/agents/tasks/" + taskNoConfId + "/report")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "outcome", "NOT_CONFIRMED",
                                "resultDetails", Map.of("reason", "No confirmation banner found")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_VERIFICATION"));

        // 10. Scenario D: External redirect -> ASSISTED_FALLBACK & Closed listing -> SKIPPED_CLOSED
        MvcResult tRedirRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[3],
                                "portal", "mock_portal",
                                "applyMode", "AUTO_SUBMIT"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskRedirId = objectMapper.readTree(tRedirRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/agents/tasks/" + taskRedirId + "/report")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outcome", "EXTERNAL_REDIRECT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSISTED_FALLBACK"));

        MvcResult tClosedRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[4],
                                "portal", "mock_portal",
                                "applyMode", "AUTO_SUBMIT"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskClosedId = objectMapper.readTree(tClosedRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/agents/tasks/" + taskClosedId + "/report")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outcome", "CLOSED_LISTING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED_CLOSED"));

        // 11. Scenario E: Blockers (LEGAL_QUESTION, UNKNOWN_QUESTION, CAPTCHA) -> PAUSED_BLOCKER
        MvcResult tBlockerRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[5],
                                "portal", "mock_portal",
                                "applyMode", "AUTO_SUBMIT"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskBlockerId = objectMapper.readTree(tBlockerRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/agents/tasks/" + taskBlockerId + "/blocker")
                        .header("X-Agent-Token", rawDeviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "blockerType", "LEGAL_QUESTION",
                                "blockerMessage", "Paused on background check declaration"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED_BLOCKER"))
                .andExpect(jsonPath("$.blockerType").value("LEGAL_QUESTION"));

        // 12. Scenario F: Consent revocation cancels pending tasks instantly!
        MvcResult tPendingRes = mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[6],
                                "portal", "mock_portal",
                                "applyMode", "AUTO_SUBMIT"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String taskPendingId = objectMapper.readTree(tPendingRes.getResponse().getContentAsString()).get("id").asText();

        // Cap reached test: 6 active/completed tasks exist (taskClosed is SKIPPED_CLOSED so doesn't count against cap; let's set dailyCap=6)
        pref = preferenceRepository.findById(userId).orElseThrow();
        pref.setDailyCap(6);
        preferenceRepository.save(pref);

        mockMvc.perform(post("/api/agents/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobIds[7],
                                "portal", "mock_portal",
                                "applyMode", "FILL_ONLY"
                        ))))
                .andExpect(status().isTooManyRequests());

        // Now revoke portal consent -> cancels taskPendingId immediately!
        mockMvc.perform(put("/api/portals/mock_portal/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "consentEnabled", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setting.consentEnabled").value(false))
                .andExpect(jsonPath("$.cancelledTasksCount").value(1));

        assertThat(taskRepository.findById(UUID.fromString(taskPendingId)).orElseThrow().getStatus())
                .isEqualTo("CANCELLED");

        // 13. Revoke agent device token -> X-Agent-Token is rejected with 401
        mockMvc.perform(post("/api/agents/" + agentId + "/revoke")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/api/agents/tasks/next")
                        .header("X-Agent-Token", rawDeviceToken))
                .andExpect(status().isUnauthorized());
    }
}

