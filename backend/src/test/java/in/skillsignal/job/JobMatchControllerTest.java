package in.skillsignal.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobMatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "JobMatch123!",
                                "fullName", "Job Matcher"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private byte[] createSampleResumePdf() throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(50, 700);
                cs.showText("Senior Backend Engineer with 5 years of experience in Java, Spring Boot, PostgreSQL, and Docker.");
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void createJobComputeExplainableMatchAndEnforceUserOwnership() throws Exception {
        String tokenA = registerAndGetToken("job.usera@skillsignal.test");
        String tokenB = registerAndGetToken("job.userb@skillsignal.test");

        // Upload resume for User A
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", createSampleResumePdf()
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(pdf)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());

        // Add Job for User A
        String jd = "We are looking for a Backend Engineer in Bengaluru (Hybrid) with 4+ years of experience " +
                "in Java, Spring Boot, PostgreSQL, and Kubernetes.\nNice to have: Docker, Redis.";
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Backend Engineer",
                                "company", "FinTech India",
                                "location", "Bengaluru",
                                "workMode", "HYBRID",
                                "rawDescription", jd
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Backend Engineer"))
                .andReturn();

        JsonNode jobNode = objectMapper.readTree(jobRes.getResponse().getContentAsString());
        String jobId = jobNode.get("id").asText();

        // User B cannot access User A's job (returns 404)
        mockMvc.perform(get("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Compute match for User A
        mockMvc.perform(post("/api/jobs/" + jobId + "/match")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.matchedRequired").isArray())
                .andExpect(jsonPath("$.missingRequired").isArray())
                .andExpect(jsonPath("$.suggestions.length()").value(3))
                .andExpect(jsonPath("$.disclaimer").value("This score estimates relevance. It does not guarantee an interview or selection."));
    }
}

