package com.novedadeslz.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ObjectMapper());
        chain = mock(FilterChain.class);
    }

    @Test
    void shouldBlockLoginAfterTenAttemptsFromSameIp() throws Exception {
        MockHttpServletResponse lastResponse = null;

        for (int attempt = 0; attempt < 11; attempt++) {
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(buildRequest("POST", "/api/auth/login", "203.0.113.10"), lastResponse, chain);
        }

        assertEquals(429, lastResponse.getStatus());
        // Las 10 primeras pasan, la 11 se corta antes de llegar al resto de la cadena.
        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void shouldTrackEachIpSeparately() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            filter.doFilter(
                    buildRequest("POST", "/api/auth/login", "203.0.113.10"),
                    new MockHttpServletResponse(),
                    chain
            );
        }

        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        filter.doFilter(buildRequest("POST", "/api/auth/login", "203.0.113.99"), otherIpResponse, chain);

        assertEquals(200, otherIpResponse.getStatus());
    }

    @Test
    void shouldMatchWildcardPathForYapeProof() throws Exception {
        MockHttpServletResponse lastResponse = null;

        for (int attempt = 0; attempt < 11; attempt++) {
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(
                    buildRequest("POST", "/api/orders/4821/yape-proof", "203.0.113.10"),
                    lastResponse,
                    chain
            );
        }

        assertEquals(429, lastResponse.getStatus());
    }

    @Test
    void shouldIgnoreEndpointsWithoutRule() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int attempt = 0; attempt < 50; attempt++) {
            filter.doFilter(buildRequest("GET", "/api/products", "203.0.113.10"), response, chain);
        }

        assertEquals(200, response.getStatus());
        verify(chain, times(50)).doFilter(any(), any());
    }

    private MockHttpServletRequest buildRequest(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Forwarded-For", ip);
        return request;
    }
}
