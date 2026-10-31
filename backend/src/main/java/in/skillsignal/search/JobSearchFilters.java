package in.skillsignal.search;

import java.util.ArrayList;
import java.util.List;

public class JobSearchFilters {
    private String rawQuery;
    private List<String> keywords = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private Integer minExperienceYears;
    private Integer maxExperienceYears;
    private List<String> locations = new ArrayList<>();
    private String workMode; // REMOTE, HYBRID, ONSITE
    private Boolean disclosedSalaryOnly = false;
    private Double minSalaryLpa;
    private Integer postedWithinDays;
    private String company;
    private List<String> sources = new ArrayList<>();
    private String employmentType; // FULL_TIME, CONTRACT, INTERNSHIP, PART_TIME
    private Boolean savedOnly = false;
    private String appliedStatus; // APPLIED, NOT_APPLIED, ANY
    private List<String> greenhouseBoards = new ArrayList<>();
    private Long perSourceTimeoutMs;

    public record FilterChip(String key, String label, String value) {}

    public String getRawQuery() { return rawQuery; }
    public void setRawQuery(String rawQuery) { this.rawQuery = rawQuery; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>(); }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>(); }

    public Integer getMinExperienceYears() { return minExperienceYears; }
    public void setMinExperienceYears(Integer minExperienceYears) { this.minExperienceYears = minExperienceYears; }

    public Integer getMaxExperienceYears() { return maxExperienceYears; }
    public void setMaxExperienceYears(Integer maxExperienceYears) { this.maxExperienceYears = maxExperienceYears; }

    public List<String> getLocations() { return locations; }
    public void setLocations(List<String> locations) { this.locations = locations != null ? new ArrayList<>(locations) : new ArrayList<>(); }

    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }

    public Boolean getDisclosedSalaryOnly() { return disclosedSalaryOnly; }
    public void setDisclosedSalaryOnly(Boolean disclosedSalaryOnly) { this.disclosedSalaryOnly = disclosedSalaryOnly != null ? disclosedSalaryOnly : false; }

    public Double getMinSalaryLpa() { return minSalaryLpa; }
    public void setMinSalaryLpa(Double minSalaryLpa) { this.minSalaryLpa = minSalaryLpa; }

    public Integer getPostedWithinDays() { return postedWithinDays; }
    public void setPostedWithinDays(Integer postedWithinDays) { this.postedWithinDays = postedWithinDays; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources != null ? new ArrayList<>(sources) : new ArrayList<>(); }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public Boolean getSavedOnly() { return savedOnly; }
    public void setSavedOnly(Boolean savedOnly) { this.savedOnly = savedOnly != null ? savedOnly : false; }

    public String getAppliedStatus() { return appliedStatus; }
    public void setAppliedStatus(String appliedStatus) { this.appliedStatus = appliedStatus; }

    public List<String> getGreenhouseBoards() { return greenhouseBoards; }
    public void setGreenhouseBoards(List<String> greenhouseBoards) {
        this.greenhouseBoards = greenhouseBoards != null ? new ArrayList<>(greenhouseBoards) : new ArrayList<>();
    }

    public Long getPerSourceTimeoutMs() { return perSourceTimeoutMs; }
    public void setPerSourceTimeoutMs(Long perSourceTimeoutMs) { this.perSourceTimeoutMs = perSourceTimeoutMs; }
}

