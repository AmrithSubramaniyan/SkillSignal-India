package in.skillsignal.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.agent.ApplyTask;
import in.skillsignal.agent.ApplyTaskRepository;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.AutoApplyPreferenceRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage26SafetyGovernorFullTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplyTaskRepository applyTaskRepository;

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private AutoApplyPreferenceRepository preferenceRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void threeConsecutiveFailuresCaptchaUnusualActivityRejectionSpikeTermsChangeRateLimiterAndKillSwitch() throws Exception {
        String email = "stage26." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage26Pass!123",
                                "fullName", "Rohan Verma"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Backend Engineer",
                                "company", "FinScale India",
                                "rawDescription", "Java Spring Boot"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID jobId = UUID.fromString(objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText());

        // Seed 2 queued tasks for mock_portal
        ApplyTask t1 = new ApplyTask();
        t1.setUserId(userId);
        t1.setJobId(jobId);
        t1.setPortal("mock_portal");
        t1.setStatus("QUEUED");
        t1 = applyTaskRepository.save(t1);

        ApplyTask t2 = new ApplyTask();
        t2.setUserId(userId);
        t2.setJobId(jobId);
        t2.setPortal("mock_portal");
        t2.setStatus("IN_PROGRESS");
        t2 = applyTaskRepository.save(t2);

        // 1. Report 2 consecutive failures -> not yet paused, circuit still CLOSED
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/safety/report-signal")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "source", "mock_portal",
                                    "signalType", "FAILURE",
                                    "details", "Network timeout #" + i
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consecutiveFailures").value(i))
                    .andExpect(jsonPath("$.circuitBreakerState").value("CLOSED"))
                    .andExpect(jsonPath("$.autoPaused").value(false));
        }

        // 2. 3rd consecutive failure -> opens circuit breaker, auto-pauses user, halts queued tasks
        mockMvc.perform(post("/api/safety/report-signal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "source", "mock_portal",
                                "signalType", "FAILURE",
                                "details", "Network timeout #3"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consecutiveFailures").value(3))
                .andExpect(jsonPath("$.circuitBreakerState").value("OPEN"))
                .andExpect(jsonPath("$.globalState").value("PAUSED"))
                .andExpect(jsonPath("$.autoPaused").value(true))
                .andExpect(jsonPath("$.haltedTasksCount").value(2));

        assertThat(applyTaskRepository.findById(t1.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(applyTaskRepository.findById(t2.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");

        // 3. Reset circuit breaker and re-activate user
        mockMvc.perform(post("/api/safety/circuit-breakers/mock_portal/reset")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.consecutiveFailures").value(0));

        AutoApplyPreference pref = preferenceRepository.findById(userId).orElseThrow();
        pref.setGlobalState("ACTIVE");
        preferenceRepository.save(pref);

        // 4. Per-source rate limiter: allow 2 requests in window when maxPerWindow=2, block 3rd
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/safety/check-rate-limit")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "source", "company_ats",
                                    "maxPerWindow", 2
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true))
                    .andExpect(jsonPath("$.windowRequestCount").value(i));
        }

        mockMvc.perform(post("/api/safety/check-rate-limit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "source", "company_ats",
                                "maxPerWindow", 2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("RATE_LIMIT_EXCEEDED"));

        // 5. Immediate auto-pause on CAPTCHA, UNUSUAL_ACTIVITY, REJECTION_SPIKE, and TERMS_CHANGE
        for (String trigger : List.of("CAPTCHA", "UNUSUAL_ACTIVITY", "REJECTION_SPIKE", "TERMS_CHANGE")) {
            pref.setGlobalState("ACTIVE");
            preferenceRepository.save(pref);
            mockMvc.perform(post("/api/safety/circuit-breakers/mock_portal/reset")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/safety/report-signal")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "source", "mock_portal",
                                    "signalType", trigger,
                                    "details", "Detected " + trigger
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.circuitBreakerState").value("OPEN"))
                    .andExpect(jsonPath("$.globalState").value("PAUSED"))
                    .andExpect(jsonPath("$.autoPaused").value(true));
        }

        // 6. User Kill Switch (Big STOP Button) halts in-flight work immediately and sets STOPPED
        pref.setGlobalState("ACTIVE");
        preferenceRepository.save(pref);

        ApplyTask t3 = new ApplyTask();
        t3.setUserId(userId);
        t3.setJobId(jobId);
        t3.setPortal("mock_portal");
        t3.setStatus("IN_PROGRESS");
        t3 = applyTaskRepository.save(t3);

        mockMvc.perform(post("/api/safety/kill-switch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "User clicked Big STOP button"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalState").value("STOPPED"))
                .andExpect(jsonPath("$.haltedTasksCount").value(1));

        assertThat(applyTaskRepository.findById(t3.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");

        // 7. Admin Kill Switch halts work across platform
        ApplyTask t4 = new ApplyTask();
        t4.setUserId(userId);
        t4.setJobId(jobId);
        t4.setPortal("company_ats");
        t4.setStatus("QUEUED");
        t4 = applyTaskRepository.save(t4);

        mockMvc.perform(post("/api/safety/admin/kill-switch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "Admin platform maintenance kill switch"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalState").value("STOPPED"))
                .andExpect(jsonPath("$.haltedTasksCount").value(1));

        assertThat(applyTaskRepository.findById(t4.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");

        // 8. Verify safety_events table contains recorded events
        List<SafetyEvent> events = safetyEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(events).extracting(SafetyEvent::getEventType)
                .contains("CONSECUTIVE_FAILURES", "RATE_LIMIT_EXCEEDED", "CAPTCHA", "UNUSUAL_ACTIVITY",
                        "REJECTION_SPIKE", "TERMS_CHANGE", "KILL_SWITCH", "ADMIN_KILL_SWITCH");

        mockMvc.perform(get("/api/safety/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalState").value("STOPPED"))
                .andExpect(jsonPath("$.circuitBreakers").isArray())
                .andExpect(jsonPath("$.recentSafetyEvents").isArray());
    }
}
