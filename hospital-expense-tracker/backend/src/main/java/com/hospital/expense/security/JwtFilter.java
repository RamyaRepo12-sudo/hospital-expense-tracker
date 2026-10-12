package com.hospital.expense.security;

import com.hospital.expense.model.User;
import com.hospital.expense.repo.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the session token (HttpOnly cookie, or a Bearer header for API clients), then re-loads the user.
 * The role always comes from the database, and a token whose version no longer matches (password changed or
 * reset, account deactivated, 2FA changed) is rejected immediately. Single-purpose tokens (2FA step, SSO state)
 * are never accepted as a session. Roles that must use 2FA are blocked from everything except 2FA setup until it is on.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwt;
    private final UserRepository users;
    private final MfaPolicy mfaPolicy;

    public JwtFilter(JwtUtil jwt, UserRepository users, MfaPolicy mfaPolicy) { this.jwt = jwt; this.users = users; this.mfaPolicy = mfaPolicy; }

    private static String tokenFrom(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) if (SessionCookies.NAME.equals(c.getName()) && !c.getValue().isBlank()) return c.getValue();
        }
        String h = req.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }

    static boolean allowedBeforeMfaSetup(String uri) {
        return !uri.startsWith("/api/") || uri.equals("/api/auth/me") || uri.equals("/api/auth/logout") || uri.equals("/api/auth/config") || uri.startsWith("/api/account/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String token = tokenFrom(req);
        if (token != null) {
            try {
                Claims c = jwt.parse(token);
                User u = c.get("purpose") != null ? null : users.findByUsername(c.getSubject()).orElse(null);
                Integer claimed = c.get("tv", Integer.class);
                if (u != null && u.active && u.tokenVersion == (claimed == null ? 0 : claimed)) {
                    boolean viaSso = Boolean.TRUE.equals(c.get("sso", Boolean.class));
                    if (!viaSso && mfaPolicy.setupPending(u) && !allowedBeforeMfaSetup(req.getRequestURI())) {
                        res.setStatus(403);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"message\":\"Set up two-factor authentication before continuing.\",\"code\":\"MFA_SETUP_REQUIRED\"}");
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(u.username, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role)));
                    auth.setDetails(Map.of("sso", viaSso));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid or expired token: the request stays unauthenticated and receives 401
            }
        }
        chain.doFilter(req, res);
    }
}
