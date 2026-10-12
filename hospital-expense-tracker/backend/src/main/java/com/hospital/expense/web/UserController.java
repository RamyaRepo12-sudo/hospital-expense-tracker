package com.hospital.expense.web;

import com.hospital.expense.model.User;
import com.hospital.expense.repo.UserRepository;
import com.hospital.expense.security.JwtUtil;
import com.hospital.expense.security.LoginRateLimiter;
import com.hospital.expense.security.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** User management (SUPER_ADMIN only) and self-service password change. */
@RestController
@RequestMapping("/api")
public class UserController {
    public record CreateUser(
            @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "[a-z0-9._-]+", message = "use lowercase letters, digits, dot, dash or underscore") String username,
            @NotBlank String name, @NotBlank String role, @NotBlank String password, @Email String email) {}
    public record Password(@NotBlank String password) {}
    public record Active(boolean active) {}
    public record ChangePassword(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "FINANCE_ADMIN", "DEPARTMENT_MANAGER", "ACCOUNTANT", "STAFF", "AUDITOR");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final JwtUtil jwt;
    private final LoginRateLimiter limiter;
    private final MfaService mfa;

    public UserController(UserRepository users, PasswordEncoder encoder, AuditService audit, JwtUtil jwt, LoginRateLimiter limiter, MfaService mfa) {
        this.users = users; this.encoder = encoder; this.audit = audit; this.jwt = jwt; this.limiter = limiter; this.mfa = mfa;
    }

    private static final List<String> COMMON = List.of("password", "12345678", "qwerty", "letmein", "welcome", "admin123", "iloveyou");

    static void checkPassword(String p) { checkPassword(p, null); }

    static void checkPassword(String p, String username) {
        String lower = p.toLowerCase();
        boolean weak = p.length() < 10 || p.length() > 100 || !p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")
                || COMMON.stream().anyMatch(lower::contains)
                || (username != null && username.length() >= 3 && lower.contains(username.toLowerCase()));
        if (weak) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be 10 to 100 characters with a letter and a number, and must not be a common password or contain your username");
        }
    }

    private static Map<String, Object> view(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id); m.put("username", u.username); m.put("name", u.name); m.put("role", u.role); m.put("active", u.active);
        m.put("email", u.email); m.put("mustChangePassword", u.mustChangePassword); m.put("mfaEnabled", u.totpEnabled);
        return m;
    }

    private User find(String id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Map<String, Object>> list() {
        return users.findAll().stream().sorted(Comparator.comparing((User u) -> u.username)).map(UserController::view).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> create(@Valid @RequestBody CreateUser r, Authentication a) {
        if (!ROLES.contains(r.role())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role");
        if (users.findByUsername(r.username()).isPresent()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        checkPassword(r.password(), r.username());
        User nu = new User(r.username(), encoder.encode(r.password()), r.name().trim(), r.role());
        nu.email = r.email() == null || r.email().isBlank() ? null : r.email().trim();
        if (nu.email != null && !users.findAllByEmailIgnoreCase(nu.email).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That email address is already used by another user");
        nu.mustChangePassword = true; // the new user must choose their own password at first sign-in
        User u = users.save(nu);
        audit.log(a.getName(), "Created user with role " + u.role, u.username);
        return view(u);
    }

    @PutMapping("/users/{id}/active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> setActive(@PathVariable String id, @RequestBody Active r, Authentication a) {
        User u = find(id);
        if (u.username.equals(a.getName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        u.active = r.active();
        u.tokenVersion++;
        users.save(u);
        audit.log(a.getName(), u.active ? "Activated user" : "Deactivated user", u.username);
        return view(u);
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> resetPassword(@PathVariable String id, @Valid @RequestBody Password r, Authentication a) {
        User u = find(id);
        checkPassword(r.password(), u.username);
        u.passwordHash = encoder.encode(r.password());
        u.mustChangePassword = true;
        u.tokenVersion++;
        users.save(u);
        audit.log(a.getName(), "Reset password", u.username);
        return view(u);
    }

    @PutMapping("/users/{id}/mfa/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> resetMfa(@PathVariable String id, Authentication a) {
        User u = find(id);
        mfa.clear(u);
        u.tokenVersion++;
        users.save(u);
        audit.log(a.getName(), "Reset two-factor authentication (lost device)", u.username);
        return view(u);
    }

    @PostMapping("/account/password")
    public Map<String, String> changeOwnPassword(@Valid @RequestBody ChangePassword r, Authentication a, HttpServletRequest req, HttpServletResponse res) {
        User u = users.findByUsername(a.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        String key = "account|" + u.username + "|" + req.getRemoteAddr();
        if (limiter.blocked(key)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Please try again in 15 minutes.");
        if (!encoder.matches(r.currentPassword(), u.passwordHash)) {
            limiter.fail(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        checkPassword(r.newPassword(), u.username);
        u.passwordHash = encoder.encode(r.newPassword());
        u.mustChangePassword = false;
        u.tokenVersion++; // signs out every other session
        users.save(u);
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.create(jwt.create(u), req.isSecure(), jwt.expirationMs()).toString());
        audit.log(u.username, "Changed own password", u.username);
        return Map.of("message", "Password changed");
    }
}
