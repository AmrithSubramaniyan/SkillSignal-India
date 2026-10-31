package in.skillsignal.skill;

import in.skillsignal.SkillSignalApplication;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = SkillSignalApplication.class)
@ActiveProfiles("test")
class Stage2DatabaseTest {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    void skillsAreSeededWithRoughly300CrossIndustryEntries() {
        long count = skillRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(300);
        assertThat(skillRepository.findByCanonicalNameIgnoreCase("Spring Boot")).isPresent();
        assertThat(skillRepository.findByCanonicalNameIgnoreCase("Patient Care")).isPresent();
        assertThat(skillRepository.findByCanonicalNameIgnoreCase("GST Compliance")).isPresent();
        assertThat(skillRepository.findByCanonicalNameIgnoreCase("Curriculum Development")).isPresent();
    }

    @Test
    void applicationsEnforceUniqueUserIdAndJobId() {
        User u = new User();
        u.setEmail("dbtest@skillsignal.test");
        u.setPasswordHash("hash");
        u.setFullName("DB Test");
        u = userRepository.save(u);

        Job j = new Job();
        j.setUserId(u.getId());
        j.setTitle("Software Engineer");
        j.setCompany("Acme India");
        j.setRawDescription("Java developer");
        j.setParsedJson("{}");
        j.setRequiredSkillsJson("[\"Java\"]");
        j.setNiceToHaveSkillsJson("[]");
        j = jobRepository.save(j);

        Application a1 = new Application();
        a1.setUserId(u.getId());
        a1.setJobId(j.getId());
        applicationRepository.saveAndFlush(a1);

        Application a2 = new Application();
        a2.setUserId(u.getId());
        a2.setJobId(j.getId());
        assertThatThrownBy(() -> applicationRepository.saveAndFlush(a2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
