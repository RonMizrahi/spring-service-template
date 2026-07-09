package com.example.template.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.template.model.Role;
import com.example.template.model.SubscriptionPlan;
import com.example.template.model.entity.User;
import com.example.template.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Configuration class responsible for initializing application data on startup.
 * Handles creation of default admin user when enabled through configuration.
 * Only active when 'app.init.add-admin=true' property is set.
 * 
 * @author Backend Architect
 * @since 1.0
 */
@Configuration
@ConditionalOnProperty(name = "app.init.add-admin", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DataInitialization {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitialization.class);
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * Seeds two demo users if they do not already exist. Runs after the context is ready and
     * only when {@code app.init.add-admin=true} (dev only — never enable in stg/prod).
     *
     * <p>Passwords are hashed here through the application's {@link PasswordEncoder}, so no
     * plaintext or hand-written hash ever lives in SQL:</p>
     * <ul>
     *   <li>{@code admin} / {@code admin} — ROLE_ADMIN, PROFESSIONAL plan</li>
     *   <li>{@code user} / {@code user} — ROLE_USER, FREE plan</li>
     * </ul>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createDefaultUsers() {
        // Guard on the username actually created so the seed is idempotent on a persistent DB.
        if (userRepository.findByUsername("admin").isEmpty()) {
            logger.info("Seeding default demo users (admin/admin, user/user)");

            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("admin"));
            adminUser.setRoles(Set.of(Role.ADMIN));
            adminUser.setSubscriptionPlan(SubscriptionPlan.PROFESSIONAL);
            userRepository.save(adminUser);

            User regularUser = new User();
            regularUser.setUsername("user");
            regularUser.setPassword(passwordEncoder.encode("user"));
            regularUser.setRoles(Set.of(Role.USER));
            regularUser.setSubscriptionPlan(SubscriptionPlan.FREE);
            userRepository.save(regularUser);

            logger.warn("Seeded demo users with well-known passwords — for local development only.");
        } else {
            logger.debug("Demo users already exist, skipping seed");
        }
    }
}
