package com.hospital.expense.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence for the cookie session: every state-changing /api request must carry a custom header.
 * A cross-site page cannot add this header without a CORS preflight, and this API allows no cross-origin access.
 * Together with SameSite=Strict cookies this blocks forged requests.
 */
@Component
public class CsrfHeaderFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "XMLHttpRequest";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String m = req.getMethod();
        boolean safe = m.equals("GET") || m.equals("HEAD") || m.equals("OPTIONS") || m.equals("TRACE");
        return safe || !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (!VALUE.equals(req.getHeader(HEADER))) {
            res.setStatus(403);
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Missing required request header\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
