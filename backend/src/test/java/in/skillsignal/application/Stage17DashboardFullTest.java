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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage17DashboardFullTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dashboardFull_returnsTodaysActionsUpcomingInterviewsAwaitingApprovalAndAutoApplyStatus() throws Exception {
        // 1. Register user
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "stage17.dashboard@skillsignal.test",
                                "password", "Stage17Pass!123",
                                "fullName", "VikramDesai"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Create two jobs
        MvcResult job1 = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Principal Engineer",
                                "company", "InfraTech India",
                                "rawDescription", "Java 17, Spring Boot, PostgreSQL."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String job1Id = objectMapper.readTree(job1.getResponse().getContentAsString()).get("id").asText();

        MvcResult job2 = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Staff Data Engineer",
                                "company", "DataPulse",
                                "rawDescription", "Python, SQL, Spark."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String job2Id = objectMapper.readTree(job2.getResponse().getContentAsString()).get("id").asText();

        // 3. Create Application 1 in AWAITING_APPROVAL
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", job1Id,
                                "status", "AWAITING_APPROVAL",
                                "followUpDate", LocalDate.now().toString()
                        ))))
                .andExpect(status().isCreated());

        // 4. Create Application 2 in INTERVIEW with upcoming interviewDate
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", job2Id,
                                "status", "INTERVIEW",
                                "interviewDate", LocalDate.now().plusDays(2).toString()
                        ))))
                .andExpect(status().isCreated());

        // 5. Create a due Reminder
        mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reminderType", "INTERVIEW",
                                "title", "Prepare architecture deck for DataPulse",
                                "dueAt", Instant.now().minusSeconds(60).toString()
                        ))))
                .andExpect(status().isCreated());

        // 6. Verify GET /api/dashboard
        MvcResult dashRes = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(2))
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.upcomingInterviews.length()").value(1))
                .andExpect(jsonPath("$.awaitingApproval.length()").value(1))
                .andReturn();

        JsonNode dash = objectMapper.readTree(dashRes.getResponse().getContentAsString());
        assertThat(dash.get("autoApplyStatus").asText()).containsIgnoringCase("Assisted Apply");
        assertThat(dash.get("todaysActions").size()).isGreaterThanOrEqualTo(3);
    }
}

