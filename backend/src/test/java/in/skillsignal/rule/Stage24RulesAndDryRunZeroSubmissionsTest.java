package in.skillsignal.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.company.Company;
import in.skillsignal.company.CompanyRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.resume.Resume;
import in.skillsignal.resume.ResumeRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.submission.ApplicationSubmissionRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage24RulesAndDryRunZeroSubmissionsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeVersionRepository resumeVersionRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationSubmissionRepository submissionRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    void rulesLimitsTierT1ReviewCompanyCooldownSpreadAndDryRunCreatesZeroSubmissions() throws Exception {
        // 1. Register user
        String email = "stage24." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage24Pass!123",
                                "fullName", "Stage 24 Candidate"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        // Seed verified default resume (4 years Java, Spring Boot, PostgreSQL, Docker)
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setTitle("Stage 24 Resume");
        resume = resumeRepository.save(resume);

        ResumeVersion rv = new ResumeVersion();
        rv.setResumeId(resume.getId());
        rv.setUserId(userId);
        rv.setVersionNumber(1);
        rv.setOriginalFilename("s24.pdf");
        rv.setContentType("application/pdf");
        rv.setFileSizeBytes(2048);
        rv.setStoragePath("./uploads/test/s24.pdf");
        rv.setExtractedText("4 years experience in Java, Spring Boot, PostgreSQL, Docker.");
        rv.setParsedJson("{\"skills\":[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Docker\"],\"yearsOfExperience\":4.0}");
        rv.setVerifiedSkillsJson("[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Docker\"]");
        rv.setYearsOfExperience(4.0);
        rv.setDefault(true);
        resumeVersionRepository.save(rv);

        // 2. Enforce max 3 conditions per rule -> 4 conditions rejected with 400
        mockMvc.perform(post("/api/auto-apply/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ruleName", "Too Many Conditions Rule",
                                "conditions", List.of(
                                        Map.of("field", "WORK_MODE", "operator", "EQUALS", "value", "HYBRID"),
                                        Map.of("field", "LOCATION", "operator", "CONTAINS", "value", "Bengaluru"),
                                        Map.of("field", "TITLE_KEYWORD", "operator", "CONTAINS", "value", "Backend"),
                                        Map.of("field", "SOURCE", "operator", "EQUALS", "value", "manual")
                                )
                        ))))
                .andExpect(status().isBadRequest());

        // 3. Enforce T1 policy = REVIEW -> setting tierT1Policy = ELIGIBLE rejected with 400
        mockMvc.perform(post("/api/auto-apply/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ruleName", "Illegal T1 Policy Rule",
                                "tierT1Policy", "ELIGIBLE"
                        ))))
                .andExpect(status().isBadRequest());

        // 4. Create 5 valid rules -> 6th rule rejected with 400 (max 5 rules per user)
        String rule1Id = null;
        for (int i = 1; i <= 5; i++) {
            MvcResult rRes = mockMvc.perform(post("/api/auto-apply/rules")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "ruleName", "Rule #" + i,
                                    "priorityOrder", i,
                                    "enabled", i == 1, // Keep Rule #1 active for dry-run test
                                    "conditions", List.of(
                                            Map.of("field", "WORK_MODE", "operator", "EQUALS", "value", "HYBRID"),
                                            Map.of("field", "LOCATION", "operator", "CONTAINS", "value", "Bengaluru")
                                    ),
                                    "tierT1Policy", "REVIEW",
                                    "tierT2Policy", "ELIGIBLE",
                                    "timeWindowStart", "09:00",
                                    "timeWindowEnd", "19:00",
                                    "companyCooldownDays", 30,
                                    "minSpreadMinutes", 15
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            if (i == 1) {
                rule1Id = objectMapper.readTree(rRes.getResponse().getContentAsString()).get("id").asText();
            }
        }

        // 6th rule fails with 400
        mockMvc.perform(post("/api/auto-apply/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ruleName", "Rule #6"))))
                .andExpect(status().isBadRequest());

        // Delete Rule #5 and update Rule #1
        MvcResult listRulesRes = mockMvc.perform(get("/api/auto-apply/rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String rule5Id = objectMapper.readTree(listRulesRes.getResponse().getContentAsString()).get(4).get("id").asText();

        mockMvc.perform(delete("/api/auto-apply/rules/" + rule5Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/auto-apply/rules/" + rule1Id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ruleName", "Primary Bengaluru Hybrid Rule",
                                "minSpreadMinutes", 20
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleName").value("Primary Bengaluru Hybrid Rule"));

        // 5. Seed 3 jobs to test dry run ("Would have matched 2 of 3 jobs"):
        // - Job A: T1 company ("TierOneFinTech"), HYBRID, Bengaluru, 3-6 yrs -> matches rule, routes to ROUTE_TO_REVIEW_T1
        // - Job B: T2 company ("TierTwoSaaS"), HYBRID, Bengaluru, 3-6 yrs -> matches rule, WOULD_MATCH_ELIGIBLE (spread +20m)
        // - Job C: ONSITE in Mumbai -> does not match rule conditions -> SKIPPED_NOT_MATCHED
        Company t1Comp = new Company();
        t1Comp.setUserId(userId);
        t1Comp.setName("TierOneFinTech");
        t1Comp.setTier("T1");
        t1Comp.setPriority(true);
        companyRepository.save(t1Comp);

        createJob(userId, "Senior Backend Engineer", "TierOneFinTech", "Bengaluru", "HYBRID", 3.0, 6.0);
        createJob(userId, "Platform Engineer", "TierTwoSaaS", "Bengaluru", "HYBRID", 3.0, 6.0);
        createJob(userId, "Onsite Legacy Developer", "MumbaiCorp", "Mumbai", "ONSITE", 3.0, 6.0);

        Instant evalTimeInWindow = ZonedDateTime.of(2026, 11, 2, 11, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        // 6. Simulate Rule #1
        mockMvc.perform(post("/api/auto-apply/rules/" + rule1Id + "/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("evaluationTime", evalTimeInWindow.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.summaryText").value("Would have matched 2 of 3 jobs"))
                .andExpect(jsonPath("$.submissionsCreatedCount").value(0));

        // 7. Execute Dry Run (POST /api/auto-apply/runs?dry_run=true) and GET /api/auto-apply/runs/{id}
        MvcResult runRes = mockMvc.perform(post("/api/auto-apply/runs?dry_run=true")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("evaluationTime", evalTimeInWindow.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.totalJobsEvaluated").value(3))
                .andExpect(jsonPath("$.matchedJobsCount").value(2))
                .andExpect(jsonPath("$.submissionsCreatedCount").value(0))
                .andExpect(jsonPath("$.summaryText").value("Would have matched 2 of 3 jobs"))
                .andReturn();
        String runId = objectMapper.readTree(runRes.getResponse().getContentAsString()).get("id").asText();

        MvcResult getRunRes = mockMvc.perform(get("/api/auto-apply/runs/" + runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryText").value("Would have matched 2 of 3 jobs"))
                .andReturn();
        JsonNode itemsNode = objectMapper.readTree(getRunRes.getResponse().getContentAsString()).get("items");
        assertThat(itemsNode.size()).isEqualTo(3);

        // ASSERT ZERO SUBMISSIONS AND ZERO TRACKER APPLICATIONS WERE CREATED BY DRY RUN!
        assertThat(submissionRepository.findByUserIdOrderByUpdatedAtDesc(userId)).isEmpty();
        assertThat(applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId)).isEmpty();
    }

    private void createJob(UUID userId, String title, String company, String location, String workMode, Double minExp, Double maxExp) {
        Job j = new Job();
        j.setUserId(userId);
        j.setTitle(title);
        j.setCompany(company);
        j.setLocation(location);
        j.setWorkMode(workMode);
        j.setSource("manual");
        j.setCountry("India");
        j.setMinYearsExperience(minExp);
        j.setMaxYearsExperience(maxExp);
        j.setRawDescription("Java, Spring Boot, PostgreSQL, Docker role at " + company);
        j.setRequiredSkillsJson("[\"Java\",\"Spring Boot\",\"PostgreSQL\"]");
        j.setNiceToHaveSkillsJson("[\"Docker\"]");
        j.setParsedJson("{}");
        jobRepository.save(j);
    }
}

