package in.skillsignal.security;

import in.skillsignal.session.SessionContextHolder;
import in.skillsignal.user.User;
import in.skillsignal.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                try {
                    Claims claims = jwtService.parseToken(token);
                    UUID userId = UUID.fromString(claims.getSubject());
                    DateIssuedCheck:
                    {
                        Optional<User> userOpt = userRepository.findById(userId);
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.EPOCH;
                            if (user.getTokenRevokedBefore() != null && !issuedAt.isAfter(user.getTokenRevokedBefore())) {
                                break DateIssuedCheck;
                            }
                            AuthenticatedUser principal = new AuthenticatedUser(
                                    user.getId(),
                                    user.getEmail(),
                                    user.getFullName(),
                                    user.getRole()
                            );
                            String roleName = user.getRole().startsWith("ROLE_") ? user.getRole() : "ROLE_" + user.getRole();
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(new SimpleGrantedAuthority(roleName))
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);

                            UUID resolvedSessionId = null;
                            String sessionHeader = request.getHeader("X-Session-Id");
                            if (sessionHeader != null && !sessionHeader.isBlank()) {
                                try {
                                    resolvedSessionId = UUID.fromString(sessionHeader.trim());
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                            if (resolvedSessionId == null) {
                                String claimSession = claims.get("sessionId", String.class);
                                if (claimSession != null && !claimSession.isBlank()) {
                                    try {
                                        resolvedSessionId = UUID.fromString(claimSession.trim());
                                    } catch (IllegalArgumentException ignored) {
                                    }
                                }
                            }
                            if (resolvedSessionId != null) {
                                SessionContextHolder.setSessionId(resolvedSessionId);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SessionContextHolder.clear();
        }
    }
}
