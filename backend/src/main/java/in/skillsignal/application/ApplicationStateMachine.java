package in.skillsignal.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
public class ApplicationStateMachine {

    public static final String SUB_STATE_NEEDS_VERIFICATION = "NEEDS_VERIFICATION";

    public static final Set<String> VALID_CONFIRMATION_TYPES = Set.of(
            "USER_CONFIRMED",
            "EXTERNALLY_CONFIRMED",
            "UNCONFIRMED"
    );

    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = createTransitionGraph();

    public Set<String> getAllStatuses() {
        return Collections.unmodifiableSet(LEGAL_TRANSITIONS.keySet());
    }

    public Set<String> getAllowedNextStatuses(String currentStatus) {
        if (currentStatus == null) return Set.of();
        String norm = currentStatus.trim().toUpperCase(Locale.ROOT);
        return LEGAL_TRANSITIONS.getOrDefault(norm, Set.of());
    }

    public boolean isValidStatus(String status) {
        if (status == null || status.isBlank()) return false;
        return LEGAL_TRANSITIONS.containsKey(status.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isLegalTransition(String fromStatus, String toStatus) {
        if (fromStatus == null || toStatus == null) return false;
        String from = fromStatus.trim().toUpperCase(Locale.ROOT);
        String to = toStatus.trim().toUpperCase(Locale.ROOT);
        if (!LEGAL_TRANSITIONS.containsKey(from) || !LEGAL_TRANSITIONS.containsKey(to)) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }
        return LEGAL_TRANSITIONS.get(from).contains(to);
    }

    public void validateTransition(String fromStatus, String toStatus) {
        if (!isValidStatus(toStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application status: " + toStatus);
        }
        if (fromStatus != null && !isLegalTransition(fromStatus, toStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Illegal application status transition from " + fromStatus + " to " + toStatus
            );
        }
    }

    public record TransitionOutcome(
            String effectiveStatus,
            String effectiveSubState,
            String effectiveConfirmationType
    ) {}

    /**
     * Golden Rule: Never state an application was submitted unless submission is confirmed.
     * If uncertain (UNCONFIRMED), sub-state becomes NEEDS_VERIFICATION.
     */
    public TransitionOutcome resolveEffectiveOutcome(
            String requestedStatus,
            String requestedSubState,
            String requestedConfirmationType
    ) {
        String status = requestedStatus != null ? requestedStatus.trim().toUpperCase(Locale.ROOT) : "SAVED";
        String conf = requestedConfirmationType != null && !requestedConfirmationType.isBlank()
                ? requestedConfirmationType.trim().toUpperCase(Locale.ROOT)
                : null;
        if (conf != null && !VALID_CONFIRMATION_TYPES.contains(conf)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid confirmationType: " + requestedConfirmationType);
        }

        String subState = requestedSubState != null && !requestedSubState.isBlank()
                ? requestedSubState.trim().toUpperCase(Locale.ROOT)
                : null;

        if ("NEEDS_VERIFICATION".equals(status)) {
            return new TransitionOutcome("NEEDS_VERIFICATION", SUB_STATE_NEEDS_VERIFICATION, "UNCONFIRMED");
        }

        if ("APPLIED".equals(status) || "APPLIED_CONFIRMED".equals(status)) {
            if ("UNCONFIRMED".equals(conf) || SUB_STATE_NEEDS_VERIFICATION.equals(subState)) {
                return new TransitionOutcome(status, SUB_STATE_NEEDS_VERIFICATION, "UNCONFIRMED");
            }
            String confirmedType = conf != null ? conf : "USER_CONFIRMED";
            return new TransitionOutcome(status, null, confirmedType);
        }

        return new TransitionOutcome(
                status,
                subState,
                conf != null ? conf : "UNCONFIRMED"
        );
    }

    private static Map<String, Set<String>> createTransitionGraph() {
        Map<String, Set<String>> m = new LinkedHashMap<>();

        Set<String> fromInterestedOrSaved = Set.of(
                "INTERESTED", "SAVED", "READY_TO_APPLY", "PREPARING", "AWAITING_APPROVAL",
                "APPLIED", "APPLIED_CONFIRMED", "SUBMISSION_FAILED", "NEEDS_VERIFICATION",
                "WITHDRAWN", "CLOSED"
        );
        m.put("INTERESTED", fromInterestedOrSaved);
        m.put("SAVED", fromInterestedOrSaved);

        Set<String> fromReady = Set.of(
                "INTERESTED", "SAVED", "READY_TO_APPLY", "PREPARING", "AWAITING_APPROVAL",
                "APPLIED", "APPLIED_CONFIRMED", "SUBMISSION_FAILED", "NEEDS_VERIFICATION",
                "WITHDRAWN", "CLOSED"
        );
        m.put("READY_TO_APPLY", fromReady);
        m.put("PREPARING", fromReady);

        m.put("AWAITING_APPROVAL", Set.of(
                "READY_TO_APPLY", "PREPARING", "APPLIED", "APPLIED_CONFIRMED",
                "SUBMISSION_FAILED", "NEEDS_VERIFICATION", "WITHDRAWN", "CLOSED"
        ));

        m.put("SUBMISSION_FAILED", Set.of(
                "READY_TO_APPLY", "AWAITING_APPROVAL", "APPLIED", "APPLIED_CONFIRMED",
                "NEEDS_VERIFICATION", "WITHDRAWN", "CLOSED"
        ));

        m.put("NEEDS_VERIFICATION", Set.of(
                "APPLIED", "APPLIED_CONFIRMED", "SUBMISSION_FAILED", "READY_TO_APPLY",
                "WITHDRAWN", "CLOSED"
        ));

        Set<String> fromApplied = Set.of(
                "APPLIED", "APPLIED_CONFIRMED", "NEEDS_VERIFICATION", "ASSESSMENT",
                "INTERVIEW", "INTERVIEWING", "ON_HOLD", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"
        );
        m.put("APPLIED", fromApplied);
        m.put("APPLIED_CONFIRMED", fromApplied);

        m.put("ASSESSMENT", Set.of(
                "INTERVIEW", "INTERVIEWING", "ON_HOLD", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"
        ));

        Set<String> fromInterview = Set.of(
                "ASSESSMENT", "INTERVIEW", "INTERVIEWING", "ON_HOLD", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"
        );
        m.put("INTERVIEW", fromInterview);
        m.put("INTERVIEWING", fromInterview);

        m.put("ON_HOLD", Set.of(
                "ASSESSMENT", "INTERVIEW", "INTERVIEWING", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"
        ));

        m.put("OFFER", Set.of(
                "ON_HOLD", "REJECTED", "WITHDRAWN", "CLOSED"
        ));

        m.put("REJECTED", Set.of("CLOSED"));
        m.put("WITHDRAWN", Set.of("CLOSED"));
        m.put("CLOSED", Set.of());

        return m;
    }
}

