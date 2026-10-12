package com.hospital.expense.web;

import com.hospital.expense.model.User;
import com.hospital.expense.repo.UserRepository;
import com.hospital.expense.security.JwtUtil;
import com.hospital.expense.security.LoginRateLimiter;
import com.hospital.expense.security.MfaPolicy;
import com.hospital.expense.security.SessionCookies;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}
    public record MfaRequest(@NotBlank @Size(max = 600) String mfaToken, @NotBlank @Size(max = 40) String code) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final LoginRateLimiter limiter;
    private final AuditService audit;
    private final MfaPolicy policy;
    private final MfaService mfa;
    private final SsoService sso;
    private final RecurringService recurring;
    private final boolean passwordLoginForEveryone;
    /** Compared against when the username does not exist, so response time does not reveal which usernames are real. */
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtUtil jwt, LoginRateLimiter limiter, AuditService audit,
                          MfaPolicy policy, MfaService mfa, SsoService sso, RecurringService recurring,
                          @Value("${app.sso.password-login}") boolean passwordLoginForEveryone) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.limiter = limiter; this.audit = audit;
        this.policy = policy; this.mfa = mfa; this.sso = sso; this.recurring = recurring;
        this.passwordLoginForEveryone = passwordLoginForEveryone;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    private Map<String, Object> view(User u, boolean viaSso) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.username); m.put("name", u.name); m.put("role", u.role); m.put("mustChangePassword", u.mustChangePassword);
        m.put("mfaEnabled", u.totpEnabled); m.put("mfaSetupRequired", !viaSso && policy.setupPending(u));
        return m;
    }

    private static String shorten(String s) { return s.length() > 40 ? s.substring(0, 40) : s; }

    private static ResponseEntity<Map<String, String>> reject(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }

    private ResponseEntity<?> startSession(User user, HttpServletRequest req) {
        audit.log(user.username, "Signed in", "-");
        recurring.generateDueAsync();
        String cookie = SessionCookies.create(jwt.create(user), req.isSecure(), jwt.expirationMs()).toString();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(view(user, false));
    }

    @GetMapping("/config")
    public Map<String, Object> config() { return Map.of("ssoEnabled", sso.enabled(), "passwordLogin", passwordLoginForEveryone); }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        String username = r.username().trim().toLowerCase();
        String key = username + "|" + req.getRemoteAddr();
        if (limiter.blocked(key)) {
            audit.log(shorten(username), "Sign-in blocked: too many failed attempts", "-");
            return reject(429, "Too many failed attempts. Please try again in 15 minutes.");
        }
        User user = users.findByUsername(username).orElse(null);
        boolean passwordOk = encoder.matches(r.password(), user == null ? dummyHash : user.passwordHash);
        if (user == null || !user.active || !passwordOk) {
            limiter.fail(key);
            audit.log(shorten(username), "Failed sign-in", "-");
            return reject(401, "Invalid username or password");
        }
        if (!passwordLoginForEveryone && !"SUPER_ADMIN".equals(user.role)) {
            audit.log(user.username, "Password sign-in refused: single sign-on is required", "-");
            return reject(403, "Password sign-in is turned off for your account. Use single sign-on.");
        }
        limiter.reset(key);
        if (user.totpEnabled) {
            String t = jwt.createScoped(user.username, "mfa", Map.of("tv", user.tokenVersion), 5 * 60_000L);
            return ResponseEntity.ok(Map.of("mfaRequired", true, "mfaToken", t));
        }
        return startSession(user, req);
    }

    @PostMapping("/mfa")
    public ResponseEntity<?> completeMfa(@Valid @RequestBody MfaRequest r, HttpServletRequest req) {
        Claims c;
        try {
            c = jwt.parseScoped(r.mfaToken(), "mfa");
        } catch (JwtException | IllegalArgumentException e) {
            return reject(401, "Your sign-in expired. Please enter your password again.");
        }
        String key = "mfa|" + c.getSubject() + "|" + req.getRemoteAddr();
        if (limiter.blocked(key)) {
            audit.log(shorten(c.getSubject()), "2FA blocked: too many failed attempts", "-");
            return reject(429, "Too many failed attempts. Please try again in 15 minutes.");
        }
        User u = users.findByUsername(c.getSubject()).orElse(null);
        Integer tv = c.get("tv", Integer.class);
        if (u == null || !u.active || !u.totpEnabled || u.tokenVersion != (tv == null ? 0 : tv)) {
            return reject(401, "Your sign-in expired. Please enter your password again.");
        }
        if (!mfa.verifyAndConsume(u, r.code())) {
            limiter.fail(key);
            audit.log(u.username, "Failed 2FA code", "-");
            return reject(401, "That code is not valid. Try the current code from your app, or a recovery code.");
        }
        limiter.reset(key);
        return startSession(u, req);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication a) {
        boolean viaSso = a.getDetails() instanceof Map<?, ?> d && Boolean.TRUE.equals(d.get("sso"));
        return users.findByUsername(a.getName()).<ResponseEntity<?>>map(u -> ResponseEntity.ok(view(u, viaSso)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication a, HttpServletRequest req) {
        if (a != null && a.isAuthenticated() && !"anonymousUser".equals(a.getName())) audit.log(a.getName(), "Signed out", "-");
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, SessionCookies.clear(req.isSecure()).toString()).body(Map.of("message", "Signed out"));
    }
}
