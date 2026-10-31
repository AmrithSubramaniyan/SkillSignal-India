package in.skillsignal.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.ParsedResumeData;
import in.skillsignal.application.Application;
import in.skillsignal.application.ApplicationEvent;
import in.skillsignal.application.ApplicationEventRepository;
import in.skillsignal.application.ApplicationRepository;
import in.skillsignal.consent.ConsentRecord;
import in.skillsignal.consent.ConsentRecordRepository;
import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import in.skillsignal.match.JobMatch;
import in.skillsignal.match.JobMatchRepository;
import in.skillsignal.match.MatchResultDto;
import in.skillsignal.match.MatchScoringService;
import in.skillsignal.profile.CareerProfile;
import in.skillsignal.profile.CareerProfileRepository;
import in.skillsignal.resume.Resume;
import in.skillsignal.resume.ResumeRepository;
import in.skillsignal.resume.ResumeVersion;
import in.skillsignal.resume.ResumeVersionRepository;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@skillsignal.test";
    public static final String DEMO_PASSWORD = "DemoPass123!";

    private final boolean seedDemoEnabled;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CareerProfileRepository profileRepository;
    private final ConsentRecordRepository consentRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final MatchScoringService matchScoringService;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final ObjectMapper objectMapper;

    public DemoDataSeeder(
            @Value("${app.seed-demo:false}") boolean seedDemoEnabled,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CareerProfileRepository profileRepository,
            ConsentRecordRepository consentRepository,
            ResumeRepository resumeRepository,
            ResumeVersionRepository resumeVersionRepository,
            JobRepository jobRepository,
            JobMatchRepository jobMatchRepository,
            MatchScoringService matchScoringService,
            ApplicationRepository applicationRepository,
            ApplicationEventRepository applicationEventRepository,
            ObjectMapper objectMapper
    ) {
        this.seedDemoEnabled = seedDemoEnabled;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.profileRepository = profileRepository;
        this.consentRepository = consentRepository;
        this.resumeRepository = resumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.jobRepository = jobRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.matchScoringService = matchScoringService;
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (seedDemoEnabled) {
            seedDemoDataIfAbsent();
        }
    }

    @Transactional
    public void seedDemoDataIfAbsent() throws Exception {
        if (userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }

        // 1. Demo User
        User demo = new User();
        demo.setEmail(DEMO_EMAIL);
        demo.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        demo.setFullName("Ananya Rao (Demo)");
        demo.setRole("USER");
        demo = userRepository.save(demo);

        // 2. Career Profile
        List<String> preferredCities = List.of("Bengaluru", "Mumbai", "Hyderabad");
        List<String> preferredModes = List.of("HYBRID", "REMOTE");
        List<String> verifiedSkills = List.of(
                "Java", "Spring Boot", "PostgreSQL", "Docker", "REST API", "Git", "JUnit"
        );

        CareerProfile profile = new CareerProfile();
        profile.setUserId(demo.getId());
        profile.setTargetTitles(objectMapper.writeValueAsString(List.of("Senior Backend Engineer", "Java Microservices Developer")));
        profile.setYearsOfExperience(4.0);
        profile.setCities(objectMapper.writeValueAsString(preferredCities));
        profile.setWorkModes(objectMapper.writeValueAsString(preferredModes));
        profile.setExpectedSalary(22.0);
        profile.setSalaryCurrency("INR");
        profile.setSalaryPeriod("LPA");
        profile.setNoticePeriodDays(30);
        profile.setSkills(objectMapper.writeValueAsString(verifiedSkills));
        profile.setEmploymentTypes(objectMapper.writeValueAsString(List.of("FULL_TIME")));
        profile.setCountry("India");
        profile.setLanguages(objectMapper.writeValueAsString(List.of("English", "Hindi", "Kannada")));
        profile.setTimeZone("Asia/Kolkata");
        profileRepository.save(profile);

        // 3. Consent Record
        ConsentRecord consent = new ConsentRecord();
        consent.setUserId(demo.getId());
        consent.setConsentType("STORE_RESUME_ANSWERS");
        consent.setConsentVersion("v1.0");
        consent.setGranted(true);
        consent.setGrantedAt(Instant.now());
        consentRepository.save(consent);

        // 4. 1 Resume + 1 Default ResumeVersion
        Resume resume = new Resume();
        resume.setUserId(demo.getId());
        resume.setTitle("Ananya_Rao_Backend_Resume.pdf");
        resume.setDefault(true);
        resume.setCurrentVersionNumber(1);
        resume = resumeRepository.save(resume);

        String sampleResumeText =
                "Ananya Rao — Bengaluru, India\n" +
                "4 years of experience building backend services in Java 17, Spring Boot, PostgreSQL, REST API, Docker, Git, and JUnit.\n" +
                "Education: B.Tech in Information Technology, NITK Surathkal\n" +
                "- Designed UPI reconciliation service handling 50k daily transactions with Spring Boot and PostgreSQL.\n" +
                "- Containerized core APIs with Docker and automated JUnit integration suites.";

        ParsedResumeData parsedResume = new ParsedResumeData(
                verifiedSkills,
                4.0,
                List.of("B.Tech in Information Technology, NITK Surathkal"),
                List.of("Designed UPI reconciliation service handling 50k daily transactions with Spring Boot and PostgreSQL.")
        );

        ResumeVersion rv = new ResumeVersion();
        rv.setResumeId(resume.getId());
        rv.setUserId(demo.getId());
        rv.setVersionNumber(1);
        rv.setOriginalFilename("Ananya_Rao_Backend_Resume.pdf");
        rv.setContentType("application/pdf");
        rv.setFileSizeBytes(42_500L);
        rv.setStoragePath("./uploads/demo/ananya-resume-v1");
        rv.setExtractedText(sampleResumeText);
        rv.setParsedJson(objectMapper.writeValueAsString(parsedResume));
        rv.setVerifiedSkillsJson(objectMapper.writeValueAsString(verifiedSkills));
        rv.setYearsOfExperience(4.0);
        rv.setDefault(true);
        rv = resumeVersionRepository.save(rv);

        // 5. 4 Jobs of different match levels
        Job jobHigh = createAndMatchJob(
                demo.getId(), rv, preferredCities, preferredModes,
                "Senior Java Backend Engineer",
                "FinStack Payments India",
                "Bengaluru",
                "HYBRID",
                "https://jobs.finstack.example/senior-java",
                "Looking for a Senior Java Backend Engineer in Bengaluru (Hybrid) with 3.5+ years of experience in Java, Spring Boot, PostgreSQL, and REST API. Nice to have: Docker, JUnit.",
                List.of("Java", "Spring Boot", "PostgreSQL", "REST API"),
                List.of("Docker", "JUnit"),
                3.5
        );

        Job jobGood = createAndMatchJob(
                demo.getId(), rv, preferredCities, preferredModes,
                "Platform Microservices Developer",
                "CloudScale Technologies",
                "Hyderabad",
                "HYBRID",
                "https://careers.cloudscale.example/platform-dev",
                "Hiring Platform Microservices Developer in Hyderabad (Hybrid) with 4+ years of experience. Required: Java, Spring Boot, PostgreSQL, Kubernetes. Nice to have: Docker, Apache Kafka.",
                List.of("Java", "Spring Boot", "PostgreSQL", "Kubernetes"),
                List.of("Docker", "Apache Kafka"),
                4.0
        );

        Job jobPartial = createAndMatchJob(
                demo.getId(), rv, preferredCities, preferredModes,
                "Full-Stack Product Engineer (Java + React)",
                "BazaarCommerce Mumbai",
                "Mumbai",
                "HYBRID",
                "https://careers.bazaar.example/fullstack",
                "Full-Stack Engineer in Mumbai (Hybrid) with 5+ years of experience across Java, Spring Boot, React, and TypeScript. Nice to have: AWS, GraphQL.",
                List.of("Java", "Spring Boot", "React", "TypeScript"),
                List.of("AWS", "GraphQL"),
                5.0
        );

        Job jobLow = createAndMatchJob(
                demo.getId(), rv, preferredCities, preferredModes,
                "Clinical Research & Healthcare Analytics Lead",
                "CareFirst Multi-Specialty Hospitals",
                "Chennai",
                "ONSITE",
                "https://careers.carefirst.example/clinical-lead",
                "Seeking a Clinical Research Lead in Chennai (On-site) with 6+ years of experience in Clinical Research, Electronic Health Records, Patient Care, and Power BI.",
                List.of("Clinical Research", "Electronic Health Records", "Patient Care", "Power BI"),
                List.of("Pharmacovigilance"),
                6.0
        );

        // 6. 3 Applications with status history & follow-ups due
        createDemoApplication(
                demo.getId(), jobHigh.getId(), rv.getId(),
                "APPLIED_CONFIRMED",
                "Submitted tailored resume v1 through company portal; follow up with recruiter today.",
                LocalDate.now()
        );
        createDemoApplication(
                demo.getId(), jobGood.getId(), rv.getId(),
                "NEEDS_VERIFICATION",
                "Portal timed out after clicking final review step; awaiting email confirmation before marking submitted.",
                LocalDate.now().minusDays(1)
        );
        createDemoApplication(
                demo.getId(), jobPartial.getId(), rv.getId(),
                "SAVED",
                "Saved to review missing React/TypeScript requirements before deciding.",
                LocalDate.now().plusDays(4)
        );
    }

    private Job createAndMatchJob(
            java.util.UUID userId,
            ResumeVersion rv,
            List<String> preferredCities,
            List<String> preferredModes,
            String title,
            String company,
            String location,
            String workMode,
            String sourceUrl,
            String rawDesc,
            List<String> reqSkills,
            List<String> niceSkills,
            Double minYears
    ) throws Exception {
        Job job = new Job();
        job.setUserId(userId);
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setWorkMode(workMode);
        job.setSourceUrl(sourceUrl);
        job.setRawDescription(rawDesc);
        job.setParsedJson("{}");
        job.setRequiredSkillsJson(objectMapper.writeValueAsString(reqSkills));
        job.setNiceToHaveSkillsJson(objectMapper.writeValueAsString(niceSkills));
        job.setMinYearsExperience(minYears);
        job = jobRepository.save(job);

        List<String> verified = objectMapper.readValue(rv.getVerifiedSkillsJson(),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});

        MatchResultDto match = matchScoringService.computeMatch(
                job.getId(),
                rv.getId(),
                verified,
                rv.getYearsOfExperience(),
                reqSkills,
                niceSkills,
                minYears,
                location,
                workMode,
                preferredCities,
                preferredModes
        );

        JobMatch entity = new JobMatch();
        entity.setUserId(userId);
        entity.setJobId(job.getId());
        entity.setResumeVersionId(rv.getId());
        entity.setTotalScore(match.score());
        entity.setRequiredSkillsScore(match.requiredSkillsScore());
        entity.setNiceToHaveScore(match.niceToHaveScore());
        entity.setExperienceScore(match.experienceScore());
        entity.setLocationScore(match.locationScore());
        entity.setResultJson(objectMapper.writeValueAsString(match));
        jobMatchRepository.save(entity);

        return job;
    }

    private void createDemoApplication(
            java.util.UUID userId,
            java.util.UUID jobId,
            java.util.UUID resumeVersionId,
            String status,
            String notes,
            LocalDate followUpDate
    ) {
        Application app = new Application();
        app.setUserId(userId);
        app.setJobId(jobId);
        app.setResumeVersionId(resumeVersionId);
        app.setStatus(status);
        app.setApplyMode("ASSISTED");
        app.setNotes(notes);
        app.setFollowUpDate(followUpDate);
        if ("APPLIED_CONFIRMED".equals(status)) {
            app.setAppliedAt(Instant.now().minusSeconds(86400));
        }
        app = applicationRepository.save(app);

        ApplicationEvent ev1 = new ApplicationEvent();
        ev1.setApplicationId(app.getId());
        ev1.setUserId(userId);
        ev1.setFromStatus(null);
        ev1.setToStatus("SAVED");
        ev1.setNote("Saved job to tracker");
        applicationEventRepository.save(ev1);

        if (!"SAVED".equals(status)) {
            ApplicationEvent ev2 = new ApplicationEvent();
            ev2.setApplicationId(app.getId());
            ev2.setUserId(userId);
            ev2.setFromStatus("SAVED");
            ev2.setToStatus(status);
            ev2.setNote(notes);
            applicationEventRepository.save(ev2);
        }
    }
}

