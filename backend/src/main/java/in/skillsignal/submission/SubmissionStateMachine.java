package in.skillsignal.submission;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
public class SubmissionStateMachine {

    public static final Set<String> VALID_STATES = Set.of(
            "DISCOVERED",
            "MATCHED",
            "PACK_PREPARING",
            "PACK_READY",
            "AWAITING_APPROVAL",
            "APPROVED",
            "EDITED",
            "REJECTED",
            "EXPIRED",
            "SUBMITTING",
            "SUBMITTED_CONFIRMED",
            "FAILED",
            "NEEDS_VERIFICATION",
            "BLOCKED_MANUAL_ACTION"
    );

    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = new HashMap<>();

    static {
        LEGAL_TRANSITIONS.put("DISCOVERED", Set.of("MATCHED", "BLOCKED_MANUAL_ACTION", "EXPIRED"));
        LEGAL_TRANSITIONS.put("MATCHED", Set.of("PACK_PREPARING", "BLOCKED_MANUAL_ACTION", "EXPIRED"));
        LEGAL_TRANSITIONS.put("PACK_PREPARING", Set.of("PACK_READY", "FAILED", "BLOCKED_MANUAL_ACTION"));
        LEGAL_TRANSITIONS.put("PACK_READY", Set.of(
                "AWAITING_APPROVAL", "APPROVED", "EDITED", "REJECTED", "EXPIRED", "BLOCKED_MANUAL_ACTION"
        ));
        LEGAL_TRANSITIONS.put("AWAITING_APPROVAL", Set.of(
                "APPROVED", "EDITED", "REJECTED", "EXPIRED", "BLOCKED_MANUAL_ACTION", "SUBMITTED_CONFIRMED"
        ));
        LEGAL_TRANSITIONS.put("EDITED", Set.of(
                "AWAITING_APPROVAL", "APPROVED", "EDITED", "REJECTED", "EXPIRED", "BLOCKED_MANUAL_ACTION"
        ));
        LEGAL_TRANSITIONS.put("APPROVED", Set.of(
                "SUBMITTING", "SUBMITTED_CONFIRMED", "NEEDS_VERIFICATION", "FAILED", "BLOCKED_MANUAL_ACTION", "EXPIRED"
        ));
        LEGAL_TRANSITIONS.put("SUBMITTING", Set.of(
                "SUBMITTED_CONFIRMED", "FAILED", "NEEDS_VERIFICATION", "BLOCKED_MANUAL_ACTION"
        ));
        // Golden Rule: NEEDS_VERIFICATION blocks any retry (cannot transition to SUBMITTING or APPROVED).
        // Only explicit user resolution (/api/submissions/{id}/resolve) can move NEEDS_VERIFICATION to SUBMITTED_CONFIRMED or FAILED.
        LEGAL_TRANSITIONS.put("NEEDS_VERIFICATION", Set.of("SUBMITTED_CONFIRMED", "FAILED"));
        LEGAL_TRANSITIONS.put("FAILED", Set.of("PACK_PREPARING", "AWAITING_APPROVAL", "BLOCKED_MANUAL_ACTION"));
        LEGAL_TRANSITIONS.put("BLOCKED_MANUAL_ACTION", Set.of(
                "AWAITING_APPROVAL", "SUBMITTED_CONFIRMED", "REJECTED", "EXPIRED"
        ));
        LEGAL_TRANSITIONS.put("REJECTED", Set.of("AWAITING_APPROVAL"));
        LEGAL_TRANSITIONS.put("EXPIRED", Set.of("PACK_PREPARING", "AWAITING_APPROVAL"));
        LEGAL_TRANSITIONS.put("SUBMITTED_CONFIRMED", Set.of());
    }

    public String normalizeState(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return norm;
    }

    public boolean isValidState(String raw) {
        return VALID_STATES.contains(normalizeState(raw));
    }

    public Set<String> getAllowedNextStates(String fromState) {
        String norm = normalizeState(fromState);
        return LEGAL_TRANSITIONS.getOrDefault(norm, Set.of());
    }

    public void validateTransition(String fromState, String toState, boolean isUserResolveAction) {
        String normFrom = normalizeState(fromState);
        String normTo = normalizeState(toState);

        if (!VALID_STATES.contains(normTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid submission state: " + toState
            );
        }

        if (normFrom.equals(normTo) && !"EDITED".equals(normTo)) {
            return;
        }

        if ("NEEDS_VERIFICATION".equals(normFrom)) {
            if ("SUBMITTING".equals(normTo) || "APPROVED".equals(normTo) || !isUserResolveAction) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Submission is in NEEDS_VERIFICATION state: automatic retry or re-submission is strictly blocked. User must resolve via /api/submissions/{id}/resolve."
                );
            }
        }

        Set<String> allowed = LEGAL_TRANSITIONS.getOrDefault(normFrom, Set.of());
        if (!allowed.contains(normTo)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Illegal submission state transition from " + normFrom + " to " + normTo
            );
        }
    }
}
