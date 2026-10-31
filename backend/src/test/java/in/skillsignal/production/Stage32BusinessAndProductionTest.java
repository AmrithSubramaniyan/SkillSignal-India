package in.skillsignal.production;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage32BusinessAndProductionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void featureFlagsNoPaymentsFeedbackUsageEventsKnownIssuesGateG7AndSecurityHeaders() throws Exception {
        String email = "stage32." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage32Pass!123",
                                "fullName", "Siddharth Joshi"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 1. Health check endpoint
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 2. Feature-flag table for free vs paid (paymentsEnabled = false)
        mockMvc.perform(get("/api/production/feature-flags")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentsEnabled").value(false))
                .andExpect(jsonPath("$.flags").isArray())
                .andExpect(jsonPath("$.flags.length()").value(5));

        // 3. Submit & list product feedback
        mockMvc.perform(post("/api/production/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "MATCH_QUALITY",
                                "rating", 5,
                                "message", "Explainable match breakdown and truthfulness check are super helpful!",
                                "pageContext", "/jobs"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("MATCH_QUALITY"))
                .andExpect(jsonPath("$.rating").value(5));

        mockMvc.perform(get("/api/production/feedback")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 4. Record & list usage events
        mockMvc.perform(post("/api/production/usage-events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventName", "ANALYTICS_VIEWED",
                                "metadata", Map.of("screen", "AnalyticsPage")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventName").value("ANALYTICS_VIEWED"));

        mockMvc.perform(get("/api/production/usage-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 5. Known issues list & GATE G7 reminder
        mockMvc.perform(get("/api/production/known-issues")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1.0.0-lite"))
                .andExpect(jsonPath("$.gateG7Reminder").isNotEmpty())
                .andExpect(jsonPath("$.knownIssues.length()").value(3));

        // 6. Verify Privacy Export & Account Deletion include Sprint 5 & 6 tables cleanly
        mockMvc.perform(get("/api/privacy/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userFeedback").isArray())
                .andExpect(jsonPath("$.usageEvents").isArray())
                .andExpect(jsonPath("$.skillRoadmapItems").isArray())
                .andExpect(jsonPath("$.interviewPrepSessions").isArray());

        mockMvc.perform(delete("/api/privacy/account")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}

