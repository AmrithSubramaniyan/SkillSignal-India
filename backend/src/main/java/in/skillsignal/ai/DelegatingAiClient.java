package in.skillsignal.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.skillsignal.skill.SkillCatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DelegatingAiClient implements AiClient {

    private static final Pattern YEARS_PATTERN = Pattern.compile(
            "(?i)(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?(?:years?|yrs?)(?:\\s+of)?(?:\\s+experience)?"
    );
    private static final Pattern EDU_PATTERN = Pattern.compile(
            "(?im)^.*\\b(B\\.?Tech|M\\.?Tech|B\\.?E\\.?|M\\.?E\\.?|B\\.?Sc|M\\.?Sc|BCA|MCA|MBA|B\\.?Com|M\\.?Com|BBA|MBBS|MD|B\\.?Pharm|M\\.?Pharm|LLB|LLM|CA|CS|CFA|Ph\\.?D|Diploma|Bachelor|Master|University|College|Institute)\\b.*$"
    );
    private static final Pattern PROJECT_LINE_PATTERN = Pattern.compile(
            "(?im)^(?:\\s*[-•*]\\s*|\\s*Project\\s*[:\\-]\\s*)(.{10,140})$"
    );

    private final SkillCatalogService skillCatalogService;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Map<String, Object> sha256Cache = new ConcurrentHashMap<>();

    public DelegatingAiClient(
            SkillCatalogService skillCatalogService,
            ObjectMapper objectMapper,
            @Value("${app.ai.provider:mock}") String provider,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:claude-3-5-sonnet-latest}") String model,
            @Value("${app.ai.timeout-seconds:30}") int timeoutSeconds
    ) {
        this.skillCatalogService = skillCatalogService;
        this.objectMapper = objectMapper;
        this.provider = provider != null ? provider.trim().toLowerCase(Locale.ROOT) : "mock";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null ? model.trim() : "";
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public ParsedResumeData parseResume(String resumeText) {
        String bounded = enforceLimit(resumeText);
        String cacheKey = "RESUME:" + provider + ":" + sha256(bounded);
        Object cached = sha256Cache.get(cacheKey);
        if (cached instanceof ParsedResumeData data) {
            return data;
        }

        ParsedResumeData result;
        if ("anthropic".equals(provider) || "openai".equals(provider)) {
            result = callExternalProviderWithRetryForResume(bounded);
        } else {
            result = parseResumeDeterministic(bounded);
        }
        sha256Cache.put(cacheKey, result);
        return result;
    }

    @Override
    public ParsedJobData parseJobDescription(String jobText, String hintTitle, String hintCompany) {
        String bounded = enforceLimit(jobText);
        String cacheKey = "JOB:" + provider + ":" + sha256(bounded + "|" + hintTitle + "|" + hintCompany);
        Object cached = sha256Cache.get(cacheKey);
        if (cached instanceof ParsedJobData data) {
            return data;
        }

        ParsedJobData result;
        if ("anthropic".equals(provider) || "openai".equals(provider)) {
            result = callExternalProviderWithRetryForJob(bounded, hintTitle, hintCompany);
        } else {
            result = parseJobDeterministic(bounded, hintTitle, hintCompany);
        }
        sha256Cache.put(cacheKey, result);
        return result;
    }

    public String enforceLimit(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() > MAX_INPUT_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Input text exceeds the 15,000-character limit (" + text.length() + " characters provided)."
            );
        }
        return text;
    }

    private ParsedResumeData parseResumeDeterministic(String text) {
        List<String> skills = extractSkillsFromText(text);
        Double years = extractYears(text);

        List<String> education = new ArrayList<>();
        Matcher eduMatcher = EDU_PATTERN.matcher(text);
        while (eduMatcher.find() && education.size() < 6) {
            String line = eduMatcher.group(0).trim();
            if (!line.isEmpty() && !education.contains(line)) {
                education.add(line);
            }
        }

        List<String> projects = new ArrayList<>();
        Matcher projMatcher = PROJECT_LINE_PATTERN.matcher(text);
        while (projMatcher.find() && projects.size() < 6) {
            String line = projMatcher.group(1).trim();
            if (!line.isEmpty() && !projects.contains(line)) {
                projects.add(line);
            }
        }

        return new ParsedResumeData(skills, years, education, projects);
    }

    private ParsedJobData parseJobDeterministic(String text, String hintTitle, String hintCompany) {
        String lower = text.toLowerCase(Locale.ROOT);
        int niceIdx = findSectionIndex(lower, List.of(
                "nice to have", "nice-to-have", "preferred qualifications", "good to have", "bonus skills", "plus:"
        ));

        String requiredPart = niceIdx > 0 ? text.substring(0, niceIdx) : text;
        String nicePart = niceIdx > 0 ? text.substring(niceIdx) : "";

        List<String> requiredSkills = extractSkillsFromText(requiredPart);
        List<String> niceSkills = new ArrayList<>(extractSkillsFromText(nicePart));
        niceSkills.removeAll(requiredSkills);

        Double minYears = extractYears(requiredPart);
        String workMode = detectWorkMode(text);
        String location = detectLocation(text);

        String title = (hintTitle != null && !hintTitle.isBlank()) ? hintTitle.trim() : extractFirstLineTitle(text);
        String company = (hintCompany != null && !hintCompany.isBlank()) ? hintCompany.trim() : "Unspecified Company";

        return new ParsedJobData(title, company, location, workMode, requiredSkills, niceSkills, minYears);
    }

    private List<String> extractSkillsFromText(String text) {
        if (text == null || text.isBlank()) return List.of();
        Map<String, String> aliasMap = skillCatalogService.buildAliasToCanonicalMap();
        String normalizedText = " " + SkillCatalogService.normalizeRawToken(text) + " ";
        Set<String> matchedCanonical = new LinkedHashSet<>();

        List<Map.Entry<String, String>> entries = new ArrayList<>(aliasMap.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, String> entry : entries) {
            String token = entry.getKey();
            if (token.length() < 2) continue;
            if (containsWholeToken(normalizedText, token)) {
                matchedCanonical.add(entry.getValue());
            }
        }
        return new ArrayList<>(matchedCanonical);
    }

    private boolean containsWholeToken(String haystackWithSpaces, String token) {
        int idx = haystackWithSpaces.indexOf(token);
        while (idx != -1) {
            char before = idx > 0 ? haystackWithSpaces.charAt(idx - 1) : ' ';
            int endIdx = idx + token.length();
            char after = endIdx < haystackWithSpaces.length() ? haystackWithSpaces.charAt(endIdx) : ' ';
            if (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after)) {
                return true;
            }
            idx = haystackWithSpaces.indexOf(token, idx + 1);
        }
        return false;
    }

    private Double extractYears(String text) {
        Matcher m = YEARS_PATTERN.matcher(text);
        Double found = null;
        while (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                if (val >= 0 && val <= 45) {
                    if (found == null || val > found) {
                        found = val;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (found == null && text.toLowerCase(Locale.ROOT).contains("fresher")) {
            return 0.0;
        }
        return found;
    }

    private String detectWorkMode(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("remote") || lower.contains("work from home") || lower.contains("wfh")) {
            if (lower.contains("hybrid")) return "HYBRID";
            return "REMOTE";
        }
        if (lower.contains("hybrid")) return "HYBRID";
        if (lower.contains("on-site") || lower.contains("onsite") || lower.contains("work from office")) {
            return "ONSITE";
        }
        return "ONSITE";
    }

    private String detectLocation(String text) {
        List<String> knownLocations = List.of(
                "Bengaluru", "Bangalore", "Mumbai", "Hyderabad", "Pune", "Chennai",
                "Gurugram", "Gurgaon", "Noida", "Delhi", "Kolkata", "Ahmedabad",
                "Kochi", "Jaipur", "Indore", "Coimbatore", "Remote", "India"
        );
        String lower = text.toLowerCase(Locale.ROOT);
        for (String loc : knownLocations) {
            if (lower.contains(loc.toLowerCase(Locale.ROOT))) {
                if ("Bangalore".equalsIgnoreCase(loc)) return "Bengaluru";
                if ("Gurgaon".equalsIgnoreCase(loc)) return "Gurugram";
                return loc;
            }
        }
        return "India";
    }

    private String extractFirstLineTitle(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() >= 3 && trimmed.length() <= 100) {
                return trimmed;
            }
        }
        return "Role from Job Description";
    }

    private int findSectionIndex(String lowerText, List<String> markers) {
        int best = -1;
        for (String marker : markers) {
            int idx = lowerText.indexOf(marker);
            if (idx > 0 && (best == -1 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private ParsedResumeData callExternalProviderWithRetryForResume(String text) {
        String prompt = "Extract ONLY facts written in the resume text below into JSON with keys: " +
                "skills (string array), yearsOfExperience (number or null), education (string array), projects (string array). " +
                "Respond with valid JSON only.\n\nRESUME:\n" + text;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = invokeExternalLlm(prompt);
            try {
                return objectMapper.readValue(extractJsonObject(raw), ParsedResumeData.class);
            } catch (Exception e) {
                if (attempt == 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider returned invalid JSON after retry");
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider failed");
    }

    private ParsedJobData callExternalProviderWithRetryForJob(String text, String hintTitle, String hintCompany) {
        String prompt = "Extract ONLY facts written in the job description below into JSON with keys: " +
                "title, company, location, workMode (REMOTE|HYBRID|ONSITE), requiredSkills (string array), " +
                "niceToHaveSkills (string array), minYearsExperience (number or null). Respond with valid JSON only.\n\nJD:\n" + text;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = invokeExternalLlm(prompt);
            try {
                return objectMapper.readValue(extractJsonObject(raw), ParsedJobData.class);
            } catch (Exception e) {
                if (attempt == 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider returned invalid JSON after retry");
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider failed");
    }

    private String invokeExternalLlm(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI_API_KEY is not configured for provider " + provider);
        }
        try {
            if ("anthropic".equals(provider)) {
                String body = objectMapper.writeValueAsString(Map.of(
                        "model", model.isBlank() ? "claude-3-5-sonnet-latest" : model,
                        "max_tokens", 1024,
                        "messages", List.of(Map.of("role", "user", "content", prompt))
                ));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.anthropic.com/v1/messages"))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream AI provider returned HTTP " + resp.statusCode());
                }
                JsonNode root = objectMapper.readTree(resp.body());
                return root.path("content").path(0).path("text").asText();
            } else {
                String body = objectMapper.writeValueAsString(Map.of(
                        "model", model.isBlank() ? "gpt-4o-mini" : model,
                        "messages", List.of(Map.of("role", "user", "content", prompt))
                ));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream AI provider returned HTTP " + resp.statusCode());
                }
                JsonNode root = objectMapper.readTree(resp.body());
                return root.path("choices").path(0).path("message").path("content").asText();
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider request failed: " + e.getMessage());
        }
    }

    private String extractJsonObject(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}

