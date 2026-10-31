package in.skillsignal.company;

import in.skillsignal.job.Job;
import in.skillsignal.job.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CompanyIntelligenceService {

    public static final Set<String> VALID_TIERS = Set.of("T1", "T2", "T3");
    public static final String MANDATORY_NOTE = "Listings are not hires — computed strictly from your own stored job listings.";

    public record CompanyDto(
            UUID id,
            String name,
            String tier,
            boolean priority,
            String website,
            String notes,
            int jobsLast30Days,
            int jobsLast60Days,
            int jobsLast90Days,
            int totalStoredJobs,
            List<String> roles,
            List<String> locations,
            String disclosedSalaryRange,
            String evidenceDisclaimer,
            Instant updatedAt
    ) {}

    public record HiringIntelligenceSummaryDto(
            int totalTrackedCompanies,
            int priorityCompaniesCount,
            int totalStoredJobsAcrossCompanies,
            String disclaimer,
            List<CompanyDto> companies
    ) {}

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;

    public CompanyIntelligenceService(
            CompanyRepository companyRepository,
            JobRepository jobRepository
    ) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public CompanyDto createOrUpdateCompany(
            UUID userId,
            String name,
            String tier,
            Boolean priority,
            String website,
            String notes
    ) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name is required");
        }
        String normTier = normalizeTier(tier != null ? tier : "T2");
        String trimmedName = name.trim();

        Company company = companyRepository.findByUserIdAndNameIgnoreCase(userId, trimmedName)
                .orElseGet(() -> {
                    Company c = new Company();
                    c.setUserId(userId);
                    c.setName(trimmedName);
                    return c;
                });

        company.setTier(normTier);
        if (priority != null) company.setPriority(priority);
        if (website != null) company.setWebsite(website.trim());
        if (notes != null) company.setNotes(notes.trim());
        company = companyRepository.save(company);

        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return buildCompanyDto(company, filterCompanyJobs(userJobs, company.getName()), Instant.now());
    }

    @Transactional
    public CompanyDto updateCompanyById(
            UUID userId,
            UUID id,
            String tier,
            Boolean priority,
            String website,
            String notes
    ) {
        Company company = companyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));

        if (tier != null && !tier.isBlank()) {
            company.setTier(normalizeTier(tier));
        }
        if (priority != null) {
            company.setPriority(priority);
        }
        if (website != null) {
            company.setWebsite(website.trim());
        }
        if (notes != null) {
            company.setNotes(notes.trim());
        }
        company = companyRepository.save(company);

        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return buildCompanyDto(company, filterCompanyJobs(userJobs, company.getName()), Instant.now());
    }

    @Transactional
    public void deleteCompany(UUID userId, UUID id) {
        Company company = companyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        companyRepository.delete(company);
    }

    @Transactional
    public List<CompanyDto> listCompanies(UUID userId, String tierFilter, Boolean priorityOnly) {
        syncCompaniesFromStoredJobs(userId);

        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Instant now = Instant.now();

        return companyRepository.findByUserIdOrderByPriorityDescNameAsc(userId).stream()
                .filter(c -> tierFilter == null || tierFilter.isBlank() || c.getTier().equalsIgnoreCase(tierFilter.trim()))
                .filter(c -> !Boolean.TRUE.equals(priorityOnly) || c.isPriority())
                .map(c -> buildCompanyDto(c, filterCompanyJobs(userJobs, c.getName()), now))
                .toList();
    }

    @Transactional
    public HiringIntelligenceSummaryDto getHiringIntelligence(UUID userId, String tierFilter, Boolean priorityOnly) {
        List<CompanyDto> dtos = listCompanies(userId, tierFilter, priorityOnly);
        int priorityCount = (int) dtos.stream().filter(CompanyDto::priority).count();
        int totalJobs = dtos.stream().mapToInt(CompanyDto::totalStoredJobs).sum();

        return new HiringIntelligenceSummaryDto(
                dtos.size(),
                priorityCount,
                totalJobs,
                MANDATORY_NOTE,
                dtos
        );
    }

    private void syncCompaniesFromStoredJobs(UUID userId) {
        List<Job> userJobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Job j : userJobs) {
            if (j.getCompany() != null && !j.getCompany().isBlank()) {
                String cName = j.getCompany().trim();
                if (companyRepository.findByUserIdAndNameIgnoreCase(userId, cName).isEmpty()) {
                    Company created = new Company();
                    created.setUserId(userId);
                    created.setName(cName);
                    created.setTier("T2");
                    created.setPriority(false);
                    companyRepository.save(created);
                }
            }
        }
    }

    private List<Job> filterCompanyJobs(List<Job> allUserJobs, String companyName) {
        return allUserJobs.stream()
                .filter(j -> j.getCompany() != null && j.getCompany().trim().equalsIgnoreCase(companyName.trim()))
                .toList();
    }

    private CompanyDto buildCompanyDto(Company company, List<Job> companyJobs, Instant now) {
        Instant cutoff30 = now.minus(30, ChronoUnit.DAYS);
        Instant cutoff60 = now.minus(60, ChronoUnit.DAYS);
        Instant cutoff90 = now.minus(90, ChronoUnit.DAYS);

        int last30 = 0;
        int last60 = 0;
        int last90 = 0;

        Set<String> roles = new LinkedHashSet<>();
        Set<String> locations = new LinkedHashSet<>();
        BigDecimal minSal = null;
        BigDecimal maxSal = null;
        String currency = "INR";
        String period = "LPA";
        int disclosedCount = 0;

        for (Job j : companyJobs) {
            Instant created = j.getCreatedAt() != null ? j.getCreatedAt() : now;
            if (!created.isBefore(cutoff30)) last30++;
            if (!created.isBefore(cutoff60)) last60++;
            if (!created.isBefore(cutoff90)) last90++;

            if (j.getTitle() != null && !j.getTitle().isBlank()) {
                roles.add(j.getTitle().trim());
            }
            if (j.getLocation() != null && !j.getLocation().isBlank()) {
                locations.add(j.getLocation().trim());
            }
            if (j.getSalaryMin() != null || j.getSalaryMax() != null) {
                disclosedCount++;
                if (j.getSalaryCurrency() != null && !j.getSalaryCurrency().isBlank()) {
                    currency = j.getSalaryCurrency();
                }
                if (j.getSalaryPeriod() != null && !j.getSalaryPeriod().isBlank()) {
                    period = j.getSalaryPeriod();
                }
                BigDecimal jobMin = j.getSalaryMin() != null ? BigDecimal.valueOf(j.getSalaryMin()) : BigDecimal.valueOf(j.getSalaryMax());
                BigDecimal jobMax = j.getSalaryMax() != null ? BigDecimal.valueOf(j.getSalaryMax()) : BigDecimal.valueOf(j.getSalaryMin());
                if (minSal == null || jobMin.compareTo(minSal) < 0) minSal = jobMin;
                if (maxSal == null || jobMax.compareTo(maxSal) > 0) maxSal = jobMax;
            }
        }

        String salaryRange = disclosedCount > 0 && minSal != null && maxSal != null
                ? (minSal.stripTrailingZeros().toPlainString() + " - "
                    + maxSal.stripTrailingZeros().toPlainString() + " " + currency + " (" + period + ", from "
                    + disclosedCount + " stored listing(s))")
                : "not enough data";

        return new CompanyDto(
                company.getId(),
                company.getName(),
                company.getTier(),
                company.isPriority(),
                company.getWebsite(),
                company.getNotes(),
                last30,
                last60,
                last90,
                companyJobs.size(),
                new ArrayList<>(roles),
                new ArrayList<>(locations),
                salaryRange,
                MANDATORY_NOTE,
                company.getUpdatedAt()
        );
    }

    private String normalizeTier(String tier) {
        String norm = tier.trim().toUpperCase(Locale.ROOT);
        if (!VALID_TIERS.contains(norm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid company tier (allowed: T1, T2, T3): " + tier);
        }
        return norm;
    }
}
