package in.skillsignal.reminder;

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
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage15RemindersAndNotificationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reminderBoundariesSnoozeRescheduleCompleteQuietHoursNoDuplicatesAndNoSilenceAsRejection() throws Exception {
        // 1. Register user
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "stage15.reminders@skillsignal.test",
                                "password", "Stage15Pass!123",
                                "fullName", "Rohan Iyer"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Verify default notification preferences (Asia/Kolkata, 22:00 - 08:00)
        mockMvc.perform(get("/api/notification-preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.quietHoursStart").value("22:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("08:00"))
                .andExpect(jsonPath("$.dailySummaryEnabled").value(true));

        // 3. Create job & application in APPLIED status
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Backend Architect",
                                "company", "CloudScale India",
                                "rawDescription", "Java 17, Spring Boot, PostgreSQL, Distributed Systems."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        MvcResult appRes = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "status", "APPLIED",
                                "confirmationType", "USER_CONFIRMED",
                                "recruiterContacts", "Meera Nair (meera@cloudscale.in)"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn();
        String appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).get("id").asText();

        // 4. Reminder boundary tests:
        // Base time: 2026-11-15T18:00:00Z = 2026-11-15T23:30:00+05:30 (Inside Asia/Kolkata quiet hours 22:00-08:00)
        Instant baseTime = ZonedDateTime.of(2026, 11, 15, 23, 30, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        Instant futureTime = baseTime.plusSeconds(3600);

        // Reminder A: due exactly at baseTime (boundary-inclusive -> overdue at baseTime)
        MvcResult remARes = mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "applicationId", appId,
                                "reminderType", "FOLLOW_UP",
                                "title", "Follow up with Meera Nair on Backend Architect role",
                                "dueAt", baseTime.toString(),
                                "notes", "Check interview panel schedule"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String remAId = objectMapper.readTree(remARes.getResponse().getContentAsString()).get("id").asText();

        // Reminder B: due at futureTime (1 hour after baseTime -> NOT overdue at baseTime)
        MvcResult remBRes = mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "applicationId", appId,
                                "reminderType", "INTERVIEW",
                                "title", "System Design Interview Prep",
                                "dueAt", futureTime.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String remBId = objectMapper.readTree(remBRes.getResponse().getContentAsString()).get("id").asText();

        // Check overdue at baseTime - 1 second -> 0 overdue
        mockMvc.perform(get("/api/reminders/overdue")
                        .param("asOf", baseTime.minusSeconds(1).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Check overdue at exact baseTime -> only Reminder A is overdue
        mockMvc.perform(get("/api/reminders/overdue")
                        .param("asOf", baseTime.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(remAId));

        // 5. Snooze Reminder B, Reschedule Reminder B, Complete Reminder B
        mockMvc.perform(put("/api/reminders/" + remBId + "/snooze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "snoozedUntil", futureTime.plusSeconds(7200).toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SNOOZED"));

        mockMvc.perform(put("/api/reminders/" + remBId + "/reschedule")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dueAt", futureTime.plusSeconds(3600).toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(put("/api/reminders/" + remBId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 6. Quiet Hours & Deduplication Test:
        // Dispatch at baseTime (23:30 IST -> within 22:00-08:00 quiet hours)
        // Must create 1 in-app notification marked suppressedByQuietHours=true, and dailySummaryEmailSent=false
        mockMvc.perform(post("/api/notifications/dispatch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evaluationTime", baseTime.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueRemindersCount").value(1))
                .andExpect(jsonPath("$.newInAppNotificationsCreated").value(1))
                .andExpect(jsonPath("$.suppressedByQuietHours").value(true))
                .andExpect(jsonPath("$.dailySummaryEmailSent").value(false));

        // Dispatch a second time at the exact same baseTime -> sent-log prevents duplicate in-app notification!
        mockMvc.perform(post("/api/notifications/dispatch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evaluationTime", baseTime.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newInAppNotificationsCreated").value(0))
                .andExpect(jsonPath("$.dailySummaryEmailSent").value(false));

        // Now dispatch at 10:00 IST next morning (outside quiet hours):
        // In-app notification for remA was already sent (0 new in-app), but Daily Summary Email is now sent once!
        Instant morningIst = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        mockMvc.perform(post("/api/notifications/dispatch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evaluationTime", morningIst.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newInAppNotificationsCreated").value(0))
                .andExpect(jsonPath("$.suppressedByQuietHours").value(false))
                .andExpect(jsonPath("$.dailySummaryEmailSent").value(true));

        // Calling dispatch again on the same morning (10:15 IST) must NOT send a duplicate Daily Summary Email!
        Instant morningIstLater = morningIst.plusSeconds(900);
        mockMvc.perform(post("/api/notifications/dispatch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evaluationTime", morningIstLater.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newInAppNotificationsCreated").value(0))
                .andExpect(jsonPath("$.dailySummaryEmailSent").value(false));

        // 7. AI Follow-up Email Draft & Golden Rule ("Never treat silence as rejection")
        MvcResult draftRes = mockMvc.perform(post("/api/reminders/follow-up-draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "applicationId", appId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("CloudScale India"))
                .andReturn();
        JsonNode draftJson = objectMapper.readTree(draftRes.getResponse().getContentAsString());
        assertThat(draftJson.get("subject").asText()).contains("Backend Architect");
        assertThat(draftJson.get("policyNote").asText()).containsIgnoringCase("never treats recruiter silence as rejection");

        // Verify the application status is still APPLIED (never auto-transitioned to REJECTED despite overdue follow-up)
        mockMvc.perform(get("/api/applications/" + appId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }
}

