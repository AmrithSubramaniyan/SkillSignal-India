package in.skillsignal.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
import in.skillsignal.job.JobController;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage13ResumeTailoringAndTruthfulnessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void tailorsResumeFromExistingContentBlocksUnsupportedClaimsAndExportsApprovedDocxAndPdf() throws Exception {
        String token = registerAndGetToken("stage13.tailor@skillsignal.test");

        // 1. Upload candidate resume with B.Tech, 3.0 years experience, Java, Spring Boot, PostgreSQL
        byte[] pdfBytes = createResumePdf(
                "Vikram Rao. B.Tech in Information Technology. 3 years experience building backend services with Java and Spring Boot. Designed relational schemas in PostgreSQL."
        );
        MockMultipartFile file = new MockMultipartFile("file", "vikram_resume.pdf", "application/pdf", pdfBytes);
        MvcResult uploadRes = mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .param("versionLabel", "v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        UUID resumeVersionId = UUID.fromString(objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(put("/api/resumes/versions/" + resumeVersionId + "/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verifiedSkills", List.of("Java", "Spring Boot", "PostgreSQL"),
                                "yearsOfExperience", 3.0
                        ))))
                .andExpect(status().isOk());

        // 2. Create target Job requiring Java, Spring Boot, Kubernetes, AWS
        var jobReq = new JobController.CreateJobRequest(
                "Cloud Backend Engineer",
                "PayGrid India",
                "Bengaluru",
                "HYBRID",
                "https://paygrid.example/jobs/9",
                "Hiring Cloud Backend Engineer in Bengaluru with Java, Spring Boot, Kubernetes, AWS.",
                List.of("Java", "Spring Boot", "Kubernetes", "AWS"),
                List.of("Docker"),
                3.0,
                18.0,
                26.0,
                "INR",
                "LPA",
                "B.Tech"
        );
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID jobId = UUID.fromString(objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText());

        // 3. Generate tailored draft (rewords & reorders existing content only)
        MvcResult tailorRes = mockMvc.perform(post("/api/resumes/tailor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resumeVersionId", resumeVersionId,
                                "jobId", jobId
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode tailoredNode = objectMapper.readTree(tailorRes.getResponse().getContentAsString());
        String tailoredId = tailoredNode.get("id").asText();

        assertThat(tailoredNode.get("keywordGaps").toString()).contains("Kubernetes", "AWS");
        assertThat(tailoredNode.get("tips").size()).isEqualTo(3);
        assertThat(tailoredNode.get("diff").size()).isGreaterThanOrEqualTo(1);
        assertThat(tailoredNode.get("truthfulnessPassed").asBoolean()).isTrue();
        assertThat(tailoredNode.get("userApproved").asBoolean()).isFalse();

        // 4. Attempting to download before user approval must be rejected with 409 Conflict
        mockMvc.perform(get("/api/resumes/tailor/" + tailoredId + "/download?format=pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // 5. Inject unsupported claims (Kubernetes, AWS, Ph.D, 8 years experience) -> Truthfulness check MUST BLOCK!
        MvcResult fabricatedRes = mockMvc.perform(put("/api/resumes/tailor/" + tailoredId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tailoredText", "Vikram Rao, Ph.D. I have 8 years experience in Java, Spring Boot, Kubernetes, and AWS."
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode fabNode = objectMapper.readTree(fabricatedRes.getResponse().getContentAsString());
        assertThat(fabNode.get("truthfulnessPassed").asBoolean()).isFalse();
        String unsupportedStr = fabNode.get("unsupportedClaims").toString();
        assertThat(unsupportedStr).contains("Kubernetes");
        assertThat(unsupportedStr).contains("AWS");
        assertThat(unsupportedStr).containsIgnoringCase("PH.D");
        assertThat(unsupportedStr).contains("8.0 years exceeds verified 3.0 years");

        // 6. Approving or downloading a draft with unsupported claims MUST be blocked with 422 Unprocessable Entity
        mockMvc.perform(post("/api/resumes/tailor/" + tailoredId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/resumes/tailor/" + tailoredId + "/download?format=pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // 7. Restore truthful draft (only verified Java, Spring Boot, PostgreSQL, B.Tech, 3 years experience)
        MvcResult truthfulRes = mockMvc.perform(put("/api/resumes/tailor/" + tailoredId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tailoredText", "Vikram Rao (B.Tech). 3 years experience building backend services with Java, Spring Boot, and PostgreSQL."
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(truthfulRes.getResponse().getContentAsString()).get("truthfulnessPassed").asBoolean()).isTrue();

        // 8. Approve the truthful draft
        MvcResult approveRes = mockMvc.perform(post("/api/resumes/tailor/" + tailoredId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(approveRes.getResponse().getContentAsString()).get("userApproved").asBoolean()).isTrue();

        // 9. Download both PDF and DOCX and verify binary magic bytes
        MvcResult pdfDownload = mockMvc.perform(get("/api/resumes/tailor/" + tailoredId + "/download?format=pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        byte[] downloadedPdf = pdfDownload.getResponse().getContentAsByteArray();
        assertThat(new String(downloadedPdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        MvcResult docxDownload = mockMvc.perform(get("/api/resumes/tailor/" + tailoredId + "/download?format=docx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        byte[] downloadedDocx = docxDownload.getResponse().getContentAsByteArray();
        assertThat(downloadedDocx[0]).isEqualTo((byte) 'P');
        assertThat(downloadedDocx[1]).isEqualTo((byte) 'K');
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
                                "fullName", "Stage 13 Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}
