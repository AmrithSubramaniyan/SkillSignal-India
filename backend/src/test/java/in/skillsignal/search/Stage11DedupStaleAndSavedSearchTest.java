package in.skillsignal.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage11DedupStaleAndSavedSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SavedSearchService savedSearchService;

    @Test
    void mergesDuplicateJobsKeepingAllSourceRecordsAndSetsStaleOnlyOnReliableEvidence() throws Exception {
        String token = registerAndGetToken("stage11.dedup@skillsignal.test");

        // 1. Ingest first job from 'manual' source ("Sr. Backend Dev" at "Acme FinTech Pvt Ltd" in "Bangalore")
        Map<String, Object> job1 = Map.of(
                "title", "Sr. Backend Dev",
                "company", "Acme FinTech Pvt Ltd",
                "location", "Bangalore",
                "workMode", "HYBRID",
                "source", "manual",
                "sourceUrl", "https://careers.acmefintech.example/jobs/1",
                "rawDescription", "Looking for a Sr. Backend Dev with Java and Spring Boot in Bangalore.",
                "requiredSkills", List.of("Java", "Spring Boot")
        );

        MvcResult r1 = mockMvc.perform(post("/api/jobs/dedup-ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(job1)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode n1 = objectMapper.readTree(r1.getResponse().getContentAsString());
        String jobId = n1.get("jobId").asText();
        assertThat(n1.get("mergedIntoExisting").asBoolean()).isFalse();
        assertThat(n1.get("totalSourceRecords").asInt()).isEqualTo(1);

        // 2. Ingest fuzzy duplicate from 'company_ats' ("Senior Backend Developer (II)" at "Acme FinTech India" in "Bengaluru")
        var job2 = new JobDedupAndStaleService.IngestJobInput(
                "Senior Backend Developer (II)",
                "Acme FinTech India",
                "Bengaluru",
                "HYBRID",
                "company_ats",
                "gh-acme-777",
                "https://boards.greenhouse.io/acmefintech/jobs/777",
                "Senior Backend Developer in Bengaluru with Java, Spring Boot, PostgreSQL, Docker. 22-28 LPA.",
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of("Docker"),
                4.0,
                22.0,
                28.0,
                "INR",
                "LPA",
                "B.Tech"
        );

        MvcResult r2 = mockMvc.perform(post("/api/jobs/dedup-ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(job2)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode n2 = objectMapper.readTree(r2.getResponse().getContentAsString());
        assertThat(n2.get("jobId").asText()).isEqualTo(jobId);
        assertThat(n2.get("mergedIntoExisting").asBoolean()).isTrue();
        assertThat(n2.get("totalSourceRecords").asInt()).isEqualTo(2);
        assertThat(n2.get("sourceRecords").toString()).contains("manual", "company_ats", "gh-acme-777");

        // 3. Stale flag check: transient HTTP 500 or 429 MUST NOT mark job stale
        MvcResult s500 = mockMvc.perform(post("/api/jobs/" + jobId + "/verify-liveness")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("observedHttpStatus", 500))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(s500.getResponse().getContentAsString()).get("stale").asBoolean()).isFalse();

        // 4. Reliable evidence: HTTP 404 marks job stale
        MvcResult s404 = mockMvc.perform(post("/api/jobs/" + jobId + "/verify-liveness")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("observedHttpStatus", 404))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode n404 = objectMapper.readTree(s404.getResponse().getContentAsString());
        assertThat(n404.get("stale").asBoolean()).isTrue();
        assertThat(n404.get("staleReason").asText()).contains("404");

        // 5. Clear stale when HTTP 200 and explicitClosedFlag=false, then mark stale on explicitClosedFlag=true
        mockMvc.perform(post("/api/jobs/" + jobId + "/verify-liveness")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("observedHttpStatus", 200, "explicitClosedFlag", false))))
                .andExpect(status().isOk());

        MvcResult sClosed = mockMvc.perform(post("/api/jobs/" + jobId + "/verify-liveness")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("explicitClosedFlag", true))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nClosed = objectMapper.readTree(sClosed.getResponse().getContentAsString());
        assertThat(nClosed.get("stale").asBoolean()).isTrue();
        assertThat(nClosed.get("staleReason").asText()).containsIgnoringCase("closed");

        // 6. Saved job toggle + Saved search with scheduled daily refresh
        mockMvc.perform(put("/api/jobs/" + jobId + "/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("saved", true))))
                .andExpect(status().isOk());

        MvcResult savedSearchRes = mockMvc.perform(post("/api/saved-searches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Bengaluru Java Roles",
                                "rawQuery", "Java Spring Boot in Bengaluru",
                                "dailyRefreshEnabled", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode ssNode = objectMapper.readTree(savedSearchRes.getResponse().getContentAsString());
        assertThat(ssNode.get("dailyRefreshEnabled").asBoolean()).isTrue();
        assertThat(ssNode.get("lastMatchCount").asInt()).isGreaterThanOrEqualTo(1);

        int refreshedCount = savedSearchService.runScheduledDailyRefresh();
        assertThat(refreshedCount).isGreaterThanOrEqualTo(1);
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "SecurePass123!",
                                "fullName", "Stage 11 Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}
