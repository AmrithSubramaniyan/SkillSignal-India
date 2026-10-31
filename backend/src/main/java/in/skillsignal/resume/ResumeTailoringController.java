package in.skillsignal.resume;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes/tailor")
public class ResumeTailoringController {

    private final ResumeTailoringService tailoringService;

    public ResumeTailoringController(ResumeTailoringService tailoringService) {
        this.tailoringService = tailoringService;
    }

    public record UpdateTailoredTextRequest(@NotBlank String tailoredText) {}

    @GetMapping
    public List<ResumeTailoringService.TailoredResumeDto> listTailoredResumes(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return tailoringService.listForUser(user.id());
    }

    @PostMapping
    public ResponseEntity<ResumeTailoringService.TailoredResumeDto> createTailoredDraft(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody ResumeTailoringService.TailorResumeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tailoringService.createTailoredDraft(user.id(), request));
    }

    @PutMapping("/{id}")
    public ResumeTailoringService.TailoredResumeDto updateTailoredDraft(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTailoredTextRequest request
    ) {
        return tailoringService.updateTailoredDraft(user.id(), id, request.tailoredText());
    }

    @PostMapping("/{id}/approve")
    public ResumeTailoringService.TailoredResumeDto approveTailoredDraft(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return tailoringService.approveTailoredDraft(user.id(), id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadTailoredResume(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        byte[] bytes = tailoringService.exportApprovedDraft(user.id(), id, format);
        boolean isDocx = "docx".equalsIgnoreCase(format);
        String contentType = isDocx
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : MediaType.APPLICATION_PDF_VALUE;
        String ext = isDocx ? "docx" : "pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tailored-resume-" + id + "." + ext + "\"")
                .body(bytes);
    }
}
