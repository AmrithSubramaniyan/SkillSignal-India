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
        String salaryFit,
        String qualificationFit,
        String userFeedback,
        String feedbackNote,
        List<String> suggestions,
        String disclaimer,
        Instant computedAt
) {
    public static final String MANDATORY_DISCLAIMER =
            "This score estimates relevance. It does not guarantee an interview or selection.";
    public static final String NOT_ENOUGH_DATA = "not enough data";

    public MatchResultDto(
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
        this(
                matchId,
                jobId,
                resumeVersionId,
                score,
                requiredSkillsScore,
                niceToHaveScore,
                experienceScore,
                locationScore,
                matchedRequired,
                missingRequired,
                matchedNiceToHave,
                missingNiceToHave,
                experienceFit,
                locationFit,
                NOT_ENOUGH_DATA,
                NOT_ENOUGH_DATA,
                null,
                null,
                suggestions,
                disclaimer,
                computedAt
        );
    }
}
