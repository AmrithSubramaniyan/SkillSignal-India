package in.skillsignal.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
import in.skillsignal.profile.CareerProfileController;
import in.skillsignal.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileConsentPrivacyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.uploads.dir:./target/test-uploads}")
    private String uploadsDir;

    @Test
    void profileConsentAuditExportAndAccountErasureFlow() throws Exception {
        String email = "privacy.user@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "PrivacyPass123!",
                                "fullName", "Rohan Verma"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regNode = objectMapper.readTree(reg.getResponse().getContentAsString());
        String token = regNode.get("token").asText();
        UUID userId = UUID.fromString(regNode.get("user").get("id").asText());

        // 1. Update Career Profile
        CareerProfileController.CareerProfileDto profilePayload = new CareerProfileController.CareerProfileDto(
                List.of("Backend Engineer", "Senior Java Developer"),
                4.5,
                List.of("Bengaluru", "Pune"),
                List.of("HYBRID", "REMOTE"),
                24.0,
                "INR",
                "LPA",
                30,
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of("FULL_TIME"),
                "India",
                List.of("English", "Hindi"),
                "Asia/Kolkata",
                null
        );
        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profilePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearsOfExperience").value(4.5))
                .andExpect(jsonPath("$.noticePeriodDays").value(30));

        // 2. Grant & Revoke Consent
        mockMvc.perform(put("/api/consents/STORE_RESUME_ANSWERS")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "granted", true,
                                "consentVersion", "v1.1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(true))
                .andExpect(jsonPath("$.consentVersion").value("v1.1"))
                .andExpect(jsonPath("$.grantedAt").isNotEmpty());

        mockMvc.perform(put("/api/consents/STORE_RESUME_ANSWERS")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "granted", false,
                                "consentVersion", "v1.1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(false))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());

        // 3. Export Data
        mockMvc.perform(get("/api/privacy/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.careerProfile.yearsOfExperience").value(4.5))
                .andExpect(jsonPath("$.auditLogs").isArray());

        // 4. Create a simulated uploaded file under ./uploads/{userId}/sample.pdf
        Path userDir = Paths.get(uploadsDir).toAbsolutePath().normalize().resolve(userId.toString());
        Files.createDirectories(userDir);
        Path sampleFile = userDir.resolve("sample.pdf");
        Files.writeString(sampleFile, "dummy file");
        assertThat(Files.exists(sampleFile)).isTrue();

        // 5. Delete Account erases both DB records and uploaded files
        mockMvc.perform(delete("/api/privacy/account")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(Files.exists(sampleFile)).isFalse();
        assertThat(Files.exists(userDir)).isFalse();
    }
}
