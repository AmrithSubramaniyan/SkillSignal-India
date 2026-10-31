package in.skillsignal.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.audit.AuditService;
import in.skillsignal.security.AuthenticatedUser;
import in.skillsignal.skill.Skill;
import in.skillsignal.skill.SkillRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CareerProfileController {

    public record CareerProfileDto(
            List<String> targetTitles,
            @Min(0) @Max(60) double yearsOfExperience,
            List<String> cities,
            List<String> workModes,
            @Min(0) Double expectedSalary,
            String salaryCurrency,
            String salaryPeriod,
            @Min(0) @Max(365) Integer noticePeriodDays,
            List<String> skills,
            List<String> employmentTypes,
            String country,
            List<String> languages,
            String timeZone,
            Instant updatedAt
    ) {}

    public record SkillSummaryDto(String canonicalName, String category) {}

    private final CareerProfileRepository profileRepository;
    private final SkillRepository skillRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CareerProfileController(
            CareerProfileRepository profileRepository,
            SkillRepository skillRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.profileRepository = profileRepository;
        this.skillRepository = skillRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/profile")
    public CareerProfileDto getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        CareerProfile profile = profileRepository.findByUserId(principal.id())
                .orElseGet(() -> {
                    CareerProfile created = new CareerProfile();
                    created.setUserId(principal.id());
                    created.setTargetTitles("[]");
                    created.setYearsOfExperience(0.0);
                    created.setCities("[\"Bengaluru\",\"Mumbai\",\"Hyderabad\"]");
                    created.setWorkModes("[\"HYBRID\",\"REMOTE\"]");
                    created.setSalaryCurrency("INR");
                    created.setSalaryPeriod("LPA");
                    created.setSkills("[]");
                    created.setEmploymentTypes("[\"FULL_TIME\"]");
                    created.setCountry("India");
                    created.setLanguages("[\"English\",\"Hindi\"]");
                    created.setTimeZone("Asia/Kolkata");
                    return profileRepository.save(created);
                });
        return toDto(profile);
    }

    @PutMapping("/profile")
    public CareerProfileDto updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CareerProfileDto req
    ) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        CareerProfile profile = profileRepository.findByUserId(principal.id())
                .orElseGet(() -> {
                    CareerProfile created = new CareerProfile();
                    created.setUserId(principal.id());
                    return created;
                });

        profile.setTargetTitles(writeList(req.targetTitles()));
        profile.setYearsOfExperience(req.yearsOfExperience());
        profile.setCities(writeList(req.cities()));
        profile.setWorkModes(writeList(req.workModes()));
        profile.setExpectedSalary(req.expectedSalary());
        profile.setSalaryCurrency(req.salaryCurrency() != null && !req.salaryCurrency().isBlank() ? req.salaryCurrency().trim() : "INR");
        profile.setSalaryPeriod(req.salaryPeriod() != null && !req.salaryPeriod().isBlank() ? req.salaryPeriod().trim() : "LPA");
        profile.setNoticePeriodDays(req.noticePeriodDays());
        profile.setSkills(writeList(req.skills()));
        profile.setEmploymentTypes(writeList(req.employmentTypes()));
        profile.setCountry(req.country() != null && !req.country().isBlank() ? req.country().trim() : "India");
        profile.setLanguages(writeList(req.languages()));
        profile.setTimeZone(req.timeZone() != null && !req.timeZone().isBlank() ? req.timeZone().trim() : "Asia/Kolkata");

        profile = profileRepository.save(profile);
        auditService.log(principal.id(), "PROFILE_UPDATED", "CareerProfile", profile.getId().toString(),
                Map.of("country", profile.getCountry(), "yearsOfExperience", profile.getYearsOfExperience()));
        return toDto(profile);
    }

    @GetMapping("/skills")
    public List<SkillSummaryDto> listSkills() {
        return skillRepository.findAll().stream()
                .map(s -> new SkillSummaryDto(s.getCanonicalName(), s.getCategory()))
                .toList();
    }

    private CareerProfileDto toDto(CareerProfile p) {
        return new CareerProfileDto(
                readList(p.getTargetTitles()),
                p.getYearsOfExperience(),
                readList(p.getCities()),
                readList(p.getWorkModes()),
                p.getExpectedSalary(),
                p.getSalaryCurrency(),
                p.getSalaryPeriod(),
                p.getNoticePeriodDays(),
                readList(p.getSkills()),
                readList(p.getEmploymentTypes()),
                p.getCountry(),
                readList(p.getLanguages()),
                p.getTimeZone(),
                p.getUpdatedAt()
        );
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeList(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items != null ? items : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }
}

