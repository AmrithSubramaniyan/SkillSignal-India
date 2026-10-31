package in.skillsignal.submission;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage22Modes1And2ApprovalQueueTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void modes1And2_approvalQueueUserSubmitsPathBatchApproveWithExceptionsSingleUseEmailLinkAndAssistedConfirm() throws Exception {
        // 1. Register user
        String email = "stage22." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage22Pass!123",
                                "fullName", "Stage 22 Candidate"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Create 4 jobs for testing Queue actions, Batch Approve with Exceptions, and Single-Use Email Link
        String job1 = createJob(token, "Backend Engineer I", "AcmePay");
        String job2 = createJob(token, "Backend Engineer II", "BetaCloud");
        String job3 = createJob(token, "Backend Engineer III", "GammaTech");
        String job4 = createJob(token, "Backend Engineer IV", "DeltaData");

        String sub1 = prepareSubmission(token, job1, "S22-SUB-1");
        String sub2 = prepareSubmission(token, job2, "S22-SUB-2");
        String sub3 = prepareSubmission(token, job3, "S22-SUB-3");
        String sub4 = prepareSubmission(token, job4, "S22-SUB-4");

        // 3. GET /api/approval-queue lists all 4 items
        MvcResult queueRes = mockMvc.perform(get("/api/approval-queue")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queueJson = objectMapper.readTree(queueRes.getResponse().getContentAsString());
        assertThat(queueJson.size()).isEqualTo(4);

        // 4. PUT /api/approval-queue/{id}/answers updates answers & transitions to EDITED
        mockMvc.perform(put("/api/approval-queue/" + sub1 + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answers", Map.of("What is your current city?", "Pune"),
                                "saveToAnswerLibrary", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EDITED"));

        // 5. POST /api/approval-queue/{id}/approve -> leads ONLY to user-submits path (tracker READY_TO_APPLY, never auto-submitted)
        MvcResult appRes1 = mockMvc.perform(post("/api/approval-queue/" + sub1 + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "Approve sub1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.applicationId").isNotEmpty())
                .andReturn();
        String trackerAppId1 = objectMapper.readTree(appRes1.getResponse().getContentAsString()).get("applicationId").asText();

        mockMvc.perform(get("/api/applications/" + trackerAppId1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_APPLY"))
                .andExpect(jsonPath("$.applyMode").value("ASSISTED"))
                .andExpect(jsonPath("$.confirmationType").value("UNCONFIRMED"));

        // 6. Mode 1 Assisted Apply: User clicks "I applied" -> sets SUBMITTED_CONFIRMED and tracker APPLIED (USER_CONFIRMED)
        mockMvc.perform(post("/api/approval-queue/" + sub1 + "/confirm-assisted-applied")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "I applied on AcmePay careers page"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED_CONFIRMED"));

        mockMvc.perform(get("/api/applications/" + trackerAppId1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.confirmationType").value("USER_CONFIRMED"));

        // 7. Batch approve sub2 and sub3 with sub3 in exceptionIds -> only sub2 is approved, sub3 stays AWAITING_APPROVAL
        mockMvc.perform(post("/api/approval-queue/batch-approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "submissionIds", List.of(sub2, sub3),
                                "exceptionIds", List.of(sub3),
                                "note", "Batch approve except sub3"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedCount").value(1))
                .andExpect(jsonPath("$.skippedExceptionCount").value(1));

        mockMvc.perform(get("/api/submissions/" + sub2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));

        mockMvc.perform(get("/api/submissions/" + sub3)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_APPROVAL"));

        // 8. Reject sub3
        mockMvc.perform(post("/api/approval-queue/" + sub3 + "/reject")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"));

        // 9. Single-use expiring email approval link on sub4 (login required -> 401 without token, 200 on first use, 409 on second use)
        MvcResult linkRes = mockMvc.perform(post("/api/approval-queue/" + sub4 + "/send-approval-link")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalToken").isNotEmpty())
                .andReturn();
        String singleUseToken = objectMapper.readTree(linkRes.getResponse().getContentAsString()).get("approvalToken").asText();

        // Unauthenticated redemption fails (login required)
        mockMvc.perform(post("/api/approval-queue/approve-by-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", singleUseToken))))
                .andExpect(status().is4xxClientError());

        // Authenticated first redemption succeeds
        mockMvc.perform(post("/api/approval-queue/approve-by-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", singleUseToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));

        // Second redemption of the same single-use token is rejected with 409 Conflict
        mockMvc.perform(post("/api/approval-queue/approve-by-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", singleUseToken))))
                .andExpect(status().isConflict());
    }

    private String createJob(String token, String title, String company) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "company", company,
                                "location", "Bengaluru",
                                "workMode", "HYBRID",
                                "rawDescription", "Java, Spring Boot, PostgreSQL, Docker role at " + company
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String prepareSubmission(String token, String jobId, String idempKey) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/submissions/prepare")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "idempotencyKey", idempKey
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }
}
