package in.skillsignal.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.ai.AiClient;
import in.skillsignal.ai.ParsedJobData;
import in.skillsignal.skill.Skill;
import in.skillsignal.skill.SkillRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NaturalLanguageQueryParser {

    private final SkillRepository skillRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> CITY_ALIASES = createCityAliases();
    private static final Map<String, String> BUILTIN_SKILL_ALIASES = createBuiltinSkillAliases();
    private static final List<String> ROLE_PHRASES = List.of(
            "software engineer", "backend developer", "backend engineer", "frontend developer", "frontend engineer",
            "full stack developer", "full-stack developer", "fullstack developer", "data scientist", "data analyst",
            "data engineer", "machine learning engineer", "devops engineer", "site reliability engineer",
            "qa engineer", "automation tester", "product manager", "product designer", "ux designer", "ui/ux designer",
            "registered nurse", "icu nurse", "clinical pharmacist", "medical coder", "healthcare administrator",
            "chartered accountant", "financial analyst", "investment banker", "tax consultant", "risk analyst",
            "audit manager", "sales manager", "business development executive", "account executive",
            "digital marketing manager", "seo specialist", "physics teacher", "mathematics teacher",
            "curriculum designer", "instructional designer", "supply chain manager", "logistics coordinator",
            "procurement specialist", "operations manager", "mechanical engineer", "civil engineer",
            "electrical engineer", "corporate lawyer", "legal counsel", "compliance officer", "hr generalist",
            "talent acquisition specialist"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "jobs", "job", "roles", "role", "openings", "opening", "positions", "position", "in", "at", "for",
            "with", "and", "or", "the", "a", "an", "to", "of", "from", "looking", "find", "search", "show", "me",
            "only", "having", "minimum", "min", "max", "maximum", "above", "over", "under", "below", "years",
            "year", "yrs", "yr", "exp", "experience", "lpa", "lakhs", "lakh", "salary", "disclosed", "posted",
            "last", "past", "within", "days", "day", "week", "month", "hours", "today", "remote", "hybrid",
            "onsite", "on-site", "wfh", "wfo", "office", "home", "work", "fresher", "freshers", "entry", "level",
            "senior", "full", "time", "full-time", "part", "part-time", "contract", "internship", "intern",
            "saved", "bookmarked", "applied", "unapplied", "not", "already", "source", "greenhouse", "manual",
            "company_ats", "email_apply"
    );

    public NaturalLanguageQueryParser(SkillRepository skillRepository, AiClient aiClient, ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public record ParseQueryResult(JobSearchFilters filters, List<JobSearchFilters.FilterChip> chips) {}

    public ParseQueryResult parse(String query) {
        JobSearchFilters filters = new JobSearchFilters();
        if (query == null || query.isBlank()) {
            return new ParseQueryResult(filters, List.of());
        }

        String trimmed = query.trim();
        filters.setRawQuery(trimmed);
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // 1. Work Mode
        if (containsAnyWord(lower, "remote", "wfh") || lower.contains("work from home")) {
            filters.setWorkMode("REMOTE");
        } else if (containsAnyWord(lower, "hybrid")) {
            filters.setWorkMode("HYBRID");
        } else if (containsAnyWord(lower, "onsite", "on-site", "wfo", "in-office") || lower.contains("work from office")) {
            filters.setWorkMode("ONSITE");
        }

        // 2. Employment Type
        if (containsAnyWord(lower, "internship", "intern", "internships")) {
            filters.setEmploymentType("INTERNSHIP");
        } else if (containsAnyWord(lower, "contract", "freelance", "consultant")) {
            filters.setEmploymentType("CONTRACT");
        } else if (lower.contains("part-time") || lower.contains("part time")) {
            filters.setEmploymentType("PART_TIME");
        } else if (lower.contains("full-time") || lower.contains("full time") || containsAnyWord(lower, "permanent")) {
            filters.setEmploymentType("FULL_TIME");
        }

        // 3. Experience
        Matcher rangeExp = Pattern.compile("\\b(\\d{1,2})\\s*(?:-|to)\\s*(\\d{1,2})\\s*(?:\\+?\\s*)?(?:years|year|yrs|yr)\\b").matcher(lower);
        Matcher plusExp = Pattern.compile("\\b(?:min(?:imum)?\\s+|at\\s+least\\s+|over\\s+|above\\s+)?(\\d{1,2})\\s*\\+?\\s*(?:years|year|yrs|yr)(?:\\s+of)?(?:\\s+exp(?:erience)?)?\\b").matcher(lower);
        if (rangeExp.find()) {
            filters.setMinExperienceYears(Integer.parseInt(rangeExp.group(1)));
            filters.setMaxExperienceYears(Integer.parseInt(rangeExp.group(2)));
        } else if (containsAnyWord(lower, "fresher", "freshers") || lower.contains("entry level") || lower.contains("entry-level") || lower.contains("new grad")) {
            filters.setMinExperienceYears(0);
            filters.setMaxExperienceYears(1);
        } else if (plusExp.find()) {
            filters.setMinExperienceYears(Integer.parseInt(plusExp.group(1)));
        } else if (containsAnyWord(lower, "senior", "lead", "principal")) {
            filters.setMinExperienceYears(5);
        }

        // 4. Salary (LPA / Disclosed)
        Matcher lpaMatcher = Pattern.compile("\\b(?:above\\s+|over\\s+|min(?:imum)?\\s+|at\\s+least\\s+)?(\\d{1,3}(?:\\.\\d)?)\\s*\\+?\\s*(?:lpa|lakhs?|lac)\\b").matcher(lower);
        if (lpaMatcher.find()) {
            filters.setMinSalaryLpa(Double.parseDouble(lpaMatcher.group(1)));
            filters.setDisclosedSalaryOnly(true);
        }
        if (lower.contains("disclosed salary") || lower.contains("salary disclosed") || lower.contains("with salary")) {
            filters.setDisclosedSalaryOnly(true);
        }

        // 5. Posted Date
        Matcher daysMatcher = Pattern.compile("\\b(?:last|past|within)\\s+(\\d{1,3})\\s+days?\\b").matcher(lower);
        if (daysMatcher.find()) {
            filters.setPostedWithinDays(Integer.parseInt(daysMatcher.group(1)));
        } else if (lower.contains("last 24 hours") || lower.contains("past 24 hours") || containsAnyWord(lower, "today")) {
            filters.setPostedWithinDays(1);
        } else if (lower.contains("this week") || lower.contains("past week") || lower.contains("last week")) {
            filters.setPostedWithinDays(7);
        } else if (lower.contains("this month") || lower.contains("past month") || lower.contains("last month")) {
            filters.setPostedWithinDays(30);
        }

        // 6. Saved / Applied status
        if (containsAnyWord(lower, "saved", "bookmarked")) {
            filters.setSavedOnly(true);
        }
        if (lower.contains("not applied") || containsAnyWord(lower, "unapplied")) {
            filters.setAppliedStatus("NOT_APPLIED");
        } else if (lower.contains("already applied") || containsAnyWord(lower, "applied")) {
            filters.setAppliedStatus("APPLIED");
        }

        // 7. Sources
        List<String> sources = new ArrayList<>();
        if (containsAnyWord(lower, "greenhouse", "company_ats") || lower.contains("company career") || lower.contains("public board")) {
            sources.add("company_ats");
        }
        if (containsAnyWord(lower, "manual")) {
            sources.add("manual");
        }
        if (containsAnyWord(lower, "email_apply")) {
            sources.add("email_apply");
        }
        filters.setSources(sources);

        // 8. Company extraction ("at <CompanyName>")
        Matcher companyMatcher = Pattern.compile("\\bat\\s+([A-Z][A-Za-z0-9&.-]+(?:\\s+[A-Z][A-Za-z0-9&.-]+)?)").matcher(trimmed);
        if (companyMatcher.find()) {
            String candidate = companyMatcher.group(1).trim();
            String candidateLower = candidate.toLowerCase(Locale.ROOT);
            if (!CITY_ALIASES.containsKey(candidateLower) && !candidateLower.startsWith("least")) {
                filters.setCompany(candidate);
            }
        }

        // 9. Locations
        Set<String> matchedLocations = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : CITY_ALIASES.entrySet()) {
            if (containsPhrase(lower, entry.getKey())) {
                matchedLocations.add(entry.getValue());
            }
        }
        filters.setLocations(new ArrayList<>(matchedLocations));

        // 10. Skills (Database catalog + built-in fallback)
        Set<String> matchedSkills = new LinkedHashSet<>();
        Map<String, String> skillLookup = buildSkillLookupMap();
        for (Map.Entry<String, String> entry : skillLookup.entrySet()) {
            if (containsSkillToken(lower, entry.getKey())) {
                matchedSkills.add(entry.getValue());
            }
        }
        filters.setSkills(new ArrayList<>(matchedSkills));

        // 11. Role / Domain Keywords
        Set<String> keywords = new LinkedHashSet<>();
        for (String phrase : ROLE_PHRASES) {
            if (lower.contains(phrase)) {
                keywords.add(phrase);
            }
        }
        if (keywords.isEmpty()) {
            // Extract remaining non-stopword tokens that are not already in locations, skills, or company
            Set<String> coveredTokens = new HashSet<>(STOP_WORDS);
            matchedLocations.forEach(loc -> coveredTokens.addAll(Arrays.asList(loc.toLowerCase(Locale.ROOT).split("\\s+"))));
            CITY_ALIASES.keySet().forEach(alias -> {
                if (containsPhrase(lower, alias)) {
                    coveredTokens.addAll(Arrays.asList(alias.split("\\s+")));
                }
            });
            matchedSkills.forEach(sk -> coveredTokens.addAll(Arrays.asList(sk.toLowerCase(Locale.ROOT).split("[\\s/.-]+"))));
            if (filters.getCompany() != null) {
                coveredTokens.addAll(Arrays.asList(filters.getCompany().toLowerCase(Locale.ROOT).split("\\s+")));
            }
            for (String token : lower.split("[^a-z0-9+#]+")) {
                if (token.length() >= 3 && ! token.matches("\\d+") && !coveredTokens.contains(token)) {
                    keywords.add(token);
                }
            }
        }
        filters.setKeywords(new ArrayList<>(keywords));

        // 12. Optional AI Assist when rule-based extraction found no skills and no structured filters
        if (filters.getSkills().isEmpty() && filters.getLocations().isEmpty() && filters.getWorkMode() == null && aiClient != null) {
            try {
                ParsedJobData aiAssisted = aiClient.parseJobDescription(trimmed, null, filters.getCompany());
                if (aiAssisted != null) {
                    if (aiAssisted.requiredSkills() != null && !aiAssisted.requiredSkills().isEmpty()) {
                        filters.setSkills(new ArrayList<>(aiAssisted.requiredSkills()));
                    }
                    if (filters.getWorkMode() == null && aiAssisted.workMode() != null && !"UNSPECIFIED".equalsIgnoreCase(aiAssisted.workMode())) {
                        filters.setWorkMode(aiAssisted.workMode());
                    }
                }
            } catch (Exception ignored) {
                // Rule-based filters remain intact even if AI assist fails
            }
        }

        return new ParseQueryResult(filters, buildChips(filters));
    }

    public List<JobSearchFilters.FilterChip> buildChips(JobSearchFilters f) {
        List<JobSearchFilters.FilterChip> chips = new ArrayList<>();
        if (f == null) return chips;

        if (f.getKeywords() != null) {
            for (String kw : f.getKeywords()) {
                chips.add(new JobSearchFilters.FilterChip("keywords", "Keyword: " + kw, kw));
            }
        }
        if (f.getSkills() != null) {
            for (String skill : f.getSkills()) {
                chips.add(new JobSearchFilters.FilterChip("skills", "Skill: " + skill, skill));
            }
        }
        if (f.getMinExperienceYears() != null || f.getMaxExperienceYears() != null) {
            String expLabel;
            if (Objects.equals(f.getMinExperienceYears(), 0) && Objects.equals(f.getMaxExperienceYears(), 1)) {
                expLabel = "Experience: Fresher (0-1 yrs)";
            } else if (f.getMinExperienceYears() != null && f.getMaxExperienceYears() != null) {
                expLabel = "Experience: " + f.getMinExperienceYears() + "-" + f.getMaxExperienceYears() + " yrs";
            } else if (f.getMinExperienceYears() != null) {
                expLabel = "Experience: " + f.getMinExperienceYears() + "+ yrs";
            } else {
                expLabel = "Experience: up to " + f.getMaxExperienceYears() + " yrs";
            }
            chips.add(new JobSearchFilters.FilterChip("experience", expLabel,
                    (f.getMinExperienceYears() != null ? f.getMinExperienceYears() : 0) + "-" +
                            (f.getMaxExperienceYears() != null ? f.getMaxExperienceYears() : "")));
        }
        if (f.getLocations() != null) {
            for (String loc : f.getLocations()) {
                chips.add(new JobSearchFilters.FilterChip("locations", "Location: " + loc, loc));
            }
        }
        if (f.getWorkMode() != null && !f.getWorkMode().isBlank()) {
            chips.add(new JobSearchFilters.FilterChip("workMode", "Work Mode: " + f.getWorkMode(), f.getWorkMode()));
        }
        if (f.getMinSalaryLpa() != null) {
            chips.add(new JobSearchFilters.FilterChip("minSalaryLpa", "Salary: " + f.getMinSalaryLpa() + "+ LPA", String.valueOf(f.getMinSalaryLpa())));
        } else if (Boolean.TRUE.equals(f.getDisclosedSalaryOnly())) {
            chips.add(new JobSearchFilters.FilterChip("disclosedSalaryOnly", "Disclosed Salary Only", "true"));
        }
        if (f.getPostedWithinDays() != null) {
            chips.add(new JobSearchFilters.FilterChip("postedWithinDays", "Posted: Last " + f.getPostedWithinDays() + "d", String.valueOf(f.getPostedWithinDays())));
        }
        if (f.getCompany() != null && !f.getCompany().isBlank()) {
            chips.add(new JobSearchFilters.FilterChip("company", "Company: " + f.getCompany(), f.getCompany()));
        }
        if (f.getSources() != null) {
            for (String src : f.getSources()) {
                chips.add(new JobSearchFilters.FilterChip("sources", "Source: " + src, src));
            }
        }
        if (f.getEmploymentType() != null && !f.getEmploymentType().isBlank()) {
            chips.add(new JobSearchFilters.FilterChip("employmentType", "Type: " + f.getEmploymentType(), f.getEmploymentType()));
        }
        if (Boolean.TRUE.equals(f.getSavedOnly())) {
            chips.add(new JobSearchFilters.FilterChip("savedOnly", "Saved Jobs Only", "true"));
        }
        if (f.getAppliedStatus() != null && !f.getAppliedStatus().isBlank()) {
            chips.add(new JobSearchFilters.FilterChip("appliedStatus", "Applied: " + f.getAppliedStatus(), f.getAppliedStatus()));
        }
        return chips;
    }

    private Map<String, String> buildSkillLookupMap() {
        Map<String, String> map = new LinkedHashMap<>();
        if (skillRepository != null) {
            try {
                List<Skill> dbSkills = skillRepository.findAll();
                for (Skill skill : dbSkills) {
                    map.put(skill.getCanonicalName().toLowerCase(Locale.ROOT), skill.getCanonicalName());
                    if (skill.getAliasesJson() != null && !skill.getAliasesJson().isBlank()) {
                        List<String> aliases = objectMapper.readValue(skill.getAliasesJson(), new TypeReference<List<String>>() {});
                        for (String alias : aliases) {
                            if (alias != null && alias.length() >= 2) {
                                map.put(alias.toLowerCase(Locale.ROOT), skill.getCanonicalName());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        map.putAll(BUILTIN_SKILL_ALIASES);
        return map;
    }

    private static boolean containsAnyWord(String text, String... words) {
        for (String w : words) {
            if (containsPhrase(text, w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPhrase(String text, String phrase) {
        String regex = "(?<![a-z0-9])" + Pattern.quote(phrase.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])";
        return Pattern.compile(regex).matcher(text).find();
    }

    private static boolean containsSkillToken(String text, String token) {
        if (token.length() <= 1) return false;
        String regex = "(?<![a-z0-9+#.])" + Pattern.quote(token) + "(?![a-z0-9+#.])";
        return Pattern.compile(regex).matcher(text).find();
    }

    private static Map<String, String> createCityAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("bengaluru", "Bengaluru");
        m.put("bangalore", "Bengaluru");
        m.put("blr", "Bengaluru");
        m.put("hyderabad", "Hyderabad");
        m.put("pune", "Pune");
        m.put("mumbai", "Mumbai");
        m.put("bombay", "Mumbai");
        m.put("gurugram", "Gurugram");
        m.put("gurgaon", "Gurugram");
        m.put("noida", "Noida");
        m.put("new delhi", "Delhi NCR");
        m.put("delhi ncr", "Delhi NCR");
        m.put("delhi", "Delhi NCR");
        m.put("ncr", "Delhi NCR");
        m.put("chennai", "Chennai");
        m.put("madras", "Chennai");
        m.put("kolkata", "Kolkata");
        m.put("calcutta", "Kolkata");
        m.put("ahmedabad", "Ahmedabad");
        m.put("kochi", "Kochi");
        m.put("coimbatore", "Coimbatore");
        m.put("jaipur", "Jaipur");
        m.put("indore", "Indore");
        m.put("chandigarh", "Chandigarh");
        m.put("thiruvananthapuram", "Thiruvananthapuram");
        m.put("trivandrum", "Thiruvananthapuram");
        m.put("london", "London");
        m.put("singapore", "Singapore");
        m.put("dubai", "Dubai");
        m.put("new york", "New York");
        m.put("san francisco", "San Francisco");
        return m;
    }

    private static Map<String, String> createBuiltinSkillAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("java", "Java");
        m.put("spring boot", "Spring Boot");
        m.put("springboot", "Spring Boot");
        m.put("python", "Python");
        m.put("react", "React");
        m.put("reactjs", "React");
        m.put("typescript", "TypeScript");
        m.put("javascript", "JavaScript");
        m.put("node.js", "Node.js");
        m.put("nodejs", "Node.js");
        m.put("postgresql", "PostgreSQL");
        m.put("postgres", "PostgreSQL");
        m.put("sql", "SQL");
        m.put("docker", "Docker");
        m.put("kubernetes", "Kubernetes");
        m.put("k8s", "Kubernetes");
        m.put("aws", "AWS");
        m.put("kafka", "Apache Kafka");
        m.put("microservices", "Microservices");
        m.put("selenium", "Selenium");
        m.put("playwright", "Playwright");
        m.put("pandas", "Pandas");
        m.put("pytorch", "PyTorch");
        m.put("tensorflow", "TensorFlow");
        m.put("power bi", "Power BI");
        m.put("tableau", "Tableau");
        m.put("patient care", "Patient Care");
        m.put("icu care", "ICU Care");
        m.put("triage", "Emergency Triage");
        m.put("pharmacovigilance", "Pharmacovigilance");
        m.put("clinical research", "Clinical Research");
        m.put("electronic health records", "Electronic Health Records (EHR)");
        m.put("ehr", "Electronic Health Records (EHR)");
        m.put("financial modeling", "Financial Modeling");
        m.put("gaap", "GAAP");
        m.put("ifrs", "IFRS");
        m.put("gst", "GST Compliance");
        m.put("tally", "Tally ERP");
        m.put("financial auditing", "Financial Auditing");
        m.put("risk management", "Risk Management");
        m.put("figma", "Figma");
        m.put("user research", "User Research");
        m.put("wireframing", "Wireframing");
        m.put("supply chain management", "Supply Chain Management");
        m.put("inventory management", "Inventory Management");
        m.put("sap", "SAP ERP");
        m.put("contract drafting", "Contract Drafting");
        m.put("corporate law", "Corporate Law");
        m.put("legal research", "Legal Research");
        m.put("curriculum development", "Curriculum Development");
        m.put("classroom management", "Classroom Management");
        m.put("b2b sales", "B2B Sales");
        m.put("salesforce", "Salesforce CRM");
        m.put("seo", "Search Engine Optimization (SEO)");
        m.put("autocad", "AutoCAD");
        m.put("solidworks", "SolidWorks");
        return m;
    }
}

