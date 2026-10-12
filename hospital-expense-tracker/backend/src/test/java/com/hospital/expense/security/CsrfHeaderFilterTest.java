package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfHeaderFilterTest {
    private MockHttpServletResponse run(String method, String uri, String headerValue) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        if (headerValue != null) req.addHeader(CsrfHeaderFilter.HEADER, headerValue);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new CsrfHeaderFilter().doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void blocksStateChangingApiCallsWithoutTheHeader() throws Exception {
        assertEquals(403, run("POST", "/api/expenses/1/approve", null).getStatus());
        assertEquals(403, run("DELETE", "/api/expenses/1", "something-else").getStatus());
    }

    @Test
    void allowsRequestsThatCarryTheHeader() throws Exception {
        assertEquals(200, run("POST", "/api/expenses/1/approve", "XMLHttpRequest").getStatus());
    }

    @Test
    void doesNotAffectReadsOrStaticFiles() throws Exception {
        assertEquals(200, run("GET", "/api/expenses", null).getStatus());
        assertEquals(200, run("POST", "/index.html", null).getStatus());
    }
}
