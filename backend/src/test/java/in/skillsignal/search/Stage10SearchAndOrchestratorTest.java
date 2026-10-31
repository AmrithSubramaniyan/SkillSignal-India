package in.skillsignal.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage10SearchAndOrchestratorTest {

    @Autowired
    private NaturalLanguageQueryParser queryParser;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void parsesOverTwentyNaturalLanguageJobQueriesIntoEditableFilterChips() {
        // 22 diverse natural-language queries spanning freshers, senior, India cities, LPA, cross-industry
        String q1 = "Backend developer with Java and Spring Boot in Bangalore 3+ years hybrid above 18 LPA";
        var r1 = queryParser.parse(q1);
        assertThat(r1.filters().getSkills()).contains("Java", "Spring Boot");
        assertThat(r1.filters().getLocations()).contains("Bengaluru");
        assertThat(r1.filters().getMinExperienceYears()).isEqualTo(3);
        assertThat(r1.filters().getWorkMode()).isEqualTo("HYBRID");
        assertThat(r1.filters().getMinSalaryLpa()).isEqualTo(18.0);
        assertThat(r1.chips()).isNotEmpty();

        String q2 = "Fresher React TypeScript frontend engineer in Pune full-time posted last 7 days";
        var r2 = queryParser.parse(q2);
        assertThat(r2.filters().getSkills()).contains("React", "TypeScript");
        assertThat(r2.filters().getMinExperienceYears()).isEqualTo(0);
        assertThat(r2.filters().getMaxExperienceYears()).isEqualTo(1);
        assertThat(r2.filters().getLocations()).contains("Pune");
        assertThat(r2.filters().getEmploymentType()).isEqualTo("FULL_TIME");
        assertThat(r2.filters().getPostedWithinDays()).isEqualTo(7);

        String q3 = "Remote Python data scientist with PyTorch and SQL 4 to 7 years min 25 LPA";
        var r3 = queryParser.parse(q3);
        assertThat(r3.filters().getWorkMode()).isEqualTo("REMOTE");
        assertThat(r3.filters().getSkills()).contains("Python", "PyTorch", "SQL");
        assertThat(r3.filters().getMinExperienceYears()).isEqualTo(4);
        assertThat(r3.filters().getMaxExperienceYears()).isEqualTo(7);
        assertThat(r3.filters().getMinSalaryLpa()).isEqualTo(25.0);

        String q4 = "Registered Nurse with ICU Care and Patient Care in Hyderabad onsite full time";
        var r4 = queryParser.parse(q4);
        assertThat(r4.filters().getSkills()).contains("ICU Care", "Patient Care");
        assertThat(r4.filters().getLocations()).contains("Hyderabad");
        assertThat(r4.filters().getWorkMode()).isEqualTo("ONSITE");
        assertThat(r4.filters().getEmploymentType()).isEqualTo("FULL_TIME");

        String q5 = "Chartered Accountant with GST and Financial Auditing in Mumbai 5+ years 15 LPA";
        var r5 = queryParser.parse(q5);
        assertThat(r5.filters().getSkills()).contains("GST Compliance", "Financial Auditing");
        assertThat(r5.filters().getLocations()).contains("Mumbai");
        assertThat(r5.filters().getMinExperienceYears()).isEqualTo(5);
        assertThat(r5.filters().getMinSalaryLpa()).isEqualTo(15.0);

        String q6 = "Entry level QA engineer with Selenium and Playwright in Chennai posted today";
        var r6 = queryParser.parse(q6);
        assertThat(r6.filters().getSkills()).contains("Selenium", "Playwright");
        assertThat(r6.filters().getMinExperienceYears()).isEqualTo(0);
        assertThat(r6.filters().getMaxExperienceYears()).isEqualTo(1);
        assertThat(r6.filters().getLocations()).contains("Chennai");
        assertThat(r6.filters().getPostedWithinDays()).isEqualTo(1);

        String q7 = "Senior DevOps engineer Kubernetes Docker AWS in Gurgaon hybrid 30+ LPA";
        var r7 = queryParser.parse(q7);
        assertThat(r7.filters().getSkills()).contains("Kubernetes", "Docker", "AWS");
        assertThat(r7.filters().getLocations()).contains("Gurugram");
        assertThat(r7.filters().getMinExperienceYears()).isEqualTo(5);
        assertThat(r7.filters().getWorkMode()).isEqualTo("HYBRID");
        assertThat(r7.filters().getMinSalaryLpa()).isEqualTo(30.0);

        String q8 = "UI/UX designer with Figma and User Research internship work from home";
        var r8 = queryParser.parse(q8);
        assertThat(r8.filters().getSkills()).contains("Figma", "User Research");
        assertThat(r8.filters().getEmploymentType()).isEqualTo("INTERNSHIP");
        assertThat(r8.filters().getWorkMode()).isEqualTo("REMOTE");

        String q9 = "Supply chain manager with SAP and Inventory Management in Ahmedabad 6+ years";
        var r9 = queryParser.parse(q9);
        assertThat(r9.filters().getSkills()).contains("SAP ERP", "Inventory Management");
        assertThat(r9.filters().getLocations()).contains("Ahmedabad");
        assertThat(r9.filters().getMinExperienceYears()).isEqualTo(6);

        String q10 = "Corporate lawyer with Contract Drafting and Legal Research in New Delhi 4+ years";
        var r10 = queryParser.parse(q10);
        assertThat(r10.filters().getSkills()).contains("Contract Drafting", "Legal Research");
        assertThat(r10.filters().getLocations()).contains("Delhi NCR");
        assertThat(r10.filters().getMinExperienceYears()).isEqualTo(4);

        String q11 = "Physics teacher with Curriculum Development and Classroom Management in Kolkata part-time";
        var r11 = queryParser.parse(q11);
        assertThat(r11.filters().getSkills()).contains("Curriculum Development", "Classroom Management");
        assertThat(r11.filters().getLocations()).contains("Kolkata");
        assertThat(r11.filters().getEmploymentType()).isEqualTo("PART_TIME");

        String q12 = "B2B Sales manager with Salesforce in Noida 2 to 5 years with disclosed salary";
        var r12 = queryParser.parse(q12);
        assertThat(r12.filters().getSkills()).contains("B2B Sales", "Salesforce CRM");
        assertThat(r12.filters().getLocations()).contains("Noida");
        assertThat(r12.filters().getMinExperienceYears()).isEqualTo(2);
        assertThat(r12.filters().getMaxExperienceYears()).isEqualTo(5);
        assertThat(r12.filters().getDisclosedSalaryOnly()).isTrue();

        String q13 = "Clinical pharmacist with Pharmacovigilance in Bengaluru posted this week";
        var r13 = queryParser.parse(q13);
        assertThat(r13.filters().getSkills()).contains("Pharmacovigilance");
        assertThat(r13.filters().getLocations()).contains("Bengaluru");
        assertThat(r13.filters().getPostedWithinDays()).isEqualTo(7);

        String q14 = "Financial analyst with Financial Modeling and Power BI at Zerodha in Bengaluru";
        var r14 = queryParser.parse(q14);
        assertThat(r14.filters().getSkills()).contains("Financial Modeling", "Power BI");
        assertThat(r14.filters().getCompany()).isEqualTo("Zerodha");
        assertThat(r14.filters().getLocations()).contains("Bengaluru");

        String q15 = "Saved jobs for Node.js and PostgreSQL not applied";
        var r15 = queryParser.parse(q15);
        assertThat(r15.filters().getSkills()).contains("Node.js", "PostgreSQL");
        assertThat(r15.filters().getSavedOnly()).isTrue();
        assertThat(r15.filters().getAppliedStatus()).isEqualTo("NOT_APPLIED");

        String q16 = "Contract SEO specialist remote posted last 14 days";
        var r16 = queryParser.parse(q16);
        assertThat(r16.filters().getSkills()).contains("Search Engine Optimization (SEO)");
        assertThat(r16.filters().getEmploymentType()).isEqualTo("CONTRACT");
        assertThat(r16.filters().getWorkMode()).isEqualTo("REMOTE");
        assertThat(r16.filters().getPostedWithinDays()).isEqualTo(14);

        String q17 = "Mechanical engineer with AutoCAD and SolidWorks in Coimbatore 1 to 3 years";
        var r17 = queryParser.parse(q17);
        assertThat(r17.filters().getSkills()).contains("AutoCAD", "SolidWorks");
        assertThat(r17.filters().getLocations()).contains("Coimbatore");
        assertThat(r17.filters().getMinExperienceYears()).isEqualTo(1);
        assertThat(r17.filters().getMaxExperienceYears()).isEqualTo(3);

        String q18 = "Software engineer with Kafka and Microservices from greenhouse in Bengaluru";
        var r18 = queryParser.parse(q18);
        assertThat(r18.filters().getSkills()).contains("Apache Kafka", "Microservices");
        assertThat(r18.filters().getSources()).contains("company_ats");
        assertThat(r18.filters().getLocations()).contains("Bengaluru");

        String q19 = "Data analyst with Tableau and Pandas in Kochi posted this month";
        var r19 = queryParser.parse(q19);
        assertThat(r19.filters().getSkills()).contains("Tableau", "Pandas");
        assertThat(r19.filters().getLocations()).contains("Kochi");
        assertThat(r19.filters().getPostedWithinDays()).isEqualTo(30);

        String q20 = "Risk analyst with Risk Management and IFRS in Bombay 12+ lakhs";
        var r20 = queryParser.parse(q20);
        assertThat(r20.filters().getSkills()).contains("Risk Management", "IFRS");
        assertThat(r20.filters().getLocations()).contains("Mumbai");
        assertThat(r20.filters().getMinSalaryLpa()).isEqualTo(12.0);

        String q21 = "Bookmarked Java jobs already applied";
        var r21 = queryParser.parse(q21);
        assertThat(r21.filters().getSkills()).contains("Java");
        assertThat(r21.filters().getSavedOnly()).isTrue();
        assertThat(r21.filters().getAppliedStatus()).isEqualTo("APPLIED");

        String q22 = "Electronic Health Records specialist in Trivandrum wfo";
        var r22 = queryParser.parse(q22);
        assertThat(r22.filters().getSkills()).contains("Electronic Health Records (EHR)");
        assertThat(r22.filters().getLocations()).contains("Thiruvananthapuram");
        assertThat(r22.filters().getWorkMode()).isEqualTo("ONSITE");
    }

    @Test
    void searchOrchestratorHandlesPerSourceTimeoutAndPartialFailuresGracefully() throws Exception {
        String token = registerAndGetToken("search.orchestrator@skillsignal.test");

        Map<String, Object> searchRequest = Map.of(
                "rawQuery", "Java Spring Boot in Bengaluru",
                "sources", List.of("manual", "company_ats", "naukri"),
                "greenhouseBoards", List.of("fixture-india-tech", "timeout-board", "error-board"),
                "perSourceTimeoutMs", 150
        );

        MvcResult result = mockMvc.perform(post("/api/jobs/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        // Verify jobs were found from fixture-india-tech despite timeout-board, error-board, and disabled naukri
        assertThat(root.get("jobs").size()).isGreaterThanOrEqualTo(1);
        assertThat(root.get("jobs").get(0).get("title").asText()).containsIgnoringCase("Backend Engineer");

        // Verify sourcesSearched includes company_ats:fixture-india-tech and manual
        String searchedStr = root.get("sourcesSearched").toString();
        assertThat(searchedStr).contains("company_ats:fixture-india-tech");
        assertThat(searchedStr).contains("manual");

        // Verify sourcesFailed includes timeout-board, error-board, and naukri
        String failedStr = root.get("sourcesFailed").toString();
        assertThat(failedStr).contains("company_ats:timeout-board");
        assertThat(failedStr).contains("Timed out after 150ms");
        assertThat(failedStr).contains("company_ats:error-board");
        assertThat(failedStr).contains("naukri");
        assertThat(failedStr).contains("no verified API access");

        // Verify coverage disclaimer is always present and never claims full market coverage
        assertThat(root.get("coverageDisclaimer").asText())
                .contains("never claim full market coverage");
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "SecurePass123!",
                                "fullName", "Search Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }
}

