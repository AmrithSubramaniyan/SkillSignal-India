package in.skillsignal.agent;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/mock-portal")
public class MockPortalController {

    private final List<Map<String, Object>> submitClicks = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage(@RequestParam(defaultValue = "true") boolean loggedIn) {
        if (loggedIn) {
            return """
                    <!DOCTYPE html>
                    <html>
                    <head><title>SkillSignal Local Mock Portal - Logged In</title></head>
                    <body data-logged-in="true">
                      <h1 id="session-status">Authenticated Session Active</h1>
                      <p>Persistent browser profile is logged in.</p>
                    </body>
                    </html>
                    """;
        }
        return """
                <!DOCTYPE html>
                <html>
                <head><title>SkillSignal Local Mock Portal - Login Required</title></head>
                <body data-logged-in="false">
                  <h1 id="login-prompt">Please Log In Manually in Browser</h1>
                  <form id="login-form">
                    <input type="email" name="email" placeholder="Email" />
                    <button type="submit">Sign In</button>
                  </form>
                </body>
                </html>
                """;
    }

    @GetMapping(value = "/jobs/{scenario}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> jobScenarioPage(@PathVariable String scenario) {
        String norm = scenario.toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "closed" -> ResponseEntity.status(404).body("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Job Closed</title></head>
                    <body data-apply-type="CLOSED_LISTING">
                      <div id="closed-banner">This job is no longer accepting applications (Closed).</div>
                    </body>
                    </html>
                    """);
            case "external-redirect" -> ResponseEntity.ok("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>External Company ATS Redirect</title></head>
                    <body data-apply-type="EXTERNAL_REDIRECT">
                      <a id="external-apply-link" href="https://careers.example.com/external-ats">Apply on Company Site</a>
                    </body>
                    </html>
                    """);
            case "captcha" -> ResponseEntity.ok("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Security Verification Required</title></head>
                    <body data-blocker="CAPTCHA">
                      <div id="captcha-challenge">Unusual activity detected. Please complete the CAPTCHA challenge.</div>
                    </body>
                    </html>
                    """);
            case "legal-question" -> ResponseEntity.ok("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Easy Apply - Legal Question</title></head>
                    <body data-apply-type="EASY_APPLY">
                      <form id="easy-apply-form" method="post" action="/mock-portal/submit">
                        <label data-question-category="LEGAL_CONSENT">Do you consent to a criminal background check and visa sponsorship declaration?</label>
                        <input type="text" name="legal_answer" />
                        <input type="file" name="resume" id="resume-upload" />
                        <button type="submit" id="final-submit-btn">Submit Application</button>
                      </form>
                    </body>
                    </html>
                    """);
            case "unknown-question" -> ResponseEntity.ok("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Easy Apply - Unknown Question</title></head>
                    <body data-apply-type="EASY_APPLY">
                      <form id="easy-apply-form" method="post" action="/mock-portal/submit">
                        <label data-question-category="UNKNOWN">What is your custom internal badge code for the 2026 rotation?</label>
                        <input type="text" name="unknown_answer" />
                        <input type="file" name="resume" id="resume-upload" />
                        <button type="submit" id="final-submit-btn">Submit Application</button>
                      </form>
                    </body>
                    </html>
                    """);
            case "no-confirmation" -> ResponseEntity.ok("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Easy Apply - Ambiguous Confirmation</title></head>
                    <body data-apply-type="EASY_APPLY" data-confirmation-mode="NONE">
                      <form id="easy-apply-form" method="post" action="/mock-portal/submit?confirm=false">
                        <label data-question-category="FACTUAL">Years of experience in Java?</label>
                        <input type="text" name="java_exp" id="q-java-exp" />
                        <input type="file" name="resume" id="resume-upload" />
                        <button type="submit" id="final-submit-btn">Submit Application</button>
                      </form>
                    </body>
                    </html>
                    """);
            default -> ResponseEntity.ok("""
                    <!DOCTYPE html>
                    <html>
                    <head><title>Easy Apply - Standard Multi-Step</title></head>
                    <body data-apply-type="EASY_APPLY" data-confirmation-mode="CONFIRMED">
                      <form id="easy-apply-form" method="post" action="/mock-portal/submit?confirm=true">
                        <div class="step-1">
                          <label data-question-category="FACTUAL">Years of experience in Java?</label>
                          <input type="text" name="java_exp" id="q-java-exp" />
                          <label data-question-category="PREFERENCE">Notice period in days?</label>
                          <input type="text" name="notice_period" id="q-notice-period" />
                        </div>
                        <div class="step-2">
                          <input type="file" name="resume" id="resume-upload" />
                        </div>
                        <div class="step-final">
                          <button type="submit" id="final-submit-btn">Submit Application</button>
                        </div>
                      </form>
                    </body>
                    </html>
                    """);
        };
    }

    @PostMapping(value = "/submit", produces = MediaType.TEXT_HTML_VALUE)
    public String submitApplication(
            @RequestParam(defaultValue = "true") boolean confirm,
            @RequestParam(required = false) String jobId
    ) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("jobId", jobId != null ? jobId : "mock-job");
        record.put("confirm", confirm);
        record.put("clickedAt", Instant.now().toString());
        submitClicks.add(record);

        if (confirm) {
            String refId = "MOCK-CONF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            return """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Application Submitted</title></head>
                    <body data-confirmed="true" data-confirmation-id="%s">
                      <div id="confirmation-banner">Application submitted! Reference ID: %s</div>
                    </body>
                    </html>
                    """.formatted(refId, refId);
        }
        return """
                <!DOCTYPE html>
                <html>
                <head><title>Processing</title></head>
                <body data-confirmed="false">
                  <div id="ambiguous-banner">Your request was received, status unknown.</div>
                </body>
                </html>
                """;
    }

    @GetMapping("/submit-clicks")
    public Map<String, Object> getSubmitClicks() {
        return Map.of(
                "count", submitClicks.size(),
                "clicks", submitClicks
        );
    }

    @DeleteMapping("/submit-clicks")
    public Map<String, Object> clearSubmitClicks() {
        submitClicks.clear();
        return Map.of("count", 0);
    }
}

