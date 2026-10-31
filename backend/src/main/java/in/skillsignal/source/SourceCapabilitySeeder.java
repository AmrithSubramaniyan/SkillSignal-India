package in.skillsignal.source;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Order(1)
public class SourceCapabilitySeeder implements ApplicationRunner {

    private final SourceCapabilityRepository repository;

    public SourceCapabilitySeeder(SourceCapabilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant reviewedNow = Instant.now();

        upsert("manual", "Manual Job Entry (Paste JD / URL)", "USER_INPUT",
                "ASSISTED_APPLY", "120/min", reviewedNow,
                "User manual confirmation in tracker",
                "Permitted: User-supplied job descriptions", true);

        upsert("company_ats", "Company Career Boards (Greenhouse Public API)", "PUBLIC_JSON_API",
                "ASSISTED_APPLY", "30/min", reviewedNow,
                "Redirect to official company board URL; user confirms submission",
                "Permitted read-only GET via boards-api.greenhouse.io (GATE G3 approved)", true);

        upsert("email_apply", "Email Application Preparation", "EMAIL_DRAFT",
                "ASSISTED_APPLY", "20/min", reviewedNow,
                "User-approved email send receipt",
                "Assisted email draft preparation; requires per-user consent", true);

        // Disabled portals with no verified public API access (Never scrape!)
        List<String[]> disabledPortals = List.of(
                new String[]{"naukri", "Naukri.com"},
                new String[]{"linkedin", "LinkedIn Jobs"},
                new String[]{"indeed", "Indeed"},
                new String[]{"foundit", "Foundit (Monster India)"},
                new String[]{"instahyre", "Instahyre"},
                new String[]{"wellfound", "Wellfound (AngelList Talent)"}
        );

        for (String[] portal : disabledPortals) {
            upsert(portal[0], portal[1], "NO_VERIFIED_API_ACCESS",
                    "ASSISTED_APPLY", "0/min (disabled)", reviewedNow,
                    "None — scraping forbidden by portal terms",
                    "no verified API access", false);
        }
    }

    private void upsert(
            String source,
            String displayName,
            String accessMethod,
            String allowedActions,
            String rateLimit,
            Instant termsReviewedAt,
            String confirmationSignals,
            String statusNote,
            boolean enabled
    ) {
        if (repository.findBySourceIgnoreCase(source).isPresent()) {
            return;
        }
        SourceCapability cap = new SourceCapability();
        cap.setSource(source);
        cap.setDisplayName(displayName);
        cap.setAccessMethod(accessMethod);
        cap.setAllowedActions(allowedActions);
        cap.setRateLimit(rateLimit);
        cap.setTermsReviewedAt(termsReviewedAt);
        cap.setConfirmationSignals(confirmationSignals);
        cap.setStatusNote(statusNote);
        cap.setEnabled(enabled);
        repository.save(cap);
    }
}

