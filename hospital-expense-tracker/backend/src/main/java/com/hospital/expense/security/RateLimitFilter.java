package com.hospital.expense.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** At most LIMIT API requests per minute per client address. Counters live in the RateStore (shared via MongoDB by default). */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    public static final int LIMIT = 240;
    private static final long WINDOW_MS = 60_000;

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final RateStore store;

    public RateLimitFilter(RateStore store) { this.store = store; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/api/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long n = 0;
        try {
            n = store.hit("api:" + req.getRemoteAddr(), WINDOW_MS);
        } catch (RuntimeException e) {
            log.warn("Rate limit store unavailable, allowing request: {}", e.getMessage()); // fail open for ordinary traffic
        }
        if (n > LIMIT) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Too many requests. Please slow down and try again in a minute.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
