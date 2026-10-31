package in.skillsignal.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
import in.skillsignal.job.JobRepository;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.seed.DemoDataSeeder;
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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationTrackerAndSeederTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeVersionRepository resumeVersionRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    void createUpdateApplicationDuplicate409EventsAndDashboard() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "tracker.user@skillsignal.test",
                                "password", "TrackerPass123!",
                                "fullName", "Tracker User"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // Create a job
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Data Analyst",
                                "company", "Analytics Corp",
                                "rawDescription", "Looking for a Data Analyst skilled in SQL, Python, and Power BI with 2 years experience."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        // 1. Create application (defaults to SAVED, never marked submitted automatically)
        MvcResult appRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "notes", "Preparing tailored resume",
                                "followUpDate", LocalDate.now().toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.applyMode").value("ASSISTED"))
                .andReturn();
        JsonNode appNode = objectMapper.readTree(appRes.getResponse().getContentAsString());
        String appId = appNode.get("id").asText();

        // 2. Duplicate application for same (user_id, job_id) returns 409 Conflict
        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobId", jobId))))
                .andExpect(status().isConflict());

        // 3. Update application status -> records status history event
        mockMvc.perform(put("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "NEEDS_VERIFICATION",
                                "notes", "Portal page closed before confirmation screen"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_VERIFICATION"));

        mockMvc.perform(get("/api/applications/" + appId + "/events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromStatus").value("SAVED"))
                .andExpect(jsonPath("$[1].toStatus").value("NEEDS_VERIFICATION"));

        // 4. Dashboard counts and due follow-ups
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(1))
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.followUpsDueCount").value(1))
                .andExpect(jsonPath("$.statusCounts.NEEDS_VERIFICATION").value(1));
    }

    @Test
    void demoDataSeederCreatesDemoUser1Resume4JobsAnd3Applications() throws Exception {
        demoDataSeeder.seedDemoDataIfAbsent();

        User demo = userRepository.findByEmailIgnoreCase(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        assertThat(resumeVersionRepository.countByUserId(demo.getId())).isEqualTo(1);
        assertThat(jobRepository.findByUserIdOrderByCreatedAtDesc(demo.getId())).hasSize(4);
        assertThat(applicationRepository.findByUserIdOrderByUpdatedAtDesc(demo.getId())).hasSize(3);
    }
}

