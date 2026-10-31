package in.skillsignal.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class GreenhousePublicBoardConnector {

    public record PublicBoardJob(
            String externalId,
            String title,
            String company,
            String location,
            String workMode,
            String sourceUrl,
            String rawDescription,
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            Instant postedAt
    ) {}

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GreenhousePublicBoardConnector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public List<PublicBoardJob> fetchBoardJobs(String boardToken, Duration timeout) throws Exception {
        String token = (boardToken == null || boardToken.isBlank()) ? "demo-india" : boardToken.trim();
        if ("simulate-timeout".equalsIgnoreCase(token) || "timeout-board".equalsIgnoreCase(token)) {
            Thread.sleep(Math.max(500, timeout.toMillis() + 300));
            throw new java.util.concurrent.TimeoutException("Simulated source timeout for boardToken=" + token);
        }
        if ("simulate-error".equalsIgnoreCase(token) || "error-board".equalsIgnoreCase(token)) {
            throw new IllegalStateException("Simulated upstream error from public job board API");
        }
        if ("demo-india".equalsIgnoreCase(token) || "offline-fixture".equalsIgnoreCase(token)
                || token.toLowerCase().startsWith("fixture-")) {
            return getCuratedPublicBoardFixtures();
        }

        // Documented public read-only Greenhouse Job Board endpoint:
        // GET https://boards-api.greenhouse.io/v1/boards/{board_token}/jobs?content=true
        String url = "https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs?content=true";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout != null ? timeout : Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode jobsNode = root.path("jobs");
                if (jobsNode.isArray() && !jobsNode.isEmpty()) {
                    List<PublicBoardJob> results = new ArrayList<>();
                    for (JsonNode jn : jobsNode) {
                        String id = jn.path("id").asText("");
                        String title = jn.path("title").asText("Role");
                        String loc = jn.path("location").path("name").asText("India");
                        String absUrl = jn.path("absolute_url").asText("");
                        String htmlContent = jn.path("content").asText("");
                        String plainText = stripHtml(htmlContent);
                        if (plainText.isBlank()) {
                            plainText = title + " at " + token + " (" + loc + ")";
                        }
                        String mode = loc.toLowerCase().contains("remote") ? "REMOTE" : "HYBRID";
                        results.add(new PublicBoardJob(
                                id,
                                title,
                                capitalize(token),
                                loc,
                                mode,
                                absUrl,
                                plainText.length() > 14000 ? plainText.substring(0, 14000) : plainText,
                                null,
                                null,
                                "INR",
                                "LPA",
                                Instant.now()
                        ));
                        if (results.size() >= 15) break;
                    }
                    return results;
                }
            }
        } catch (Exception ignored) {
            // Fall back to curated public board fixtures when offline or board token is unreachable
        }
        return getCuratedPublicBoardFixtures();
    }

    public List<PublicBoardJob> getCuratedPublicBoardFixtures() {
        Instant now = Instant.now();
        return List.of(
                new PublicBoardJob(
                        "gh-ind-101",
                        "Senior Backend Engineer (Java & Spring Boot)",
                        "Razorflow India",
                        "Bengaluru",
                        "HYBRID",
                        "https://boards.greenhouse.io/razorflow-demo/jobs/101",
                        "Razorflow India is hiring a Senior Backend Engineer in Bengaluru (Hybrid) with 4+ years of experience in Java, Spring Boot, PostgreSQL, REST API, and Docker. Nice to have: Kubernetes, Apache Kafka. Education: B.Tech / B.E. in Computer Science or equivalent.",
                        20.0,
                        30.0,
                        "INR",
                        "LPA",
                        now.minusSeconds(3600 * 12)
                ),
                new PublicBoardJob(
                        "gh-ind-102",
                        "Data & BI Analyst (SQL + Power BI)",
                        "SwiggyPulse DemoBoard",
                        "Hyderabad",
                        "REMOTE",
                        "https://boards.greenhouse.io/swiggypulse-demo/jobs/102",
                        "Looking for a Data & BI Analyst (Remote, India) with 2+ years of experience in SQL, Python, Power BI, and Data Analysis. Nice to have: Snowflake, Tableau. Disclosed compensation: 12 - 18 LPA INR.",
                        12.0,
                        18.0,
                        "INR",
                        "LPA",
                        now.minusSeconds(3600 * 24)
                ),
                new PublicBoardJob(
                        "gh-ind-103",
                        "Clinical Pharmacovigilance Associate",
                        "BioCare LifeSciences",
                        "Mumbai",
                        "ONSITE",
                        "https://boards.greenhouse.io/biocare-demo/jobs/103",
                        "Hiring Clinical Pharmacovigilance Associate in Mumbai with 1+ years of experience in Pharmacovigilance, Clinical Research, Electronic Health Records, and Regulatory Affairs. Qualification: B.Pharm / M.Pharm / MBBS.",
                        6.5,
                        10.0,
                        "INR",
                        "LPA",
                        now.minusSeconds(3600 * 36)
                )
        );
    }

    private String stripHtml(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Company";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

