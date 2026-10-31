package in.skillsignal.consent;

import in.skillsignal.audit.AuditService;
import in.skillsignal.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/consents")
public class ConsentController {

    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "STORE_RESUME_ANSWERS",
            "ENABLE_PORTAL_APPLY",
            "ENABLE_EMAIL_SENDING"
    );

    public record ConsentDto(
            String consentType,
            String consentVersion,
            boolean granted,
            Instant grantedAt,
            Instant revokedAt,
            Instant updatedAt
    ) {
        public static ConsentDto from(ConsentRecord r) {
            return new ConsentDto(
                    r.getConsentType(),
                    r.getConsentVersion(),
                    r.isGranted(),
                    r.getGrantedAt(),
                    r.getRevokedAt(),
                    r.getUpdatedAt()
            );
        }
    }

    public record UpdateConsentRequest(
            @NotNull Boolean granted,
            String consentVersion
    ) {}

    private final ConsentRecordRepository consentRepository;
    private final AuditService auditService;

    public ConsentController(ConsentRecordRepository consentRepository, AuditService auditService) {
        this.consentRepository = consentRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<ConsentDto> getConsents(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        List<ConsentDto> result = new ArrayList<>();
        for (String type : List.of("STORE_RESUME_ANSWERS", "ENABLE_PORTAL_APPLY", "ENABLE_EMAIL_SENDING")) {
            ConsentRecord rec = consentRepository.findByUserIdAndConsentType(principal.id(), type)
                    .orElseGet(() -> {
                        ConsentRecord created = new ConsentRecord();
                        created.setUserId(principal.id());
                        created.setConsentType(type);
                        created.setConsentVersion("v1.0");
                        created.setGranted(false);
                        return consentRepository.save(created);
                    });
            result.add(ConsentDto.from(rec));
        }
        return result;
    }

    @PutMapping("/{consentType}")
    public ConsentDto updateConsent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String consentType,
            @Valid @RequestBody UpdateConsentRequest req
    ) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String normalizedType = consentType.trim().toUpperCase();
        if (!SUPPORTED_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported consent type: " + consentType);
        }
        ConsentRecord rec = consentRepository.findByUserIdAndConsentType(principal.id(), normalizedType)
                .orElseGet(() -> {
                    ConsentRecord created = new ConsentRecord();
                    created.setUserId(principal.id());
                    created.setConsentType(normalizedType);
                    return created;
                });

        String version = (req.consentVersion() != null && !req.consentVersion().isBlank())
                ? req.consentVersion().trim()
                : "v1.0";
        rec.setConsentVersion(version);
        rec.setGranted(req.granted());
        if (Boolean.TRUE.equals(req.granted())) {
            rec.setGrantedAt(Instant.now());
            rec.setRevokedAt(null);
        } else {
            rec.setRevokedAt(Instant.now());
        }
        rec = consentRepository.save(rec);

        auditService.log(
                principal.id(),
                req.granted() ? "CONSENT_GRANTED" : "CONSENT_REVOKED",
                "ConsentRecord",
                rec.getId().toString(),
                Map.of("consentType", normalizedType, "version", version, "granted", req.granted())
        );

        return ConsentDto.from(rec);
    }
}

