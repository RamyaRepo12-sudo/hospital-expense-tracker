package com.hospital.expense.web;

import com.hospital.expense.model.User;
import com.hospital.expense.repo.UserRepository;
import com.hospital.expense.security.JwtUtil;
import com.hospital.expense.security.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {
    private static final Logger log = LoggerFactory.getLogger(SsoController.class);
    private static final String COOKIE = "hfcc_sso";

    private final SsoService sso;
    private final UserRepository users;
    private final JwtUtil jwt;
    private final AuditService audit;
    private final RecurringService recurring;

    public SsoController(SsoService sso, UserRepository users, JwtUtil jwt, AuditService audit, RecurringService recurring) {
        this.sso = sso; this.users = users; this.jwt = jwt; this.audit = audit; this.recurring = recurring;
    }

    /** Lax (not Strict) because the identity provider sends the browser back here from another site. It only carries the sign-in state. */
    private static ResponseCookie stateCookie(String value, boolean secure, Duration age) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite("Lax").path("/api/auth/sso").maxAge(age).build();
    }

    private static ResponseEntity<Void> redirect(String to, String... cookies) {
        ResponseEntity.BodyBuilder b = ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, to);
        for (String c : cookies) b.header(HttpHeaders.SET_COOKIE, c);
        return b.build();
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpServletRequest req) {
        if (!sso.enabled()) return ResponseEntity.notFound().build();
        try {
            SsoService.Start s = sso.start();
            return redirect(s.redirect(), stateCookie(s.stateToken(), req.isSecure(), Duration.ofMinutes(10)).toString());
        } catch (Exception e) {
            log.warn("SSO start failed: {}", e.getMessage());
            return redirect("/login?sso=failed");
        }
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         @CookieValue(name = COOKIE, required = false) String stateToken, HttpServletRequest req) {
        String clear = stateCookie("", req.isSecure(), Duration.ZERO).toString();
        if (!sso.enabled()) return ResponseEntity.notFound().build();
        try {
            if (error != null || code == null || state == null || stateToken == null) throw new IllegalStateException("incomplete callback" + (error == null ? "" : ": " + error));
            String email = sso.finish(code, state, stateToken);
            List<User> matches = users.findAllByEmailIgnoreCase(email);
            User u = matches.size() == 1 && matches.get(0).active ? matches.get(0) : null;
            if (u == null) {
                audit.log(email.length() > 60 ? email.substring(0, 60) : email, "SSO sign-in refused: no single active user with this email", "-");
                return redirect("/login?sso=denied", clear);
            }
            audit.log(u.username, "Signed in with SSO", "-");
            recurring.generateDueAsync();
            String session = SessionCookies.create(jwt.create(u, true), req.isSecure(), jwt.expirationMs()).toString();
            return redirect("/", session, clear);
        } catch (Exception e) {
            log.warn("SSO sign-in failed: {}", e.getMessage());
            audit.log("sso", "SSO sign-in failed", "-");
            return redirect("/login?sso=failed", clear);
        }
    }
}
