package in.skillsignal.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchResultDto(
        UUID matchId,
        UUID jobId,
        UUID resumeVersionId,
        int score,
        int requiredSkillsScore,
        int niceToHaveScore,
        int experienceScore,
        int locationScore,
        List<String> matchedRequired,
        List<String> missingRequired,
        List<String> matchedNiceToHave,
        List<String> missingNiceToHave,
        String experienceFit,
        String locationFit,
        List<String> suggestions,
        String disclaimer,
        Instant computedAt
) {
    public static final String MANDATORY_DISCLAIMER =
            "This score estimates relevance. It does not guarantee an interview or selection.";
}

