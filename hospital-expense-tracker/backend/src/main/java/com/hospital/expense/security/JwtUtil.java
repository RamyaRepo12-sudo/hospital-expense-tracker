package com.hospital.expense.security;

import com.hospital.expense.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String create(User u) { return create(u, false); }

    public String create(User u, boolean viaSso) {
        Date now = new Date();
        return Jwts.builder().subject(u.username).claim("role", u.role).claim("tv", u.tokenVersion).claim("sso", viaSso)
                .issuedAt(now).expiration(new Date(now.getTime() + expirationMs)).signWith(key).compact();
    }

    /** Short-lived single-purpose tokens ("mfa" step-up, "sso" login state). They are never accepted as a session. */
    public String createScoped(String subject, String purpose, java.util.Map<String, Object> extra, long ttlMs) {
        Date now = new Date();
        return Jwts.builder().subject(subject).claim("purpose", purpose).claims(extra)
                .issuedAt(now).expiration(new Date(now.getTime() + ttlMs)).signWith(key).compact();
    }

    public Claims parseScoped(String token, String purpose) {
        Claims c = parse(token);
        if (!purpose.equals(c.get("purpose", String.class))) throw new io.jsonwebtoken.JwtException("wrong token purpose");
        return c;
    }

    public long expirationMs() { return expirationMs; }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
