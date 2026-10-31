package in.skillsignal.ai;

import java.util.List;

public record ParsedResumeData(
        List<String> skills,
        Double yearsOfExperience,
        List<String> education,
        List<String> projects
) {}

