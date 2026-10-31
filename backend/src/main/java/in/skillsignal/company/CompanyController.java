package in.skillsignal.company;

import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    public record UpsertCompanyRequest(
            @NotBlank String name,
            String tier,
            Boolean priority,
            String website,
            String notes
    ) {}

    public record UpdateCompanyRequest(
            String tier,
            Boolean priority,
            String website,
            String notes
    ) {}

    private final CompanyIntelligenceService service;

    public CompanyController(CompanyIntelligenceService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompanyIntelligenceService.CompanyDto> listCompanies(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) Boolean priorityOnly
    ) {
        requireAuth(principal);
        return service.listCompanies(principal.id(), tier, priorityOnly);
    }

    @GetMapping("/hiring-intelligence")
    public CompanyIntelligenceService.HiringIntelligenceSummaryDto getHiringIntelligence(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) Boolean priorityOnly
    ) {
        requireAuth(principal);
        return service.getHiringIntelligence(principal.id(), tier, priorityOnly);
    }

    @PostMapping
    public ResponseEntity<CompanyIntelligenceService.CompanyDto> createOrUpdateCompany(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpsertCompanyRequest req
    ) {
        requireAuth(principal);
        var dto = service.createOrUpdateCompany(
                principal.id(),
                req.name(),
                req.tier(),
                req.priority(),
                req.website(),
                req.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public CompanyIntelligenceService.CompanyDto updateCompany(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestBody UpdateCompanyRequest req
    ) {
        requireAuth(principal);
        return service.updateCompanyById(
                principal.id(),
                id,
                req.tier(),
                req.priority(),
                req.website(),
                req.notes()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCompany(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        requireAuth(principal);
        service.deleteCompany(principal.id(), id);
        return ResponseEntity.ok(Map.of("message", "Company removed"));
    }

    private void requireAuth(AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}

