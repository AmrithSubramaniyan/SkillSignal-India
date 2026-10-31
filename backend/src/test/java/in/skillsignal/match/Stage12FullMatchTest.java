package in.skillsignal.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
import in.skillsignal.job.JobController;
import in.skillsignal.profile.CareerProfileController;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage12FullMatchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullMatchComputesSalaryAndQualificationFitWhenDataExistsAggregatesMissingSkillsComparesJobsAndStoresFeedback() throws Exception {
        String token = registerAndGetToken("stage12.match@skillsignal.test");

        // 1. Upload a PDF resume with B.Tech in Computer Science, Java, Spring Boot, SQL (3.5 years experience)
        byte[] pdfBytes = createResumePdf(
                "Ananya Nair - B.Tech in Computer Science. 3.5 years experience in Java, Spring Boot, SQL."
        );
        MockMultipartFile file = new MockMultipartFile("file", "ananya_resume.pdf", "application/pdf", pdfBytes);
        MvcResult uploadRes = mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .param("versionLabel", "v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String versionId = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/resumes/versions/" + versionId + "/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verifiedSkills", List.of("Java", "Spring Boot", "SQL"),
                                "yearsOfExperience", 3.5
                        ))))
                .andExpect(status().isOk());

        // 2. Save career profile with expectedSalary = 20.0 LPA
        var profileDto = new CareerProfileController.CareerProfileDto(
                List.of("Backend Engineer"),
                3.5,
                List.of("Bengaluru"),
                List.of("HYBRID", "REMOTE"),
                20.0,
                "INR",
                "LPA",
                30,
                List.of("Java", "Spring Boot", "SQL"),
                List.of("FULL_TIME"),
                "India",
                List.of("English", "Hindi"),
                "Authorized to work in India",
                null
        );
        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileDto)))
                .andExpect(status().isOk());

        // 3. Create Job A (with disclosed salary 22-28 LPA and requiredEducation B.Tech, missing Docker & Kubernetes)
        var jobAReq = new JobController.CreateJobRequest(
                "Senior Backend Engineer",
                "FinCore India",
                "Bengaluru",
                "HYBRID",
                "https://fincore.example/jobs/1",
                "Hiring Senior Backend Engineer in Bengaluru (Hybrid) with Java, Spring Boot, Docker, Kubernetes.",
                List.of("Java", "Spring Boot", "Docker", "Kubernetes"),
                List.of("Apache Kafka"),
                3.0,
                22.0,
                28.0,
                "INR",
                "LPA",
                "B.Tech"
        );
        MvcResult jobARes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobAReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID jobAId = UUID.fromString(objectMapper.readTree(jobARes.getResponse().getContentAsString()).get("id").asText());

        // 4. Create Job B (without salary or requiredEducation -> must report 'not enough data', missing Docker)
        var jobBReq = new JobController.CreateJobRequest(
                "Platform Engineer",
                "CloudScale",
                "Bengaluru",
                "REMOTE",
                "https://cloudscale.example/jobs/2",
                "Hiring Platform Engineer with Java, SQL, Docker.",
                List.of("Java", "SQL", "Docker"),
                List.of(),
                2.0,
                null,
                null,
                null,
                null,
                null
        );
        MvcResult jobBRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobBReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID jobBId = UUID.fromString(objectMapper.readTree(jobBRes.getResponse().getContentAsString()).get("id").asText());

        // 5. Compute match for Job A -> verify explanation completeness + salaryFit + qualificationFit
        MvcResult matchARes = mockMvc.perform(post("/api/jobs/" + jobAId + "/match")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode matchA = objectMapper.readTree(matchARes.getResponse().getContentAsString());
        assertThat(matchA.get("salaryFit").asText()).contains("Aligned");
        assertThat(matchA.get("qualificationFit").asText()).contains("Meets qualification (B.Tech)");
        assertThat(matchA.get("suggestions").size()).isEqualTo(3);
        assertThat(matchA.get("disclaimer").asText()).isEqualTo(MatchResultDto.MANDATORY_DISCLAIMER);

        // 6. Compute match for Job B -> verify 'not enough data' when salary/education are absent
        MvcResult matchBRes = mockMvc.perform(post("/api/jobs/" + jobBId + "/match")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode matchB = objectMapper.readTree(matchBRes.getResponse().getContentAsString());
        assertThat(matchB.get("salaryFit").asText()).isEqualTo("not enough data");
        assertThat(matchB.get("qualificationFit").asText()).isEqualTo("not enough data");

        // 7. Submit THUMBS_UP feedback on Job A match and verify persistence
        MvcResult fbRes = mockMvc.perform(put("/api/jobs/" + jobAId + "/match/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "feedback", "THUMBS_UP",
                                "note", "Very clear breakdown"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode fbNode = objectMapper.readTree(fbRes.getResponse().getContentAsString());
        assertThat(fbNode.get("userFeedback").asText()).isEqualTo("THUMBS_UP");
        assertThat(fbNode.get("feedbackNote").asText()).isEqualTo("Very clear breakdown");

        // 8. Aggregated missing skills across saved jobs (Docker is missing in both Job A and Job B -> jobCount = 2)
        MvcResult missingRes = mockMvc.perform(get("/api/jobs/missing-skills-summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode missingArr = objectMapper.readTree(missingRes.getResponse().getContentAsString());
        assertThat(missingArr.size()).isGreaterThanOrEqualTo(2);
        assertThat(missingArr.get(0).get("skill").asText()).isEqualTo("Docker");
        assertThat(missingArr.get(0).get("jobCount").asInt()).isEqualTo(2);

        // 9. Compare 2 jobs side by side
        MvcResult compareRes = mockMvc.perform(post("/api/jobs/compare")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jobIds", List.of(jobAId, jobBId)))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode compareArr = objectMapper.readTree(compareRes.getResponse().getContentAsString());
        assertThat(compareArr.size()).isEqualTo(2);
    }

    private byte[] createResumePdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "SecurePass123!",
                                "fullName", "Stage 12 Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}
