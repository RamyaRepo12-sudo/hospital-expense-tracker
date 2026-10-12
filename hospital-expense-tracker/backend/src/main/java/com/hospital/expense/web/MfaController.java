package com.hospital.expense.web;

import com.hospital.expense.model.User;
import com.hospital.expense.repo.UserRepository;
import com.hospital.expense.security.JwtUtil;
import com.hospital.expense.security.LoginRateLimiter;
import com.hospital.expense.security.MfaPolicy;
import com.hospital.expense.security.SecretBox;
import com.hospital.expense.security.SessionCookies;
import com.hospital.expense.security.Totp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Two-factor authentication (authenticator app) for the signed-in user. Sensitive steps ask for the password again. */
@RestController
@RequestMapping("/api/account/mfa")
public class MfaController {
    public record Setup(@NotBlank @Size(max = 200) String password) {}
    public record Enable(@NotBlank @Size(max = 40) String code) {}
    public record Confirm(@NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 40) String code) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecretBox box;
    private final MfaService mfa;
    private final MfaPolicy policy;
    private final JwtUtil jwt;
    private final LoginRateLimiter limiter;
    private final AuditService audit;
    private final String issuer;

    public MfaController(UserRepository users, PasswordEncoder encoder, SecretBox box, MfaService mfa, MfaPolicy policy, JwtUtil jwt,
                         LoginRateLimiter limiter, AuditService audit, @Value("${app.mfa.issuer}") String issuer) {
        this.users = users; this.encoder = encoder; this.box = box; this.mfa = mfa; this.policy = policy; this.jwt = jwt;
        this.limiter = limiter; this.audit = audit; this.issuer = issuer;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private User me(Authentication a) {
        return users.findByUsername(a.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    private void requirePassword(User u, String password, HttpServletRequest req) {
        String key = "account|" + u.username + "|" + req.getRemoteAddr();
        if (limiter.blocked(key)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Please try again in 15 minutes.");
        if (!encoder.matches(password, u.passwordHash)) {
            limiter.fail(key);
            throw bad("Password is incorrect");
        }
    }

    private void reissue(User u, HttpServletRequest req, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.create(jwt.create(u), req.isSecure(), jwt.expirationMs()).toString());
    }

    @PostMapping("/setup")
    public Map<String, String> setup(@Valid @RequestBody Setup r, Authentication a, HttpServletRequest req) {
        User u = me(a);
        if (u.totpEnabled) throw bad("Two-factor authentication is already on");
        requirePassword(u, r.password(), req);
        String secret = Totp.newSecret();
        u.totpPendingEnc = box.seal(secret);
        users.save(u);
        return Map.of("secret", secret, "otpauthUri", Totp.otpauthUri(issuer, u.username, secret));
    }

    @PostMapping("/enable")
    public Map<String, List<String>> enable(@Valid @RequestBody Enable r, Authentication a, HttpServletRequest req, HttpServletResponse res) {
        User u = me(a);
        if (u.totpPendingEnc == null) throw bad("Start the setup first");
        String key = "account|" + u.username + "|" + req.getRemoteAddr();
        if (limiter.blocked(key)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Please try again in 15 minutes.");
        long step = Totp.verify(box.open(u.totpPendingEnc), r.code().trim(), System.currentTimeMillis(), 0);
        if (step < 0) {
            limiter.fail(key);
            throw bad("That code is not correct. Check that the time on your phone is automatic, then try again.");
        }
        u.totpSecretEnc = u.totpPendingEnc;
        u.totpPendingEnc = null;
        u.totpEnabled = true;
        u.totpLastStep = step;
        List<String> codes = mfa.issueRecoveryCodes(u);
        u.tokenVersion++; // signs out every other session
        users.save(u);
        reissue(u, req, res);
        audit.log(u.username, "Turned on two-factor authentication", u.username);
        return Map.of("recoveryCodes", codes);
    }

    @PostMapping("/disable")
    public Map<String, String> disable(@Valid @RequestBody Confirm r, Authentication a, HttpServletRequest req, HttpServletResponse res) {
        User u = me(a);
        if (policy.requiredFor(u)) throw bad("Your role requires two-factor authentication, so it cannot be turned off.");
        if (!u.totpEnabled) throw bad("Two-factor authentication is not on");
        requirePassword(u, r.password(), req);
        if (!mfa.verifyAndConsume(u, r.code())) throw bad("That code is not correct");
        mfa.clear(u);
        u.tokenVersion++;
        users.save(u);
        reissue(u, req, res);
        audit.log(u.username, "Turned off two-factor authentication", u.username);
        return Map.of("message", "Two-factor authentication is off");
    }

    @PostMapping("/recovery-codes")
    public Map<String, List<String>> newRecoveryCodes(@Valid @RequestBody Confirm r, Authentication a, HttpServletRequest req) {
        User u = me(a);
        if (!u.totpEnabled) throw bad("Two-factor authentication is not on");
        requirePassword(u, r.password(), req);
        if (!mfa.verifyAndConsume(u, r.code())) throw bad("That code is not correct");
        List<String> codes = mfa.issueRecoveryCodes(u);
        users.save(u);
        audit.log(u.username, "Generated new recovery codes", u.username);
        return Map.of("recoveryCodes", codes);
    }
}
