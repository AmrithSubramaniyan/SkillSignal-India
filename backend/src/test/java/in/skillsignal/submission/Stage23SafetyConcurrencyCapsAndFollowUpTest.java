package in.skillsignal.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.eligibility.AutoApplyPreference;
import in.skillsignal.eligibility.StrictEligibilityService;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.reminder.Reminder;
import in.skillsignal.reminder.ReminderRepository;
import in.skillsignal.resume.Resume;
import in.skillsignal.resume.ResumeRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage23SafetyConcurrencyCapsAndFollowUpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private StrictEligibilityService eligibilityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeVersionRepository resumeVersionRepository;

    @Test
    void concurrentApprovalsProduceSingleApplication_expiryAndFreshnessRecheck_needsVerifBlocksRetry_capsAndTimezoneRollover_legalPauses_andAutoFollowUpCreated() throws Exception {
        // 1. Register user
        String email = "stage23." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage23Pass!123",
                                "fullName", "Aarav Mehta"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        // Seed verified default resume
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setTitle("Aarav Verified Resume");
        resume = resumeRepository.save(resume);

        ResumeVersion rv = new ResumeVersion();
        rv.setResumeId(resume.getId());
        rv.setUserId(userId);
        rv.setVersionNumber(1);
        rv.setOriginalFilename("aarav.pdf");
        rv.setContentType("application/pdf");
        rv.setFileSizeBytes(2048);
        rv.setStoragePath("./uploads/test/aarav.pdf");
        rv.setExtractedText("Aarav Mehta. 4 years experience in Java, Spring Boot, PostgreSQL, Docker.");
        rv.setParsedJson("{\"skills\":[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Docker\"],\"yearsOfExperience\":4.0}");
        rv.setVerifiedSkillsJson("[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Docker\"]");
        rv.setYearsOfExperience(4.0);
        rv.setDefault(true);
        resumeVersionRepository.save(rv);

        // =====================================================================
        // TEST 1: Concurrent approvals produce EXACTLY ONE tracker Application
        // =====================================================================
        UUID jobConcurrentId = createJob(userId, "Staff Backend Engineer", "ConcurrentCorp", 3.0, 6.0);
        SubmissionService.SubmissionDto subConcurrent = submissionService.prepareSubmission(
                userId, jobConcurrentId, "CONCURRENT-KEY-1", List.of(), null
        );

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<SubmissionService.SubmissionDto>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> submissionService.approveQueueItem(userId, subConcurrent.id(), "Concurrent approve"));
        }
        List<Future<SubmissionService.SubmissionDto>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        UUID expectedAppId = null;
        for (Future<SubmissionService.SubmissionDto> f : futures) {
            SubmissionService.SubmissionDto res = f.get();
            assertThat(res.state()).isEqualTo("APPROVED");
            assertThat(res.applicationId()).isNotNull();
            if (expectedAppId == null) {
                expectedAppId = res.applicationId();
            } else {
                assertThat(res.applicationId()).isEqualTo(expectedAppId);
            }
        }
        List<Application> appsAfterConcurrent = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        assertThat(appsAfterConcurrent).hasSize(1);

        // =====================================================================
        // TEST 2: Expiry and Freshness Re-Check (stale job or past expiresAt -> EXPIRED, blocks approve)
        // =====================================================================
        UUID jobExpiredId = createJob(userId, "Expired Role", "PastTimeCorp", 2.0, 5.0);
        SubmissionService.SubmissionDto subExpired = submissionService.prepareSubmission(
                userId, jobExpiredId, "EXPIRED-KEY-1", List.of(), Instant.now().minusSeconds(120)
        );

        mockMvc.perform(post("/api/approval-queue/" + subExpired.id() + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isGone());

        mockMvc.perform(get("/api/submissions/" + subExpired.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EXPIRED"));

        UUID jobStaleId = createJob(userId, "Closed Role", "StaleBoardCorp", 2.0, 5.0);
        SubmissionService.SubmissionDto subStale = submissionService.prepareSubmission(
                userId, jobStaleId, "STALE-KEY-1", List.of(), null
        );
        Job staleJob = jobRepository.findById(jobStaleId).orElseThrow();
        staleJob.setStale(true);
        jobRepository.save(staleJob);

        // Freshness re-check on approve transitions to EXPIRED and rejects with 410 Gone
        mockMvc.perform(post("/api/approval-queue/" + subStale.id() + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isGone());

        mockMvc.perform(get("/api/submissions/" + subStale.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EXPIRED"));

        // =====================================================================
        // TEST 3: Needs Verification blocks retry in both /approve and /transition
        // =====================================================================
        UUID jobNeedsVerifId = createJob(userId, "Uncertain Portal Role", "VerifyCorp", 2.0, 6.0);
        SubmissionService.SubmissionDto subNv = submissionService.prepareSubmission(
                userId, jobNeedsVerifId, "NV-KEY-1", List.of(), null
        );
        submissionService.transitionSubmission(userId, subNv.id(), "APPROVED", "Approved", "USER", false);
        submissionService.transitionSubmission(userId, subNv.id(), "SUBMITTING", "Submitting", "SYSTEM", false);
        submissionService.transitionSubmission(userId, subNv.id(), "NEEDS_VERIFICATION", "Timeout after submit click", "SYSTEM", false);

        mockMvc.perform(post("/api/approval-queue/" + subNv.id() + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/submissions/" + subNv.id() + "/transition")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetState", "SUBMITTING"))))
                .andExpect(status().isUnprocessableEntity());

        // =====================================================================
        // TEST 4: Daily (5/day) & Weekly (20/week) Caps at Boundaries + Asia/Kolkata Rollover
        // =====================================================================
        AutoApplyPreference pref = eligibilityService.getOrCreatePreferences(userId);
        // Boundary: 4 today (< 5), 19 this week (< 20) -> PASS
        var underCapResult = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                4.0, true,
                List.of("Java", "Spring Boot", "PostgreSQL"),
                2.0, 6.0, "Backend Engineer", "CapTestCorp", "manual", "India", "Java role",
                true, true, true, true,
                4, 19, 90, true, pref, StrictEligibilityService.ExclusionListsDto.empty(), List.of("manual")
        ));
        assertThat(underCapResult.eligible()).isTrue();

        // Exact daily cap boundary: 5 today (5/5) -> FAIL (routes to Review Queue)
        var atDailyCapResult = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                4.0, true,
                List.of("Java", "Spring Boot", "PostgreSQL"),
                2.0, 6.0, "Backend Engineer", "CapTestCorp", "manual", "India", "Java role",
                true, true, true, true,
                5, 19, 90, true, pref, StrictEligibilityService.ExclusionListsDto.empty(), List.of("manual")
        ));
        assertThat(atDailyCapResult.eligible()).isFalse();
        assertThat(atDailyCapResult.routesToReviewQueue()).isTrue();
        assertThat(atDailyCapResult.whyAppliedOrSkipped()).contains("Daily cap reached (5/5)");

        // Exact weekly cap boundary: 4 today, 20 this week (20/20) -> FAIL (routes to Review Queue)
        var atWeeklyCapResult = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                4.0, true,
                List.of("Java", "Spring Boot", "PostgreSQL"),
                2.0, 6.0, "Backend Engineer", "CapTestCorp", "manual", "India", "Java role",
                true, true, true, true,
                4, 20, 90, true, pref, StrictEligibilityService.ExclusionListsDto.empty(), List.of("manual")
        ));
        assertThat(atWeeklyCapResult.eligible()).isFalse();
        assertThat(atWeeklyCapResult.whyAppliedOrSkipped()).contains("Weekly cap reached (20/20)");

        // Per-user time zone rollover in Asia/Kolkata:
        // Evaluating at 00:05 IST tomorrow means applications created yesterday in IST do not count toward today's daily cap
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        Instant tomorrowIstMorning = ZonedDateTime.now(ist).plusDays(1).withHour(0).withMinute(5).toInstant();
        UUID jobTzId = createJob(userId, "IST Rollover Engineer", "MumbaiTech", 2.0, 6.0);
        // Save approved answers for default pack questions so Check #5 passes
        mockMvc.perform(post("/api/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionText", "What is your current city?",
                                "category", "FACTUAL",
                                "approvedAnswer", "Bengaluru"
                        ))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionText", "What is your preferred work mode (remote or hybrid)?",
                                "category", "PREFERENCE",
                                "approvedAnswer", "Hybrid"
                        ))))
                .andExpect(status().isCreated());

        // Set dailyCap = 1; even though 1 application exists today, tomorrow at 00:05 IST todayCount resets to 0!
        eligibilityService.updatePreferences(userId, 3, true, "BLOCK", "REVIEW", 70, 1, 20, 30, List.of("manual"), null);
        var rolloverRes = eligibilityService.evaluateForUserAndJob(userId, jobTzId, List.of("What is your current city?"), tomorrowIstMorning);
        assertThat(rolloverRes.eligible()).isTrue();

        // =====================================================================
        // TEST 5: Legal / Visa / Salary-History Question ALWAYS Pauses and Routes to Review
        // =====================================================================
        var legalRes = eligibilityService.evaluateForUserAndJob(
                userId,
                jobTzId,
                List.of("Do you require visa sponsorship or have any non-compete legal disclosure?"),
                tomorrowIstMorning
        );
        assertThat(legalRes.eligible()).isFalse();
        assertThat(legalRes.routesToReviewQueue()).isTrue();
        assertThat(legalRes.whyAppliedOrSkipped()).contains("Check #5");

        // =====================================================================
        // TEST 6: Global STOP requires typed confirmation & Confirming Applied Auto-Creates Follow-Up Reminder + Draft
        // =====================================================================
        mockMvc.perform(post("/api/auto-apply/preferences/global-state")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("state", "STOP", "confirmationText", "wrong"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auto-apply/preferences/global-state")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("state", "STOP", "confirmationText", "STOP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalState").value("STOPPED"));

        // Confirming "I applied" on subConcurrent auto-creates a FOLLOW_UP reminder and follow-up email draft!
        mockMvc.perform(post("/api/approval-queue/" + subConcurrent.id() + "/confirm-assisted-applied")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "Confirmed applied on ConcurrentCorp"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTED_CONFIRMED"));

        List<Reminder> userReminders = reminderRepository.findByUserIdOrderByDueAtAsc(userId);
        assertThat(userReminders).isNotEmpty();
        Reminder followUpRem = userReminders.get(0);
        assertThat(followUpRem.getReminderType()).isEqualTo("FOLLOW_UP");
        assertThat(followUpRem.getTitle()).contains("ConcurrentCorp");
        assertThat(followUpRem.getNotes()).contains("Auto-generated follow-up draft:");
        assertThat(followUpRem.getNotes()).contains("Subject: Following up on my application");
    }

    private UUID createJob(UUID userId, String title, String company, Double minExp, Double maxExp) {
        Job j = new Job();
        j.setUserId(userId);
        j.setTitle(title);
        j.setCompany(company);
        j.setLocation("Bengaluru");
        j.setWorkMode("HYBRID");
        j.setSource("manual");
        j.setCountry("India");
        j.setMinYearsExperience(minExp);
        j.setMaxYearsExperience(maxExp);
        j.setRawDescription("Looking for Java, Spring Boot, PostgreSQL, Docker.");
        j.setRequiredSkillsJson("[\"Java\",\"Spring Boot\",\"PostgreSQL\"]");
        j.setNiceToHaveSkillsJson("[\"Docker\"]");
        j.setParsedJson("{}");
        return jobRepository.save(j).getId();
    }
}

