package in.skillsignal.career;

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

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage31CareerRoadmapAndInterviewPrepTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void skillRoadmapWeeklyPlanCheckboxLinksInterviewPrepTopicSetsMockInterviewRubricWeakTopicsAndDisclaimer() throws Exception {
        String email = "stage31." + System.currentTimeMillis() + "@skillsignal.test";
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Stage31Pass!123",
                                "fullName", "Meera Krishnan"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 1. Generate weekly skill roadmap from missing skills -> includes learning links from config file
        MvcResult genRes = mockMvc.perform(post("/api/career/roadmap/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "missingSkills", List.of("Kafka", "Kubernetes")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skillName").value("Kafka"))
                .andExpect(jsonPath("$[0].weekNumber").value(1))
                .andExpect(jsonPath("$[0].completed").value(false))
                .andExpect(jsonPath("$[0].learningLinksJson").isNotEmpty())
                .andReturn();
        String itemId = objectMapper.readTree(genRes.getResponse().getContentAsString()).get(0).get("id").asText();

        // 2. Toggle checkbox progress
        mockMvc.perform(put("/api/career/roadmap/" + itemId + "/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/career/roadmap")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.completedItems").value(1));

        // 3. Topic sets include Java, Spring Boot, SQL, Microservices, Kafka, DSA + custom field + behavioural/project + disclaimer
        mockMvc.perform(get("/api/career/interview-prep/topics")
                        .param("customTopic", "Financial Risk Analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclaimer").value(CareerService.PRACTICE_DISCLAIMER))
                .andExpect(jsonPath("$.topicSets.Java").isArray())
                .andExpect(jsonPath("$.topicSets['Spring Boot']").isArray())
                .andExpect(jsonPath("$.topicSets.SQL").isArray())
                .andExpect(jsonPath("$.topicSets.Microservices").isArray())
                .andExpect(jsonPath("$.topicSets.Kafka").isArray())
                .andExpect(jsonPath("$.topicSets.DSA").isArray())
                .andExpect(jsonPath("$.topicSets['Financial Risk Analytics']").isArray())
                .andExpect(jsonPath("$.behaviouralQuestions").isArray())
                .andExpect(jsonPath("$.projectQuestions").isArray());

        // 4. Interview prep from a Job Description
        MvcResult jobRes = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Distributed Systems Engineer",
                                "company", "CloudPulse India",
                                "rawDescription", "Java, Spring Boot, Kafka, Microservices"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = objectMapper.readTree(jobRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/career/interview-prep/from-job/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Distributed Systems Engineer"))
                .andExpect(jsonPath("$.technicalQuestions").isArray())
                .andExpect(jsonPath("$.disclaimer").value(CareerService.PRACTICE_DISCLAIMER));

        // 5. Text mock interview with short answer -> flags weak topics and includes mandatory disclaimer
        mockMvc.perform(post("/api/career/mock-interview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobId", jobId,
                                "topic", "Kafka",
                                "question", "Explain Kafka consumer groups and partition rebalancing.",
                                "userAnswer", "Consumers read from partitions."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic").value("Kafka"))
                .andExpect(jsonPath("$.rubricFeedback.overallScore").isNumber())
                .andExpect(jsonPath("$.weakTopics", hasItems("Kafka — Depth & Completeness", "Kafka — Applied Project Examples")))
                .andExpect(jsonPath("$.disclaimer").value(CareerService.PRACTICE_DISCLAIMER));

        mockMvc.perform(get("/api/career/mock-interview/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weakTopics").isArray())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.disclaimer").value(CareerService.PRACTICE_DISCLAIMER));
    }
}

