package in.skillsignal.eligibility;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class Stage20StrictEligibilityAnd40FixturesTest {

    @Autowired
    private StrictEligibilityService eligibilityService;

    record IndustryFixture(
            String industry,
            double expBandYears,
            List<String> candidateSkills,
            List<String> jobSkills3Match,
            List<String> jobSkills2Match
    ) {}

    @Test
    void strictRule_exactly2MatchesFailsAndExactly3MatchesPasses_across44Fixtures8IndustriesAnd4ExpBands() {
        List<IndustryFixture> baseIndustries = List.of(
                new IndustryFixture(
                        "Software Engineering", 0.0,
                        List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                        List.of("Java", "Spring Boot", "PostgreSQL", "Kubernetes"),
                        List.of("Java", "Spring Boot", "Kubernetes", "Terraform")
                ),
                new IndustryFixture(
                        "Data & AI", 2.0,
                        List.of("Python", "SQL", "Machine Learning", "PyTorch"),
                        List.of("Python", "SQL", "PyTorch", "Spark"),
                        List.of("Python", "SQL", "Spark", "Hadoop")
                ),
                new IndustryFixture(
                        "Healthcare & Nursing", 5.0,
                        List.of("Patient Care", "ICU", "Triage", "Phlebotomy"),
                        List.of("Patient Care", "ICU", "Triage", "Radiology"),
                        List.of("Patient Care", "ICU", "Pediatrics", "Oncology")
                ),
                new IndustryFixture(
                        "Finance & Accounting", 12.0,
                        List.of("Financial Modeling", "GST", "Tally", "Auditing"),
                        List.of("Financial Modeling", "GST", "Tally", "Taxation"),
                        List.of("Financial Modeling", "GST", "Actuarial Science", "Risk Management")
                ),
                new IndustryFixture(
                        "Sales & Marketing", 0.0,
                        List.of("B2B Sales", "CRM", "Lead Generation", "SEO"),
                        List.of("B2B Sales", "CRM", "Lead Generation", "Public Relations"),
                        List.of("B2B Sales", "CRM", "Public Relations", "Brand Management")
                ),
                new IndustryFixture(
                        "Teaching & Education", 2.0,
                        List.of("Curriculum Design", "Lesson Planning", "Classroom Management", "Mathematics"),
                        List.of("Curriculum Design", "Lesson Planning", "Classroom Management", "Chemistry"),
                        List.of("Curriculum Design", "Lesson Planning", "Special Education", "Chemistry")
                ),
                new IndustryFixture(
                        "Design & UX", 5.0,
                        List.of("Figma", "Wireframing", "User Research", "Prototyping"),
                        List.of("Figma", "Wireframing", "User Research", "3D Modeling"),
                        List.of("Figma", "Wireframing", "Motion Graphics", "3D Modeling")
                ),
                new IndustryFixture(
                        "Core Engineering & Logistics", 12.0,
                        List.of("Supply Chain Management", "Inventory Control", "AutoCAD", "Six Sigma"),
                        List.of("Supply Chain Management", "Inventory Control", "AutoCAD", "Thermodynamics"),
                        List.of("Supply Chain Management", "Inventory Control", "Thermodynamics", "Robotics")
                )
        );

        double[] expBands = new double[]{0.0, 2.0, 5.0, 12.0};
        AutoApplyPreference pref = new AutoApplyPreference();
        pref.setMinMatchedSkills(3);
        pref.setRequire50PercentSkillCoverage(true);

        int fixtureCount = 0;

        // 8 industries x 4 experience bands = 32 pass fixtures (3/4 matched = 75%) + 12 boundary/2-match fail fixtures = 44 fixtures
        for (IndustryFixture ind : baseIndustries) {
            for (double band : expBands) {
                // Case A: 3 of 4 skills match (75% >= 50%, 3 >= MIN_SKILLS=3) and experience within [band, band+2] -> MUST PASS
                var passInput = new StrictEligibilityService.EvaluationInput(
                        ind.candidateSkills(),
                        band,
                        true,
                        ind.jobSkills3Match(),
                        band,
                        band + 2.0,
                        ind.industry() + " Specialist",
                        ind.industry() + " Corp",
                        "company_ats",
                        "India",
                        "Role in " + ind.industry(),
                        true,
                        true,
                        true,
                        true,
                        1,
                        5,
                        85,
                        true,
                        pref,
                        StrictEligibilityService.ExclusionListsDto.empty(),
                        List.of("manual", "company_ats")
                );
                var passResult = eligibilityService.evaluateCandidateAgainstJob(passInput);
                assertThat(passResult.matchedSkillsCount()).isEqualTo(3);
                assertThat(passResult.eligible())
                        .as("Industry %s at %.0f yrs with 3/4 matched skills must pass", ind.industry(), band)
                        .isTrue();
                assertThat(passResult.routesToReviewQueue()).isFalse();
                fixtureCount++;
            }

            // Case B: Exactly 2 of 4 skills match (2 < MIN_SKILLS=3) -> MUST FAIL and route to Review Queue
            var fail2MatchInput = new StrictEligibilityService.EvaluationInput(
                    ind.candidateSkills(),
                    ind.expBandYears(),
                    true,
                    ind.jobSkills2Match(),
                    ind.expBandYears(),
                    ind.expBandYears() + 3.0,
                    ind.industry() + " Role",
                    ind.industry() + " Corp",
                    "company_ats",
                    "India",
                    "Role in " + ind.industry(),
                    true,
                    true,
                    true,
                    true,
                    1,
                    5,
                    85,
                    true,
                    pref,
                    StrictEligibilityService.ExclusionListsDto.empty(),
                    List.of("manual", "company_ats")
            );
            var fail2Result = eligibilityService.evaluateCandidateAgainstJob(fail2MatchInput);
            assertThat(fail2Result.matchedSkillsCount()).isEqualTo(2);
            assertThat(fail2Result.eligible())
                    .as("Industry %s with exactly 2 matched skills must fail when MIN_SKILLS=3", ind.industry())
                    .isFalse();
            assertThat(fail2Result.routesToReviewQueue()).isTrue();
            assertThat(fail2Result.whyAppliedOrSkipped()).contains("FAIL_BELOW_MIN_SKILLS");
            fixtureCount++;
        }

        // Additional 4 edge-case fixtures to reach 44 total fixtures:
        // Fixture 41: Job lists fewer than MIN_SKILLS (only 2 skills listed) and ALL 2 match -> PASSES
        var twoListedAllMatch = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java", "Spring Boot"), 2.0, true,
                List.of("Java", "Spring Boot"), 1.0, 4.0,
                "Java Dev", "Acme", "company_ats", "India", "Java role",
                true, true, true, true, 0, 0, 90, true, pref,
                StrictEligibilityService.ExclusionListsDto.empty(), List.of("company_ats")
        ));
        assertThat(twoListedAllMatch.eligible()).isTrue();
        fixtureCount++;

        // Fixture 42: Job lists fewer than MIN_SKILLS (2 skills listed) and only 1 matches -> FAILS
        var twoListedOneMatch = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java"), 2.0, true,
                List.of("Java", "Spring Boot"), 1.0, 4.0,
                "Java Dev", "Acme", "company_ats", "India", "Java role",
                true, true, true, true, 0, 0, 90, true, pref,
                StrictEligibilityService.ExclusionListsDto.empty(), List.of("company_ats")
        ));
        assertThat(twoListedOneMatch.eligible()).isFalse();
        assertThat(twoListedOneMatch.routesToReviewQueue()).isTrue();
        fixtureCount++;

        // Fixture 43: Job lists 0 skills -> routes to Review Queue
        var zeroSkillsListed = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java", "Spring Boot", "PostgreSQL"), 5.0, true,
                List.of(), 3.0, 8.0,
                "Generalist", "Acme", "company_ats", "India", "General role",
                true, true, true, true, 0, 0, 90, true, pref,
                StrictEligibilityService.ExclusionListsDto.empty(), List.of("company_ats")
        ));
        assertThat(zeroSkillsListed.eligible()).isFalse();
        assertThat(zeroSkillsListed.skillRuleOutcome()).contains("NO_SKILLS_LISTED_REVIEW");
        fixtureCount++;

        // Fixture 44: Over-qualified candidate (12 yrs > max 6 yrs with default BLOCK policy) -> FAILS
        var overQualified = eligibilityService.evaluateCandidateAgainstJob(new StrictEligibilityService.EvaluationInput(
                List.of("Java", "Spring Boot", "PostgreSQL"), 12.0, true,
                List.of("Java", "Spring Boot", "PostgreSQL"), 2.0, 6.0,
                "Mid-Level Engineer", "Acme", "company_ats", "India", "Mid-level role",
                true, true, true, true, 0, 0, 90, true, pref,
                StrictEligibilityService.ExclusionListsDto.empty(), List.of("company_ats")
        ));
        assertThat(overQualified.eligible()).isFalse();
        assertThat(overQualified.experienceRuleOutcome()).contains("OVER_QUALIFIED_BLOCKED");
        fixtureCount++;

        assertThat(fixtureCount).isEqualTo(44);
    }
}

