package com.example.template.interceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.template.model.SubscriptionPlan;
import com.example.template.model.entity.User;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-user rate limiting driven by the caller's subscription plan.
 *
 * <p>The plan is read from the authenticated {@link User} principal (placed in the context by the
 * JWT filter), so there is no per-request database lookup. Buckets are keyed by {@code username|plan}
 * so a plan change rebuilds the bucket rather than keeping the old limit.</p>
 *
 * <p>The bucket map is in-memory and not evicted — fine for a single instance or demo. A
 * multi-instance production service should use a distributed store (bucket4j-redis) with eviction.</p>
 */
@Component
public class SubscriptionRateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return true; // unauthenticated or non-user principal: nothing to rate limit
        }

        SubscriptionPlan plan = user.getSubscriptionPlan();
        String key = user.getUsername() + "|" + plan.name();
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(plan.getLimit()).build());

        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(429);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write("Rate limit exceeded");
        return false;
    }
}
