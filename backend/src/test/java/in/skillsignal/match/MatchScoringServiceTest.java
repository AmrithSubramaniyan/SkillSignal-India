package in.skillsignal.match;

import in.skillsignal.SkillSignalApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SkillSignalApplication.class)
@ActiveProfiles("test")
class MatchScoringServiceTest {

    @Autowired
    private MatchScoringService matchScoringService;

    @Test
    void handlesZeroSkillsAndUnstatedExperienceWithoutDivisionByZero() {
        MatchResultDto res = matchScoringService.computeMatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(),
                0.0,
                List.of(), // empty required skills -> no division by zero (60)
                List.of(), // empty nice-to-have -> full 10
                null,      // unstated experience -> 10
                "Bengaluru",
                "HYBRID",
                List.of("Bengaluru"),
                List.of("HYBRID")
        );

        assertThat(res.requiredSkillsScore()).isEqualTo(60);
        assertThat(res.niceToHaveScore()).isEqualTo(10);
        assertThat(res.experienceScore()).isEqualTo(10);
        assertThat(res.locationScore()).isEqualTo(10);
        assertThat(res.score()).isEqualTo(90);
        assertThat(res.disclaimer()).isEqualTo("This score estimates relevance. It does not guarantee an interview or selection.");
        assertThat(res.suggestions()).hasSize(3);
    }

    @Test
    void normalizesSynonymsCaseAndPunctuationAndScalesExperience() {
        // Candidate has "reactjs", "spring-boot", "psql" -> should match "React", "Spring Boot", "PostgreSQL"
        MatchResultDto res = matchScoringService.computeMatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of("reactjs", "spring-boot", "psql"),
                2.0, // 2 years vs 4 years required -> scaled to 10/20
                List.of("React.js", "Spring Boot", "PostgreSQL", "Kubernetes"), // 3/4 matched -> 45/60
                List.of(), // none listed -> 10/10
                4.0,
                "Bangalore", // alias for Bengaluru
                "HYBRID",
                List.of("Bengaluru"),
                List.of("HYBRID")
        );

        assertThat(res.matchedRequired()).containsExactlyInAnyOrder("React", "Spring Boot", "PostgreSQL");
        assertThat(res.missingRequired()).containsExactly("Kubernetes");
        assertThat(res.requiredSkillsScore()).isEqualTo(45);
        assertThat(res.niceToHaveScore()).isEqualTo(10);
        assertThat(res.experienceScore()).isEqualTo(10);
        assertThat(res.locationScore()).isEqualTo(10);
        assertThat(res.score()).isEqualTo(75);
        assertThat(res.suggestions().get(0)).contains("If you have genuine prior experience");
    }
}

