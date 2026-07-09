package com.example.template.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.template.model.Role;
import com.example.template.model.SubscriptionPlan;
import com.example.template.model.entity.User;

class SubscriptionRateLimitInterceptorTest {

    // Fresh interceptor (and bucket map) per test method — JUnit 5 defaults to PER_METHOD.
    private final SubscriptionRateLimitInterceptor interceptor = new SubscriptionRateLimitInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private User user(String name, SubscriptionPlan plan) {
        User u = new User();
        u.setUsername(name);
        u.setPassword("x");
        u.setRoles(Set.of(Role.USER));
        u.setSubscriptionPlan(plan);
        return u;
    }

    @Test
    void allowsUpToPlanCapacityThenRejects() throws Exception {
        authenticateAs(user("free-user", SubscriptionPlan.FREE)); // capacity 2

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void rejectionIs429WithRetryAfterHeader() throws Exception {
        authenticateAs(user("free-user", SubscriptionPlan.FREE));
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(allowed);
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
    }

    @Test
    void unauthenticatedRequestsAreNotRateLimited() throws Exception {
        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        assertTrue(allowed);
    }
}
