package in.skillsignal.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage21SubmissionStateMachineAndIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldEnforceStateMachineIdempotencyAuditTrailAndNeedsVerificationResolution() throws Exception {
        // 1. Register user
        String email = "stage21." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage21Pass!123",
                                "fullName", "Stage 21 Candidate"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Create a job
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Senior Platform Engineer",
                                "company", "FinCore Systems",
                                "location", "Bengaluru",
                                "workMode", "HYBRID",
                                "rawDescription", "Looking for 3+ years experience in Java, Spring Boot, PostgreSQL, and Docker."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        // 3. Prepare submission with idempotency key
        String idempKey = "IDEMP-STAGE21-KEY-001";
        Map<String, Object> prepReq = Map.of(
                "jobId", jobId,
                "idempotencyKey", idempKey,
                "questions", List.of("What is your current city?")
        );
        MvcResult prepRes1 = mockMvc.perform(post("/api/submissions/prepare")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prepReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.whyAppliedOrSkipped").isNotEmpty())
                .andReturn();
        String submissionId = objectMapper.readTree(prepRes1.getResponse().getContentAsString()).get("id").asText();

        // 4. Repeat prepare with same idempotencyKey -> returns exact same submissionId without duplicating attempts
        MvcResult prepRes2 = mockMvc.perform(post("/api/submissions/prepare")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prepReq)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(prepRes2.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(submissionId);

        // 5. Inspect immutable audit attempts (DISCOVERED -> MATCHED -> PACK_PREPARING -> PACK_READY -> AWAITING_APPROVAL = 5 attempts)
        MvcResult attemptsRes = mockMvc.perform(get("/api/submissions/" + submissionId + "/attempts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode attemptsNode = objectMapper.readTree(attemptsRes.getResponse().getContentAsString());
        assertThat(attemptsNode.size()).isEqualTo(5);
        assertThat(attemptsNode.get(0).get("toState").asText()).isEqualTo("DISCOVERED");
        assertThat(attemptsNode.get(4).get("toState").asText()).isEqualTo("AWAITING_APPROVAL");
        assertThat(attemptsNode.get(4).get("ruleDecision").isObject()).isTrue();
        assertThat(attemptsNode.get(4).get("packSnapshot").isObject()).isTrue();

        // 6. Reject illegal transition (AWAITING_APPROVAL -> SUBMITTING directly without APPROVED is illegal -> 422)
        mockMvc.perform(post("/api/submissions/" + submissionId + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetState", "SUBMITTING",
                                "providerResponse", "Attempt direct submit"
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // 7. Legal transitions: AWAITING_APPROVAL -> APPROVED -> SUBMITTING -> NEEDS_VERIFICATION
        mockMvc.perform(post("/api/submissions/" + submissionId + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetState", "APPROVED",
                                "providerResponse", "User approved"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));

        mockMvc.perform(post("/api/submissions/" + submissionId + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetState", "SUBMITTING",
                                "providerResponse", "Agent started submission"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTING"));

        mockMvc.perform(post("/api/submissions/" + submissionId + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetState", "NEEDS_VERIFICATION",
                                "providerResponse", "Network timeout before confirmation banner"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NEEDS_VERIFICATION"));

        // 8. Golden Rule: NEEDS_VERIFICATION strictly blocks any retry (transition to SUBMITTING or APPROVED -> 422)
        mockMvc.perform(post("/api/submissions/" + submissionId + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetState", "SUBMITTING",
                                "providerResponse", "Auto-retry attempt"
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // Even transitioning NEEDS_VERIFICATION -> SUBMITTED_CONFIRMED via generic /transition (without /resolve) is blocked
        mockMvc.perform(post("/api/submissions/" + submissionId + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetState", "SUBMITTED_CONFIRMED",
                                "providerResponse", "Bypass resolve"
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // 9. User explicitly resolves NEEDS_VERIFICATION via POST /api/submissions/{id}/resolve
        mockMvc.perform(post("/api/submissions/" + submissionId + "/resolve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resolution", "SUBMITTED_CONFIRMED",
                                "note", "Received confirmation email from company ATS"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED_CONFIRMED"))
                .andExpect(jsonPath("$.applicationId").isNotEmpty());

        // 10. Check /why endpoint
        MvcResult whyRes = mockMvc.perform(get("/api/submissions/" + submissionId + "/why")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(whyRes.getResponse().getContentAsString())
                .get("whyAppliedOrSkipped").asText())
                .contains("User resolved Needs Verification as Submitted-Confirmed");
    }
}
