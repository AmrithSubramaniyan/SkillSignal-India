package in.skillsignal.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage19ApplicationPackAndReadinessTest {

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

    @Test
    void applicationPack_generatesThreeTonesJobSnapshotHashTruthfulnessAndReadinessRouting() throws Exception {
        // 1. Register user
        String email = "stage19.pack@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage19Pass!123",
                                "fullName", "Neha Kulkarni"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // 2. Seed a default verified ResumeVersion
        Resume resume = new Resume();
        resume.setUserId(user.getId());
        resume.setTitle("Neha Backend Resume");
        resume = resumeRepository.save(resume);

        ResumeVersion rv = new ResumeVersion();
        rv.setResumeId(resume.getId());
        rv.setUserId(user.getId());
        rv.setVersionNumber(1);
        rv.setOriginalFilename("neha_resume.pdf");
        rv.setContentType("application/pdf");
        rv.setFileSizeBytes(2048);
        rv.setStoragePath("./uploads/test/neha.pdf");
        rv.setExtractedText("Neha Kulkarni. B.Tech Computer Science. 4 years of experience in Java, Spring Boot, PostgreSQL, and Docker in Bengaluru.");
        rv.setParsedJson("{\"skills\":[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Docker\"],\"yearsOfExperience\":4.0}");
        rv.setVerifiedSkillsJson("[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Docker\"]");
        rv.setYearsOfExperience(4.0);
        rv.setDefault(true);
        resumeVersionRepository.save(rv);

        // 3. Create a matching Job
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Senior Backend Developer",
                                "company", "RazorFlow India",
                                "location", "Bengaluru",
                                "workMode", "HYBRID",
                                "rawDescription", "Looking for a Senior Backend Developer with 3+ years experience in Java, Spring Boot, PostgreSQL, and Docker."
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        // 4. First call GET /api/applications/packs/{jobId} BEFORE saving answers to Answer Library
        // Questions are unanswered -> routesToReviewQueue must be true
        MvcResult initialPackRes = mockMvc.perform(get("/api/applications/packs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobSnapshotHash").isNotEmpty())
                .andExpect(jsonPath("$.resumeHash").isNotEmpty())
                .andExpect(jsonPath("$.notesThreeTones.formal").isNotEmpty())
                .andExpect(jsonPath("$.notesThreeTones.concise").isNotEmpty())
                .andExpect(jsonPath("$.notesThreeTones.enthusiastic").isNotEmpty())
                .andExpect(jsonPath("$.truthfulnessPassed").value(true))
                .andExpect(jsonPath("$.routesToReviewQueue").value(true))
                .andReturn();

        // 5. Save approved answers for the two default factual/preference questions
        mockMvc.perform(post("/api/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionText", "What is your current city?",
                                "category", "FACTUAL",
                                "approvedAnswer", "Bengaluru",
                                "confidence", 1.0
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionText", "What is your preferred work mode (remote or hybrid)?",
                                "category", "PREFERENCE",
                                "approvedAnswer", "Hybrid",
                                "confidence", 1.0
                        ))))
                .andExpect(status().isCreated());

        // 6. Call GET /api/applications/packs/{jobId} again -> readinessScore >= 70 and routesToReviewQueue = false!
        MvcResult readyPackRes = mockMvc.perform(get("/api/applications/packs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truthfulnessPassed").value(true))
                .andExpect(jsonPath("$.routesToReviewQueue").value(false))
                .andReturn();
        JsonNode readyJson = objectMapper.readTree(readyPackRes.getResponse().getContentAsString());
        assertThat(readyJson.get("readinessScore").asInt()).isGreaterThanOrEqualTo(85);

        // 7. Inject an unsupported claim via POST /api/applications/packs/{jobId} -> truthfulness report blocks it & routes to review!
        mockMvc.perform(post("/api/applications/packs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customNoteOverride", "I hold a Ph.D and have 12 years of experience in Kubernetes and Rust."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truthfulnessPassed").value(false))
                .andExpect(jsonPath("$.routesToReviewQueue").value(true));
    }
}

