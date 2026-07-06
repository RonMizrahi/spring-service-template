package com.example.template.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Demonstrates {@code @Async} on virtual threads.
 *
 * <p>With {@code spring.threads.virtual.enabled=true}, Boot runs {@code @Async} methods on its
 * auto-configured virtual-thread executor — no custom {@code Executor} bean is needed. The work
 * runs off the request thread, so a slow audit sink never delays the login response. Note that
 * {@code @Async} only applies when the method is called from another bean (not via self-invocation),
 * which is why this lives in its own service.</p>
 */
@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    @Async
    public void recordLogin(String username) {
        log.info("Login recorded for user '{}' (virtual thread: {})",
                username, Thread.currentThread().isVirtual());
    }
}
