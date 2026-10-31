package in.skillsignal.answer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class QuestionClassifierAndAnswerService {

    public static final Set<String> VALID_CATEGORIES = Set.of(
            "FACTUAL",
            "PREFERENCE",
            "NUMERIC",
            "LEGAL_CONSENT",
            "FREE_TEXT",
            "UNKNOWN"
    );

    private static final List<String> LEGAL_CONSENT_KEYWORDS = List.of(
            "legal", "declaration", "certify that", "background check", "background verification",
            "criminal", "conviction", "felony", "visa", "sponsorship", "work authorisation",
            "work authorization", "legally authorized", "legally authorised", "right to work",
            "disclosure", "non-compete", "non compete", "nda", "conflict of interest",
            "salary history", "current ctc", "last drawn salary", "last drawn ctc", "payslip",
            "demographic", "gender", "race", "ethnicity", "disability", "veteran", "caste",
            "religion", "marital status", "sexual orientation"
    );

    private static final List<String> FREE_TEXT_KEYWORDS = List.of(
            "why do you want", "why should we hire", "describe a", "tell us about",
            "cover letter", "biggest achievement", "challenging project", "what motivates you",
            "explain how you", "elaborate on"
    );

    private static final List<String> NUMERIC_KEYWORDS = List.of(
            "how many years", "years of experience", "notice period in days",
            "expected ctc", "expected salary", "expected compensation", "current cgpa", "percentage in"
    );

    private static final List<String> PREFERENCE_KEYWORDS = List.of(
            "willing to relocate", "open to relocation", "preferred work mode", "work mode preference",
            "remote or hybrid", "preferred location", "preferred city", "preferred shift",
            "night shift", "rotational shift", "employment type preference", "comfortable working"
    );

    private static final List<String> FACTUAL_KEYWORDS = List.of(
            "highest degree", "highest qualification", "education qualification", "university name",
            "college name", "graduation year", "linkedin profile", "linkedin url", "github url",
            "portfolio url", "current city", "current location", "current employer", "current designation",
            "current job title", "languages known", "proficient in", "do you have experience with"
    );

    public record QuestionResolutionDto(
            String questionText,
            String normalizedQuestion,
            String category,
            String approvedAnswer,
            double confidence,
            String source,
            boolean autoFillAllowed,
            boolean requiresUserPause,
            boolean offerToSaveNewAnswer,
            String status,
            String reason
    ) {}

    private final AnswerLibraryRepository repository;

    public QuestionClassifierAndAnswerService(AnswerLibraryRepository repository) {
        this.repository = repository;
    }

    public String normalizeQuestion(String questionText) {
        if (questionText == null) return "";
        String cleaned = questionText.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    public String classifyQuestion(String questionText) {
        if (questionText == null || questionText.isBlank()) {
            return "UNKNOWN";
        }
        String norm = " " + normalizeQuestion(questionText) + " ";
        String lowerRaw = questionText.toLowerCase(Locale.ROOT);

        for (String kw : LEGAL_CONSENT_KEYWORDS) {
            if (lowerRaw.contains(kw) || norm.contains(" " + kw + " ")) {
                return "LEGAL_CONSENT";
            }
        }
        for (String kw : FREE_TEXT_KEYWORDS) {
            if (lowerRaw.contains(kw) || norm.contains(kw)) {
                return "FREE_TEXT";
            }
        }
        for (String kw : NUMERIC_KEYWORDS) {
            if (lowerRaw.contains(kw) || norm.contains(kw)) {
                return "NUMERIC";
            }
        }
        for (String kw : PREFERENCE_KEYWORDS) {
            if (lowerRaw.contains(kw) || norm.contains(kw)) {
                return "PREFERENCE";
            }
        }
        for (String kw : FACTUAL_KEYWORDS) {
            if (lowerRaw.contains(kw) || norm.contains(kw)) {
                return "FACTUAL";
            }
        }
        return "UNKNOWN";
    }

    @Transactional
    public QuestionResolutionDto resolveQuestion(UUID userId, String questionText) {
        String norm = normalizeQuestion(questionText);
        String classifiedCategory = classifyQuestion(questionText);

        Optional<AnswerLibraryEntry> existingOpt = repository.findByUserIdAndNormalizedQuestion(userId, norm);
        Instant now = Instant.now();
        AnswerLibraryEntry validEntry = existingOpt
                .filter(e -> e.getExpiresAt() == null || e.getExpiresAt().isAfter(now))
                .orElse(null);

        String effectiveCategory = classifiedCategory;
        if ("UNKNOWN".equals(classifiedCategory) && validEntry != null && !"UNKNOWN".equals(validEntry.getCategory())) {
            // Even if user saved a category, sensitive/legal check still overrides
            effectiveCategory = validEntry.getCategory();
        }
        if ("LEGAL_CONSENT".equals(classifiedCategory)) {
            effectiveCategory = "LEGAL_CONSENT";
        }

        // Rule 1: LEGAL_CONSENT (legal, declaration, background-check, visa, work-authorisation, disclosure,
        // salary-history, demographic) and UNKNOWN questions ALWAYS pause for the user.
        if ("LEGAL_CONSENT".equals(effectiveCategory) || "UNKNOWN".equals(effectiveCategory)) {
            return new QuestionResolutionDto(
                    questionText,
                    norm,
                    effectiveCategory,
                    validEntry != null ? validEntry.getApprovedAnswer() : null,
                    validEntry != null ? validEntry.getConfidence() : 0.0,
                    validEntry != null ? "ANSWER_LIBRARY_PAUSED" : "NONE",
                    false,
                    true,
                    validEntry == null,
                    "PAUSED_FOR_USER",
                    "Questions in category " + effectiveCategory + " ALWAYS pause for explicit user review and action."
            );
        }

        // Rule 2: Auto-fill ONLY FACTUAL and PREFERENCE questions with an exact approved answer.
        if ("FACTUAL".equals(effectiveCategory) || "PREFERENCE".equals(effectiveCategory)) {
            if (validEntry != null && validEntry.getApprovedAnswer() != null && !validEntry.getApprovedAnswer().isBlank()) {
                validEntry.setLastUsedAt(now);
                repository.save(validEntry);
                return new QuestionResolutionDto(
                        questionText,
                        norm,
                        effectiveCategory,
                        validEntry.getApprovedAnswer(),
                        validEntry.getConfidence(),
                        "ANSWER_LIBRARY",
                        true,
                        false,
                        false,
                        "AUTO_FILLED",
                        "Exact approved answer found for " + effectiveCategory + " question."
                );
            }
            return new QuestionResolutionDto(
                    questionText,
                    norm,
                    effectiveCategory,
                    null,
                    0.0,
                    "NONE",
                    false,
                    true,
                    true,
                    "MISSING_APPROVED_ANSWER",
                    "No approved answer in library; user input required (offer to save to Answer Library)."
            );
        }

        // Rule 3: FREE_TEXT needs a pre-approved template or explicit user approval (never silent auto-fill).
        if ("FREE_TEXT".equals(effectiveCategory)) {
            if (validEntry != null && validEntry.getApprovedAnswer() != null && !validEntry.getApprovedAnswer().isBlank()) {
                validEntry.setLastUsedAt(now);
                repository.save(validEntry);
                return new QuestionResolutionDto(
                        questionText,
                        norm,
                        effectiveCategory,
                        validEntry.getApprovedAnswer(),
                        validEntry.getConfidence(),
                        "PRE_APPROVED_TEMPLATE",
                        false,
                        false,
                        false,
                        "TEMPLATE_READY",
                        "Pre-approved free-text template loaded from Answer Library."
                );
            }
            return new QuestionResolutionDto(
                    questionText,
                    norm,
                    effectiveCategory,
                    null,
                    0.0,
                    "NONE",
                    false,
                    true,
                    true,
                    "NEEDS_TEMPLATE_OR_APPROVAL",
                    "Free-text question requires a pre-approved template or manual user approval."
            );
        }

        // Rule 4: NUMERIC questions — never auto-fill blindly; suggest stored answer if present, else pause.
        if (validEntry != null && validEntry.getApprovedAnswer() != null && !validEntry.getApprovedAnswer().isBlank()) {
            validEntry.setLastUsedAt(now);
            repository.save(validEntry);
            return new QuestionResolutionDto(
                    questionText,
                    norm,
                    effectiveCategory,
                    validEntry.getApprovedAnswer(),
                    validEntry.getConfidence(),
                    "ANSWER_LIBRARY_NUMERIC",
                    false,
                    false,
                    false,
                    "APPROVED_NUMERIC",
                    "Approved numeric answer loaded from Answer Library (auto-fill restricted to FACTUAL/PREFERENCE)."
            );
        }
        return new QuestionResolutionDto(
                questionText,
                norm,
                effectiveCategory,
                null,
                0.0,
                "NONE",
                false,
                true,
                true,
                "MISSING_APPROVED_ANSWER",
                "Numeric question has no approved answer in library; user input required."
        );
    }

    public List<AnswerLibraryEntry> listAnswers(UUID userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public AnswerLibraryEntry createOrUpdateAnswer(
            UUID userId,
            String questionText,
            String category,
            String approvedAnswer,
            Double confidence,
            Instant expiresAt
    ) {
        if (questionText == null || questionText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionText is required");
        }
        if (approvedAnswer == null || approvedAnswer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approvedAnswer is required");
        }
        String norm = normalizeQuestion(questionText);
        String resolvedCategory = (category != null && !category.isBlank())
                ? validateCategory(category)
                : classifyQuestion(questionText);

        AnswerLibraryEntry entry = repository.findByUserIdAndNormalizedQuestion(userId, norm)
                .orElseGet(() -> {
                    AnswerLibraryEntry e = new AnswerLibraryEntry();
                    e.setUserId(userId);
                    e.setNormalizedQuestion(norm);
                    return e;
                });

        entry.setQuestionText(questionText.trim());
        entry.setCategory(resolvedCategory);
        entry.setApprovedAnswer(approvedAnswer.trim());
        entry.setConfidence(confidence != null ? Math.max(0.0, Math.min(1.0, confidence)) : 1.0);
        entry.setExpiresAt(expiresAt);
        return repository.save(entry);
    }

    @Transactional
    public AnswerLibraryEntry updateAnswerById(
            UUID userId,
            UUID id,
            String questionText,
            String category,
            String approvedAnswer,
            Double confidence,
            Instant expiresAt
    ) {
        AnswerLibraryEntry entry = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));

        if (questionText != null && !questionText.isBlank()) {
            entry.setQuestionText(questionText.trim());
            entry.setNormalizedQuestion(normalizeQuestion(questionText));
        }
        if (category != null && !category.isBlank()) {
            entry.setCategory(validateCategory(category));
        }
        if (approvedAnswer != null && !approvedAnswer.isBlank()) {
            entry.setApprovedAnswer(approvedAnswer.trim());
        }
        if (confidence != null) {
            entry.setConfidence(Math.max(0.0, Math.min(1.0, confidence)));
        }
        entry.setExpiresAt(expiresAt);
        return repository.save(entry);
    }

    @Transactional
    public void deleteAnswer(UUID userId, UUID id) {
        AnswerLibraryEntry entry = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
        repository.delete(entry);
    }

    private String validateCategory(String cat) {
        String norm = cat.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CATEGORIES.contains(norm)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid category (allowed: FACTUAL, PREFERENCE, NUMERIC, LEGAL_CONSENT, FREE_TEXT, UNKNOWN): " + cat
            );
        }
        return norm;
    }
}

