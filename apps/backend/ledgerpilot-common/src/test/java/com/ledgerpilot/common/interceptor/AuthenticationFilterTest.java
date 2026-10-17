package com.ledgerpilot.common.interceptor;

import com.ledgerpilot.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

/** 认证实闸行为锁：豁免/401/租户上下文/作用域。 */
class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter();
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        JwtUtil.setSecret("unit-test-jwt-secret-0123456789abcdef-XYZ");
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void loginExempt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        run(req);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void internalPrefixExemptFromJwt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/rateconfig/internal/list");
        run(req);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void businessWithoutToken401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/voucher");
        MockHttpServletResponse resp = run(req);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void businessWithForgedToken401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/voucher");
        req.addHeader("Authorization", "Bearer forged");
        MockHttpServletResponse resp = run(req);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void validTokenSetsTenantContextAndClears() throws Exception {
        String token = JwtUtil.generate(1L, 9L, 604800000L, "ADMIN");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/voucher");
        req.addHeader("Authorization", "Bearer " + token);
        final Long[] seen = new Long[1];
        FilterChain capturing = (r, resp) -> seen[0] = com.ledgerpilot.common.util.TenantContext.get();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, capturing);
        assertEquals(9L, seen[0]);
        assertNull(com.ledgerpilot.common.util.TenantContext.get());
    }

    @Test
    void tokenWithoutTenantClaimRejected() throws Exception {
        String token = JwtUtil.generate(1L, null, 604800000L, "USER");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/voucher");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = run(req);
        assertEquals(401, resp.getStatus());
    }
}
