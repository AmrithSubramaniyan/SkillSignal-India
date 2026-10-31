package in.skillsignal.application;

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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage14FullTrackerStateMachineTest {

    @Autowired
    private ApplicationStateMachine stateMachine;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void stateMachine_enforcesAllLegalAndIllegalTransitions() {
        // Legal progressive path: INTERESTED -> READY_TO_APPLY -> AWAITING_APPROVAL -> APPLIED -> ASSESSMENT -> INTERVIEW -> ON_HOLD -> OFFER -> CLOSED
        stateMachine.validateTransition("INTERESTED", "READY_TO_APPLY");
        stateMachine.validateTransition("READY_TO_APPLY", "AWAITING_APPROVAL");
        stateMachine.validateTransition("AWAITING_APPROVAL", "APPLIED");
        stateMachine.validateTransition("APPLIED", "ASSESSMENT");
        stateMachine.validateTransition("ASSESSMENT", "INTERVIEW");
        stateMachine.validateTransition("INTERVIEW", "ON_HOLD");
        stateMachine.validateTransition("ON_HOLD", "OFFER");
        stateMachine.validateTransition("OFFER", "CLOSED");

        // Failure & verification recovery paths
        stateMachine.validateTransition("AWAITING_APPROVAL", "SUBMISSION_FAILED");
        stateMachine.validateTransition("SUBMISSION_FAILED", "READY_TO_APPLY");
        stateMachine.validateTransition("AWAITING_APPROVAL", "NEEDS_VERIFICATION");
        stateMachine.validateTransition("NEEDS_VERIFICATION", "APPLIED");
        stateMachine.validateTransition("NEEDS_VERIFICATION", "SUBMISSION_FAILED");

        // Terminal path to CLOSED
        stateMachine.validateTransition("INTERVIEW", "REJECTED");
        stateMachine.validateTransition("REJECTED", "CLOSED");
        stateMachine.validateTransition("READY_TO_APPLY", "WITHDRAWN");
        stateMachine.validateTransition("WITHDRAWN", "CLOSED");

        // Illegal transitions must throw 422 UNPROCESSABLE_ENTITY
        assertThatThrownBy(() -> stateMachine.validateTransition("INTERESTED", "OFFER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Illegal application status transition");

        assertThatThrownBy(() -> stateMachine.validateTransition("CLOSED", "INTERVIEW"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Illegal application status transition");

        assertThatThrownBy(() -> stateMachine.validateTransition("REJECTED", "OFFER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Illegal application status transition");
    }

    @Test
    void confirmationRules_unconfirmedAppliedSetsNeedsVerificationSubState() {
        var unconfirmed = stateMachine.resolveEffectiveOutcome("APPLIED", null, "UNCONFIRMED");
        assertThat(unconfirmed.effectiveStatus()).isEqualTo("APPLIED");
        assertThat(unconfirmed.effectiveSubState()).isEqualTo("NEEDS_VERIFICATION");
        assertThat(unconfirmed.effectiveConfirmationType()).isEqualTo("UNCONFIRMED");

        var userConfirmed = stateMachine.resolveEffectiveOutcome("APPLIED", null, "USER_CONFIRMED");
        assertThat(userConfirmed.effectiveStatus()).isEqualTo("APPLIED");
        assertThat(userConfirmed.effectiveSubState()).isNull();
        assertThat(userConfirmed.effectiveConfirmationType()).isEqualTo("USER_CONFIRMED");

        var externallyConfirmed = stateMachine.resolveEffectiveOutcome("APPLIED", null, "EXTERNALLY_CONFIRMED");
        assertThat(externallyConfirmed.effectiveStatus()).isEqualTo("APPLIED");
        assertThat(externallyConfirmed.effectiveSubState()).isNull();
        assertThat(externallyConfirmed.effectiveConfirmationType()).isEqualTo("EXTERNALLY_CONFIRMED");
    }

    @Test
    void fullTrackerApi_storesRichFieldsEnforcesTransitionsAndRecordsHistory() throws Exception {
        // 1. Register user
        String email = "stage14-tracker@skillsignal.in";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage14Pass!123",
                                "fullName", "Stage 14 Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Create job
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Staff Backend Engineer",
                                "company", "FinTech India",
                                "location", "Bengaluru",
                                "workMode", "HYBRID",
                                "rawDescription", "Java 17, Spring Boot, PostgreSQL, Kafka, Microservices.",
                                "source", "company_ats",
                                "sourceUrl", "https://careers.fintech.in/jobs/101"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        // 3. Create application in INTERESTED with enriched fields
        MvcResult appRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "status", "INTERESTED",
                                "submissionMethod", "COMPANY_ATS",
                                "confirmationType", "UNCONFIRMED",
                                "recruiterContacts", "Ananya Sharma (ananya@fintech.in)",
                                "assessmentDate", LocalDate.now().plusDays(4).toString(),
                                "interviewDate", LocalDate.now().plusDays(10).toString(),
                                "nextAction", "Complete system design prep",
                                "followUpDate", LocalDate.now().plusDays(5).toString(),
                                "notes", "Strong role alignment"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INTERESTED"))
                .andExpect(jsonPath("$.recruiterContacts").value("Ananya Sharma (ananya@fintech.in)"))
                .andExpect(jsonPath("$.submissionMethod").value("COMPANY_ATS"))
                .andReturn();
        String appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).get("id").asText();

        // 4. Transition to APPLIED with UNCONFIRMED -> must set subState = NEEDS_VERIFICATION
        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "APPLIED",
                                "confirmationType", "UNCONFIRMED",
                                "notes", "Portal timed out on final page"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.subState").value("NEEDS_VERIFICATION"))
                .andExpect(jsonPath("$.confirmationType").value("UNCONFIRMED"));

        // Verify filter needsVerificationOnly=true returns this application
        mockMvc.perform(get("/api/applications")
                        .param("needsVerificationOnly", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 5. User confirms submission -> clears NEEDS_VERIFICATION subState and sets appliedAt
        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "APPLIED",
                                "confirmationType", "USER_CONFIRMED",
                                "notes", "Received confirmation email from ATS"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.subState").isEmpty())
                .andExpect(jsonPath("$.confirmationType").value("USER_CONFIRMED"));

        // 6. Illegal transition from INTERESTED/APPLIED directly to illegal state after closing
        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "CLOSED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INTERVIEW"
                        ))))
                .andExpect(status().isUnprocessableEntity());

        // 7. Verify history events
        MvcResult eventsRes = mockMvc.perform(get("/api/applications/" + appId + "/events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode events = objectMapper.readTree(eventsRes.getResponse().getContentAsString());
        assertThat(events.size()).isGreaterThanOrEqualTo(3);
    }
}

