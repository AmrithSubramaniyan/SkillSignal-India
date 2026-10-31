package in.skillsignal.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.SkillSignalApplication;
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

@SpringBootTest(classes = SkillSignalApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage9SourceRegistryAndConnectorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SourceCapabilityRepository capabilityRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void capabilitiesRegistrySeedsAllRowsAndProtectsDisabledPortalsAndSyncsPublicBoard() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "stage9.admin@skillsignal.test",
                                "password", "Stage9Pass123!",
                                "fullName", "Stage 9 Admin"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String userToken = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // 1. GET /api/sources/capabilities returns all 9 rows
        MvcResult capRes = mockMvc.perform(get("/api/sources/capabilities")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode caps = objectMapper.readTree(capRes.getResponse().getContentAsString());
        assertThat(caps.size()).isGreaterThanOrEqualTo(9);

        // Verify disabled portals are marked "no verified API access" and ASSISTED_APPLY
        for (String portal : List.of("naukri", "linkedin", "indeed", "foundit", "instahyre", "wellfound")) {
            SourceCapability cap = capabilityRepository.findBySourceIgnoreCase(portal).orElseThrow();
            assertThat(cap.isEnabled()).isFalse();
            assertThat(cap.getStatusNote()).isEqualTo("no verified API access");
            assertThat(cap.getAllowedActions()).isEqualTo("ASSISTED_APPLY");
        }

        // 2. Non-admin user cannot toggle capabilities (403)
        mockMvc.perform(put("/api/sources/capabilities/email_apply/toggle")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isForbidden());

        // Promote user to ADMIN and verify Admin switch works for permitted source, but blocks unverified portal
        User adminUser = userRepository.findByEmailIgnoreCase("stage9.admin@skillsignal.test").orElseThrow();
        adminUser.setRole("ADMIN");
        userRepository.save(adminUser);

        mockMvc.perform(put("/api/sources/capabilities/naukri/toggle")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isBadRequest());

        // 3. Sync public company_ats board (Greenhouse public board connector)
        mockMvc.perform(post("/api/sources/company-ats/sync")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("boardToken", "demo-india"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("company_ats"))
                .andExpect(jsonPath("$.importedCount").value(3))
                .andExpect(jsonPath("$.allowedActions").value("ASSISTED_APPLY"));
    }
}

