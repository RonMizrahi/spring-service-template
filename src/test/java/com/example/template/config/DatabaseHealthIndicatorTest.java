package com.example.template.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for DatabaseHealthIndicator to verify database health monitoring.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseHealthIndicatorTest {

    @Autowired
    private DatabaseHealthIndicator databaseHealthIndicator;

    @Test
    @DisplayName("Database health indicator should report UP status for healthy database")
    void databaseHealthIndicator_shouldReportUpStatus() {
        // When
        Health health = databaseHealthIndicator.health();
        
        // Then
        assertNotNull(health, "Health should not be null");
        assertEquals(Status.UP, health.getStatus(), "Database should be UP");
        assertNotNull(health.getDetails(), "Health details should not be null");
    }

    @Test
    @DisplayName("Database health indicator should provide connection details")
    void databaseHealthIndicator_shouldProvideConnectionDetails() {
        // When
        Health health = databaseHealthIndicator.health();
        
        // Then
        assertNotNull(health.getDetails().get("database"), "Database detail should be present");
        assertNotNull(health.getDetails().get("connection"), "Connection detail should be present");
    }
}
