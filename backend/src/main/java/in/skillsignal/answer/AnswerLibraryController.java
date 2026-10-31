package in.skillsignal.answer;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/answers")
public class AnswerLibraryController {

    public record SaveAnswerRequest(
            @NotBlank String questionText,
            String category,
            @NotBlank String approvedAnswer,
            Double confidence,
            Instant expiresAt
    ) {}

    public record UpdateAnswerRequest(
            String questionText,
            String category,
            String approvedAnswer,
            Double confidence,
            Instant expiresAt
    ) {}

    public record ClassifyQuestionsRequest(
            List<String> questions
    ) {}

    private final QuestionClassifierAndAnswerService service;

    public AnswerLibraryController(QuestionClassifierAndAnswerService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnswerLibraryEntry> listAnswers(@AuthenticationPrincipal AuthenticatedUser principal) {
        requireAuth(principal);
        return service.listAnswers(principal.id());
    }

    @PostMapping
    public ResponseEntity<AnswerLibraryEntry> createOrUpdateAnswer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SaveAnswerRequest req
    ) {
        requireAuth(principal);
        AnswerLibraryEntry saved = service.createOrUpdateAnswer(
                principal.id(),
                req.questionText(),
                req.category(),
                req.approvedAnswer(),
                req.confidence(),
                req.expiresAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public AnswerLibraryEntry updateAnswer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody UpdateAnswerRequest req
    ) {
        requireAuth(principal);
        return service.updateAnswerById(
                principal.id(),
                id,
                req.questionText(),
                req.category(),
                req.approvedAnswer(),
                req.confidence(),
                req.expiresAt()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAnswer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        service.deleteAnswer(principal.id(), id);
        return ResponseEntity.ok(Map.of("message", "Answer removed from library"));
    }

    @PostMapping("/classify")
    public List<QuestionClassifierAndAnswerService.QuestionResolutionDto> classifyAndResolve(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody ClassifyQuestionsRequest req
    ) {
        requireAuth(principal);
        List<String> questions = req != null && req.questions() != null ? req.questions() : List.of();
        return questions.stream()
                .map(q -> service.resolveQuestion(principal.id(), q))
                .toList();
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

