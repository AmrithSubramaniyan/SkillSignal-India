package in.skillsignal.company;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage16CompaniesAndIntelligenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void companiesAndHiringIntelligence_computes30_60_90DayEvidenceAndIncludesMandatoryNote() throws Exception {
        // 1. Register user
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "stage16.companies@skillsignal.test",
                                "password", "Stage16Pass!123",
                                "fullName", "Anjali Verma"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Store two jobs for "PayFlow India" (with disclosed salaries) and one job for "HealthBridge Clinics" (without disclosed salary)
        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Senior Java Engineer",
                                "company", "PayFlow India",
                                "location", "Bengaluru",
                                "salaryMin", 22,
                                "salaryMax", 32,
                                "salaryCurrency", "INR",
                                "salaryPeriod", "LPA",
                                "rawDescription", "Java 17, Spring Boot, PostgreSQL."
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Platform Tech Lead",
                                "company", "PayFlow India",
                                "location", "Hyderabad",
                                "salaryMin", 28,
                                "salaryMax", 40,
                                "salaryCurrency", "INR",
                                "salaryPeriod", "LPA",
                                "rawDescription", "Java, Kubernetes, Kafka."
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Clinical Operations Manager",
                                "company", "HealthBridge Clinics",
                                "location", "Mumbai",
                                "rawDescription", "Healthcare operations, patient care, clinical workflows."
                        ))))
                .andExpect(status().isCreated());

        // 3. Promote "PayFlow India" to T1 and priority = true
        MvcResult compRes = mockMvc.perform(post("/api/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "PayFlow India",
                                "tier", "T1",
                                "priority", true,
                                "website", "https://careers.payflow.in",
                                "notes", "Top fintech target"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tier").value("T1"))
                .andExpect(jsonPath("$.priority").value(true))
                .andExpect(jsonPath("$.jobsLast30Days").value(2))
                .andExpect(jsonPath("$.jobsLast60Days").value(2))
                .andExpect(jsonPath("$.jobsLast90Days").value(2))
                .andReturn();
        JsonNode payflowNode = objectMapper.readTree(compRes.getResponse().getContentAsString());
        assertThat(payflowNode.get("disclosedSalaryRange").asText()).contains("22 - 40 INR");
        assertThat(payflowNode.get("evidenceDisclaimer").asText()).containsIgnoringCase("listings are not hires");

        // 4. Query Hiring Intelligence endpoint -> both companies appear, disclaimer contains "listings are not hires"
        MvcResult intelRes = mockMvc.perform(get("/api/companies/hiring-intelligence")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrackedCompanies").value(2))
                .andExpect(jsonPath("$.priorityCompaniesCount").value(1))
                .andExpect(jsonPath("$.totalStoredJobsAcrossCompanies").value(3))
                .andReturn();
        JsonNode intelJson = objectMapper.readTree(intelRes.getResponse().getContentAsString());
        assertThat(intelJson.get("disclaimer").asText()).containsIgnoringCase("listings are not hires");

        // 5. Filter by priorityOnly=true -> returns only PayFlow India
        mockMvc.perform(get("/api/companies")
                        .param("priorityOnly", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("PayFlow India"));
    }
}

