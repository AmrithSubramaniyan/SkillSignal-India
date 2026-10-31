package in.skillsignal.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class Stage29LearningLoopAndAutoApplyCentreTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AutoApplyPreferenceRepository preferenceRepository;

    @Test
    void learningLoopOwnDataConsentGatedSuggestionsSkipFeedbackAutoApplyCentreProviderFailuresAndCrossUserIsolation() throws Exception {
        // 1. Register User A and User B
        String emailA = "stage29a." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult regA = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailA,
                                "password", "Stage29Pass!123",
                                "fullName", "Ananya Rao"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenA = objectMapper.readTree(regA.getResponse().getContentAsString()).get("token").asText();
        User userA = userRepository.findByEmailIgnoreCase(emailA).orElseThrow();
        UUID userIdA = userA.getId();

        String emailB = "stage29b." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult regB = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailB,
                                "password", "Stage29Pass!123",
                                "fullName", "Cross User B"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenB = objectMapper.readTree(regB.getResponse().getContentAsString()).get("token").asText();

        // 2. Seed Company Tier T1 and Job + Application for User A
        mockMvc.perform(post("/api/companies")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "RazorFin India",
                                "tier", "T1",
                                "priorityOrder", 1
                        ))))
                .andExpect(status().isCreated());

        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Staff Java Engineer",
                                "company", "RazorFin India",
                                "source", "company_ats",
                                "rawDescription", "Java, Spring Boot, Kafka, Microservices"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        // Create Application in APPLIED, then transition to INTERVIEW so learning loop has a positive response
        MvcResult appRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "status", "APPLIED",
                                "source", "company_ats",
                                "confirmationType", "USER_CONFIRMED"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INTERVIEW"
                        ))))
                .andExpect(status().isOk());

        // 3. Submit Skip-Reason Feedback
        mockMvc.perform(post("/api/auto-apply/centre/skip-feedback")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "skipReason", "T1 company policy routes to Review Queue",
                                "agreeWithSkip", true,
                                "userComment", "Good guard for T1 companies"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreeWithSkip").value(true));

        // 4. GET /api/auto-apply/centre -> verify counts, learning loop breakdowns, labelled estimate, and suggestions
        MvcResult centreRes = mockMvc.perform(get("/api/auto-apply/centre")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.discovered").value(1))
                .andExpect(jsonPath("$.limitMeters.globalState").value("ACTIVE"))
                .andExpect(jsonPath("$.estimatedTimeSaved.isEstimate").value(true))
                .andExpect(jsonPath("$.learningLoop.sampleSizeTotalApplied").value(1))
                .andExpect(jsonPath("$.learningLoop.byTitle[0].title").value("Staff Java Engineer"))
                .andExpect(jsonPath("$.learningLoop.byTitle[0].responseRatePercent").value(100.0))
                .andExpect(jsonPath("$.learningLoop.byTier[0].tier").value("T1"))
                .andExpect(jsonPath("$.learningLoop.bySource[0].source").value("company_ats"))
                .andExpect(jsonPath("$.learningLoop.ruleChangeSuggestions[0].status").value("PENDING_USER_CONSENT"))
                .andReturn();

        JsonNode centreJson = objectMapper.readTree(centreRes.getResponse().getContentAsString());
        String suggestionId = centreJson.get("learningLoop").get("ruleChangeSuggestions").get(0).get("id").asText();

        // 5. Rule-change suggestion NEVER applied without explicit consent (422 when consentGranted=false)
        mockMvc.perform(post("/api/auto-apply/centre/suggestions/" + suggestionId + "/apply")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consentGranted", false))))
                .andExpect(status().isUnprocessableEntity());

        assertThat(preferenceRepository.findById(userIdA).orElseThrow().getMinMatchedSkills()).isEqualTo(3);

        // Cross-user isolation: User B cannot apply or dismiss User A's suggestion (returns 404)
        mockMvc.perform(post("/api/auto-apply/centre/suggestions/" + suggestionId + "/apply")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consentGranted", true))))
                .andExpect(status().isNotFound());

        // Apply suggestion WITH User A's explicit consent -> updates status to APPLIED_WITH_CONSENT & minMatchedSkills=4
        mockMvc.perform(post("/api/auto-apply/centre/suggestions/" + suggestionId + "/apply")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("consentGranted", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED_WITH_CONSENT"));

        assertThat(preferenceRepository.findById(userIdA).orElseThrow().getMinMatchedSkills()).isEqualTo(4);

        // 6. Verify all provider failure types (TIMEOUT, HTTP_4XX, HTTP_5XX, PARTIAL, AMBIGUOUS) lead to NEEDS_VERIFICATION
        AutoApplyPreference pref = preferenceRepository.findById(userIdA).orElseThrow();
        pref.setDailyCap(20);
        pref.setWeeklyCap(50);
        preferenceRepository.save(pref);

        for (String failureOutcome : List.of("TIMEOUT", "HTTP_4XX", "HTTP_5XX", "PARTIAL", "AMBIGUOUS")) {
            // Ensure globalState is ACTIVE in case 3 consecutive failures auto-paused
            pref = preferenceRepository.findById(userIdA).orElseThrow();
            pref.setGlobalState("ACTIVE");
            preferenceRepository.save(pref);
            mockMvc.perform(post("/api/safety/circuit-breakers/mock_portal/reset")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk());

            MvcResult jr = mockMvc.perform(post("/api/jobs")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", "Engineer " + failureOutcome,
                                    "company", "TestCo " + failureOutcome,
                                    "rawDescription", "Java Spring Boot"
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            String fJobId = objectMapper.readTree(jr.getResponse().getContentAsString()).get("id").asText();

            MvcResult tr = mockMvc.perform(post("/api/agents/tasks")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "jobId", fJobId,
                                    "portal", "mock_portal",
                                    "applyMode", "FILL_ONLY"
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            String fTaskId = objectMapper.readTree(tr.getResponse().getContentAsString()).get("id").asText();

            // Cross-user isolation on task reporting -> User B gets 404
            mockMvc.perform(post("/api/agents/tasks/" + fTaskId + "/report")
                            .header("Authorization", "Bearer " + tokenB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("outcome", failureOutcome))))
                    .andExpect(status().isNotFound());

            // User A reports provider failure -> transitions strictly to NEEDS_VERIFICATION (no auto-retry)
            mockMvc.perform(post("/api/agents/tasks/" + fTaskId + "/report")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("outcome", failureOutcome))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NEEDS_VERIFICATION"));
        }
    }
}
