package in.skillsignal.ai;

public interface AiClient {
    int MAX_INPUT_CHARS = 15_000;

    ParsedResumeData parseResume(String resumeText);

    ParsedJobData parseJobDescription(String jobText, String hintTitle, String hintCompany);
}

