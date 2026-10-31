package in.skillsignal.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "ResumePass123!",
                                "fullName", "Resume Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private byte[] createPdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null && !text.isEmpty()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.newLineAtOffset(50, 700);
                    for (String line : text.split("\n")) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -16);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createDocxWithText(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void resumeUploadValidationParsingVerifiedSkillsMaxVersionsAndIsolation() throws Exception {
        String tokenUserA = registerAndGetToken("user.a.resume@skillsignal.test");
        String tokenUserB = registerAndGetToken("user.b.resume@skillsignal.test");

        // 1. Bad file extension (.exe) -> 400
        MockMultipartFile badExt = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "MZ-fake".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(badExt)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isBadRequest());

        // 2. Fake PDF magic bytes (named .pdf but contains plain text) -> 400
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "Not a real PDF header".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(fakePdf)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File content does not match PDF magic bytes (%PDF)."));

        // 3. Empty file (0 bytes) -> 400
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(emptyFile)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isBadRequest());

        // 4. Scanned/Blank PDF with no extractable text -> 422
        byte[] blankPdfBytes = createPdfWithText("");
        MockMultipartFile scannedPdf = new MockMultipartFile(
                "file", "scanned.pdf", "application/pdf", blankPdfBytes
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(scannedPdf)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isUnprocessableEntity());

        // 5. Oversize file (> 5 MB) -> 413
        byte[] oversizeBytes = new byte[5 * 1024 * 1024 + 1024];
        oversizeBytes[0] = 0x25; oversizeBytes[1] = 0x50; oversizeBytes[2] = 0x44; oversizeBytes[3] = 0x46;
        MockMultipartFile oversizePdf = new MockMultipartFile(
                "file", "huge.pdf", "application/pdf", oversizeBytes
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(oversizePdf)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isPayloadTooLarge());

        // 6. Valid PDF upload (Version 1)
        byte[] validPdf = createPdfWithText(
                "Aarav Sharma - 4 years of experience in Java, Spring Boot, React, and PostgreSQL.\n" +
                "B.Tech Computer Science, IIT Bombay\n" +
                "- Built distributed payment microservices using Spring Boot and Docker"
        );
        MockMultipartFile pdfFile1 = new MockMultipartFile(
                "file", "aarav_resume_v1.pdf", "application/pdf", validPdf
        );
        MvcResult upload1 = mockMvc.perform(multipart("/api/resumes/upload").file(pdfFile1)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.yearsOfExperience").value(4.0))
                .andExpect(jsonPath("$.verifiedSkills").isArray())
                .andReturn();

        JsonNode v1Json = objectMapper.readTree(upload1.getResponse().getContentAsString());
        String v1Id = v1Json.get("id").asText();

        // 7. Other user's file returns 404 (User B trying to read or update User A's resume version)
        mockMvc.perform(get("/api/resumes/versions/" + v1Id)
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/resumes/versions/" + v1Id)
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        // 8. User A edits verified skills
        mockMvc.perform(put("/api/resumes/versions/" + v1Id + "/skills")
                        .header("Authorization", "Bearer " + tokenUserA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verifiedSkills", List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                                "yearsOfExperience", 4.5
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearsOfExperience").value(4.5))
                .andExpect(jsonPath("$.verifiedSkills.length()").value(4));

        // 9. Valid DOCX upload (Version 2) and PDF upload (Version 3), then 4th upload rejected (max 3 versions)
        byte[] validDocx = createDocxWithText("Experienced nurse with 6 years of experience in Patient Care, Triage, and Basic Life Support.");
        MockMultipartFile docxFile2 = new MockMultipartFile(
                "file", "resume_v2.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", validDocx
        );
        mockMvc.perform(multipart("/api/resumes/upload").file(docxFile2)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));

        mockMvc.perform(multipart("/api/resumes/upload").file(pdfFile1)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(3));

        // 4th upload fails because max 3 versions allowed per user
        mockMvc.perform(multipart("/api/resumes/upload").file(pdfFile1)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isBadRequest());
    }
}

