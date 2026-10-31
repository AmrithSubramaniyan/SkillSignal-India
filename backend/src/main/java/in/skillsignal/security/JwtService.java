package in.skillsignal.security;

import in.skillsignal.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours:24}") long expirationHours
    ) {
        String padded = secret;
        while (padded.getBytes(StandardCharsets.UTF_8).length < 32) {
            padded = padded + "_skillsignal_padding_key_material";
        }
        this.secretKey = Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String generateToken(User user) {
        return generateToken(user, null);
    }

    public String generateToken(User user, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationHours, ChronoUnit.HOURS);
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));
        if (sessionId != null) {
            builder.claim("sessionId", sessionId.toString());
        }
        return builder.signWith(secretKey).compact();
    }

    public String generateUnsubscribeToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(30, ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("purpose", "SESSION_SUMMARY_UNSUBSCRIBE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
