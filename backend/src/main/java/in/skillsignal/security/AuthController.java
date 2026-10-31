package in.skillsignal.security;

import in.skillsignal.audit.AuditService;
import in.skillsignal.session.SessionCheckoutEmailService;
import in.skillsignal.session.SessionContextHolder;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(min = 2, max = 120) String fullName
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    public record UserDto(UUID id, String email, String fullName, String role, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getCreatedAt());
        }
    }

    public record AuthResponse(String token, UserDto user, UUID sessionId, String beaconToken) {}

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final MailSenderService mailSenderService;
    private final AuditService auditService;
    private final SessionCheckoutEmailService sessionService;
    private final String frontendUrl;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            MailSenderService mailSenderService,
            AuditService auditService,
            SessionCheckoutEmailService sessionService,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.mailSenderService = mailSenderService;
        this.auditService = auditService;
        this.sessionService = sessionService;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        String normalizedEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setRole("USER");
        user = userRepository.save(user);

        var checkIn = sessionService.checkIn(user.getId());
        auditService.log(user.getId(), "USER_REGISTERED", "User", user.getId().toString(), Map.of("role", user.getRole()));
        String token = jwtService.generateToken(user, checkIn.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, UserDto.from(user), checkIn.sessionId(), checkIn.beaconToken()));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        String normalizedEmail = req.email().trim().toLowerCase(Locale.ROOT);
        String rateKey = httpReq.getRemoteAddr() + ":" + normalizedEmail;
        if (!loginRateLimiter.tryAcquire(rateKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please try again later.");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        loginRateLimiter.reset(rateKey);
        var checkIn = sessionService.checkIn(user.getId());
        auditService.log(user.getId(), "USER_LOGIN", "User", user.getId().toString(), Map.of("sessionId", checkIn.sessionId().toString()));
        String token = jwtService.generateToken(user, checkIn.sessionId());
        return ResponseEntity.ok(new AuthResponse(token, UserDto.from(user), checkIn.sessionId(), checkIn.beaconToken()));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UUID activeSessionId = SessionContextHolder.getSessionId();
        if (activeSessionId != null) {
            try {
                sessionService.checkOut(principal.id(), activeSessionId, "LOGOUT");
            } catch (Exception ignored) {
            }
        }
        userRepository.findById(principal.id()).ifPresent(user -> {
            user.setTokenRevokedBefore(Instant.now());
            userRepository.save(user);
            auditService.log(user.getId(), "USER_LOGOUT", "User", user.getId().toString(), Map.of());
        });
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        String normalizedEmail = req.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setResetToken(resetToken);
            user.setResetTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            userRepository.save(user);
            String link = frontendUrl + "/reset-password?token=" + resetToken;
            mailSenderService.sendPasswordResetEmail(user.getEmail(), link);
            auditService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "User", user.getId().toString(), Map.of());
        });
        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a password reset link has been sent."));
    }

    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        User user = userRepository.findByResetToken(req.token().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token has expired");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        user.setTokenRevokedBefore(Instant.now());
        userRepository.save(user);
        auditService.log(user.getId(), "PASSWORD_RESET_COMPLETED", "User", user.getId().toString(), Map.of());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully"));
    }

    @GetMapping("/api/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return ResponseEntity.ok(UserDto.from(user));
    }
}
