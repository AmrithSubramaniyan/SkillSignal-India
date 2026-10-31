package in.skillsignal.ai;

import java.util.List;

public record ParsedJobData(
        String title,
        String company,
        String location,
        String workMode,
        List<String> requiredSkills,
        List<String> niceToHaveSkills,
        Double minYearsExperience
) {}

