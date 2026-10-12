package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    private int call(RateLimitFilter f, String ip, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void blocksAnAddressThatExceedsTheLimitButNotOthers() throws Exception {
        RateLimitFilter f = new RateLimitFilter(new MemoryRateStore(() -> 1_000_000L));
        for (int i = 0; i < RateLimitFilter.LIMIT; i++) assertEquals(200, call(f, "10.0.0.1", "/api/expenses"));
        assertEquals(429, call(f, "10.0.0.1", "/api/expenses"));
        assertEquals(200, call(f, "10.0.0.2", "/api/expenses"));
    }

    @Test
    void ignoresStaticFiles() throws Exception {
        RateLimitFilter f = new RateLimitFilter(new MemoryRateStore(() -> 1_000_000L));
        for (int i = 0; i < RateLimitFilter.LIMIT + 20; i++) assertEquals(200, call(f, "10.0.0.3", "/assets/app.js"));
    }
}
