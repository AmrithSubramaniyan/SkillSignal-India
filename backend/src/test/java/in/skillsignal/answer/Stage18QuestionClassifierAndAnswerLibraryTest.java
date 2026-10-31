package in.skillsignal.answer;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage18QuestionClassifierAndAnswerLibraryTest {

    @Autowired
    private QuestionClassifierAndAnswerService classifierService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void classifier_detectsAllSixCategoriesAndSensitiveKeywords() {
        assertThat(classifierService.classifyQuestion("What is your current city?")).isEqualTo("FACTUAL");
        assertThat(classifierService.classifyQuestion("What is your preferred work mode (remote or hybrid)?")).isEqualTo("PREFERENCE");
        assertThat(classifierService.classifyQuestion("How many years of experience do you have in Java?")).isEqualTo("NUMERIC");
        assertThat(classifierService.classifyQuestion("Why do you want to join our engineering team?")).isEqualTo("FREE_TEXT");

        // All sensitive / legal / declaration / background-check / visa / work-authorisation / disclosure / salary-history / demographic questions
        assertThat(classifierService.classifyQuestion("Do you require visa sponsorship or work authorisation in India?")).isEqualTo("LEGAL_CONSENT");
        assertThat(classifierService.classifyQuestion("Do you consent to a criminal background check?")).isEqualTo("LEGAL_CONSENT");
        assertThat(classifierService.classifyQuestion("I hereby certify that this declaration is true.")).isEqualTo("LEGAL_CONSENT");
        assertThat(classifierService.classifyQuestion("What is your salary history / last drawn CTC?")).isEqualTo("LEGAL_CONSENT");
        assertThat(classifierService.classifyQuestion("Voluntary demographic self-identification: gender and disability status")).isEqualTo("LEGAL_CONSENT");
        assertThat(classifierService.classifyQuestion("Are you bound by any non-compete or NDA disclosure?")).isEqualTo("LEGAL_CONSENT");

        // Unknown
        assertThat(classifierService.classifyQuestion(" T-shirt size for company offsite? ")).isEqualTo("UNKNOWN");
    }

    @Test
    void answerLibraryCrudAndAutoFillVsPauseRules() throws Exception {
        // 1. Register user
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "stage18.answers@skillsignal.test",
                                "password", "Stage18Pass!123",
                                "fullName", "Siddharth Menon"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 2. Save FACTUAL, PREFERENCE, and FREE_TEXT answers via POST /api/answers
        MvcResult factualRes = mockMvc.perform(post("/api/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionText", "What is your current city?",
                                "category", "FACTUAL",
                                "approvedAnswer", "Bengaluru",
                                "confidence", 1.0
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("FACTUAL"))
                .andReturn();
        String factualId = objectMapper.readTree(factualRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionText", "Are you willing to relocate?",
                                "category", "PREFERENCE",
                                "approvedAnswer", "Yes, open to Bengaluru and Hyderabad",
                                "confidence", 0.95
                        ))))
                .andExpect(status().isCreated());

        // Update FACTUAL answer via PUT /api/answers/{id}
        mockMvc.perform(put("/api/answers/" + factualId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "approvedAnswer", "Bengaluru, Karnataka",
                                "confidence", 1.0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedAnswer").value("Bengaluru, Karnataka"));

        // Verify GET /api/answers
        mockMvc.perform(get("/api/answers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 3. Classify & Resolve batch of questions:
        // - Factual with exact approved answer -> autoFillAllowed=true, requiresUserPause=false
        // - Preference with exact approved answer -> autoFillAllowed=true, requiresUserPause=false
        // - Legal/visa question -> ALWAYS requiresUserPause=true, autoFillAllowed=false
        // - Salary-history question -> ALWAYS requiresUserPause=true, autoFillAllowed=false
        // - Free-text without template -> requiresUserPause=true, offerToSaveNewAnswer=true
        // - Unknown question -> ALWAYS requiresUserPause=true, autoFillAllowed=false
        MvcResult classifyRes = mockMvc.perform(post("/api/answers/classify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questions", List.of(
                                        "What is your current city?",
                                        "Are you willing to relocate?",
                                        "Do you require visa sponsorship or work authorisation?",
                                        "What is your salary history and last drawn CTC?",
                                        "Why do you want to join our company?",
                                        "What is your favourite board game?"
                                )
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = objectMapper.readTree(classifyRes.getResponse().getContentAsString());
        assertThat(arr.size()).isEqualTo(6);

        // [0] FACTUAL -> autoFillAllowed=true, requiresUserPause=false
        assertThat(arr.get(0).get("category").asText()).isEqualTo("FACTUAL");
        assertThat(arr.get(0).get("autoFillAllowed").asBoolean()).isTrue();
        assertThat(arr.get(0).get("requiresUserPause").asBoolean()).isFalse();
        assertThat(arr.get(0).get("approvedAnswer").asText()).isEqualTo("Bengaluru, Karnataka");

        // [1] PREFERENCE -> autoFillAllowed=true, requiresUserPause=false
        assertThat(arr.get(1).get("category").asText()).isEqualTo("PREFERENCE");
        assertThat(arr.get(1).get("autoFillAllowed").asBoolean()).isTrue();
        assertThat(arr.get(1).get("requiresUserPause").asBoolean()).isFalse();

        // [2] LEGAL_CONSENT (visa/work-authorisation) -> ALWAYS pauses
        assertThat(arr.get(2).get("category").asText()).isEqualTo("LEGAL_CONSENT");
        assertThat(arr.get(2).get("autoFillAllowed").asBoolean()).isFalse();
        assertThat(arr.get(2).get("requiresUserPause").asBoolean()).isTrue();

        // [3] LEGAL_CONSENT (salary-history) -> ALWAYS pauses
        assertThat(arr.get(3).get("category").asText()).isEqualTo("LEGAL_CONSENT");
        assertThat(arr.get(3).get("autoFillAllowed").asBoolean()).isFalse();
        assertThat(arr.get(3).get("requiresUserPause").asBoolean()).isTrue();

        // [4] FREE_TEXT (no template yet) -> pauses & offers to save
        assertThat(arr.get(4).get("category").asText()).isEqualTo("FREE_TEXT");
        assertThat(arr.get(4).get("autoFillAllowed").asBoolean()).isFalse();
        assertThat(arr.get(4).get("requiresUserPause").asBoolean()).isTrue();
        assertThat(arr.get(4).get("offerToSaveNewAnswer").asBoolean()).isTrue();

        // [5] UNKNOWN -> ALWAYS pauses
        assertThat(arr.get(5).get("category").asText()).isEqualTo("UNKNOWN");
        assertThat(arr.get(5).get("autoFillAllowed").asBoolean()).isFalse();
        assertThat(arr.get(5).get("requiresUserPause").asBoolean()).isTrue();

        // 4. Expired answer must NOT auto-fill
        mockMvc.perform(put("/api/answers/" + factualId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expiresAt", Instant.now().minusSeconds(60).toString()
                        ))))
                .andExpect(status().isOk());

        MvcResult expiredRes = mockMvc.perform(post("/api/answers/classify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questions", List.of("What is your current city?")
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode expNode = objectMapper.readTree(expiredRes.getResponse().getContentAsString()).get(0);
        assertThat(expNode.get("autoFillAllowed").asBoolean()).isFalse();
        assertThat(expNode.get("requiresUserPause").asBoolean()).isTrue();
        assertThat(expNode.get("offerToSaveNewAnswer").asBoolean()).isTrue();
    }
}

