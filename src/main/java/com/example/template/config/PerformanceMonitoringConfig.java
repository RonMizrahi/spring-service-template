package com.example.template.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Custom business metrics via Micrometer. JVM, system, process and HikariCP metrics are already
 * published by Spring Boot's auto-configuration (see management.metrics.enable.* in application.yaml),
 * so this only adds the domain counters/timers the {@code PerformanceMonitoringAspect} records.
 */
@Configuration
public class PerformanceMonitoringConfig {

    /**
     * Custom business metrics: authentication and external-API counters and timers.
     */
    @Component
    public static class BusinessMetrics {

        private final MeterRegistry meterRegistry;
        private final Counter loginAttempts;
        private final Counter loginSuccesses;
        private final Counter loginFailures;
        private final Timer authenticationTimer;
        private final Counter externalApiCalls;
        private final Timer externalApiTimer;

        public BusinessMetrics(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;

            this.loginAttempts = Counter.builder("auth.login.attempts")
                    .description("Total number of login attempts")
                    .register(meterRegistry);
            this.loginSuccesses = Counter.builder("auth.login.successes")
                    .description("Number of successful logins")
                    .register(meterRegistry);
            this.loginFailures = Counter.builder("auth.login.failures")
                    .description("Number of failed logins")
                    .register(meterRegistry);
            this.authenticationTimer = Timer.builder("auth.login.duration")
                    .description("Time taken for authentication")
                    .register(meterRegistry);

            this.externalApiCalls = Counter.builder("external.api.calls")
                    .description("Total number of external API calls")
                    .register(meterRegistry);
            this.externalApiTimer = Timer.builder("external.api.duration")
                    .description("Time taken for external API calls")
                    .register(meterRegistry);
        }

        public void recordLoginAttempt() {
            loginAttempts.increment();
        }

        public void recordLoginSuccess() {
            loginSuccesses.increment();
        }

        public void recordLoginFailure() {
            loginFailures.increment();
        }

        public Timer.Sample startAuthenticationTimer() {
            return Timer.start(meterRegistry);
        }

        public void recordAuthenticationTime(Timer.Sample sample) {
            sample.stop(authenticationTimer);
        }

        public void recordExternalApiCall() {
            externalApiCalls.increment();
        }

        public Timer.Sample startExternalApiTimer() {
            return Timer.start(meterRegistry);
        }

        public void recordExternalApiTime(Timer.Sample sample) {
            sample.stop(externalApiTimer);
        }
    }
}
