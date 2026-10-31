package in.skillsignal.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage30AnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void sixMetricsSampleSizesResponseRateFormulaDisclosedSalariesOnlyAndSourceHealthTable() throws Exception {
        String email = "stage30." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage30Pass!123",
                                "fullName", "Karthik Iyer"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        // Create 1 job WITH disclosed salary and 1 job WITHOUT disclosed salary
        MvcResult j1Res = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Senior Java Engineer",
                                "company", "DisclosedTech India",
                                "rawDescription", "Java, Spring Boot, SQL"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID job1Id = UUID.fromString(objectMapper.readTree(j1Res.getResponse().getContentAsString()).get("id").asText());
        Job j1 = jobRepository.findById(job1Id).orElseThrow();
        j1.setSalaryMin(2500000.0);
        j1.setSalaryMax(3500000.0);
        j1.setSalaryCurrency("INR");
        j1.setSalaryPeriod("YEARLY");
        jobRepository.save(j1);

        MvcResult j2Res = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Backend Developer",
                                "company", "UndisclosedCorp",
                                "rawDescription", "Java, Microservices"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String job2Id = objectMapper.readTree(j2Res.getResponse().getContentAsString()).get("id").asText();

        // Create 2 applications: 1 reaches INTERVIEW, 1 stays APPLIED -> Response Rate = 50.0% (n=2)
        MvcResult a1Res = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", job1Id.toString(),
                                "status", "APPLIED",
                                "confirmationType", "USER_CONFIRMED"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String app1Id = objectMapper.readTree(a1Res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/applications/" + app1Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INTERVIEW"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", job2Id,
                                "status", "APPLIED",
                                "confirmationType", "USER_CONFIRMED"
                        ))))
                .andExpect(status().isCreated());

        // Create a FOLLOW_UP reminder and complete it
        MvcResult remRes = mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "applicationId", app1Id,
                                "reminderType", "FOLLOW_UP",
                                "title", "Follow up with DisclosedTech",
                                "dueAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String remId = objectMapper.readTree(remRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/reminders/" + remId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify GET /api/analytics
        mockMvc.perform(get("/api/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationsPerWeek.sampleSize").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.sampleSize").value(2))
                .andExpect(jsonPath("$.responseRate.sampleSize").value(2))
                .andExpect(jsonPath("$.responseRate.positiveResponses").value(1))
                .andExpect(jsonPath("$.responseRate.ratePercent").value(50.0))
                .andExpect(jsonPath("$.responseRate.formula").value(AnalyticsService.RESPONSE_RATE_FORMULA))
                .andExpect(jsonPath("$.resultsByResumeVersion.sampleSize").value(2))
                .andExpect(jsonPath("$.followUpCompletion.sampleSize").value(1))
                .andExpect(jsonPath("$.followUpCompletion.completionRatePercent").value(100.0))
                .andExpect(jsonPath("$.discoveredVsSavedVsApplied.sampleSize").value(2))
                .andExpect(jsonPath("$.discoveredVsSavedVsApplied.discovered").value(2))
                .andExpect(jsonPath("$.discoveredVsSavedVsApplied.applied").value(2))
                .andExpect(jsonPath("$.disclosedSalaryRangesOnly.sampleSize").value(1))
                .andExpect(jsonPath("$.disclosedSalaryRangesOnly.undisclosedExcludedCount").value(1))
                .andExpect(jsonPath("$.disclosedSalaryRangesOnly.listings[0].company").value("DisclosedTech India"))
                .andExpect(jsonPath("$.sourceHealthTable").isArray());
    }
}
