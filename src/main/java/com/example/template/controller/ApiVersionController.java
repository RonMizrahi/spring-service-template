package com.example.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

/**
 * API Versioning demonstration controller.
 * Supports only URI-based versioning for API Gateway integration.
 */
@RestController
@RequestMapping("/api")
public class ApiVersionController {
    
    /**
     * Version 1 endpoint (URI-based).
     * Access via: /api/v1/status
     */
    @GetMapping("/v1/status")
    public ResponseEntity<Map<String, Object>> getStatusV1() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "version", "1.0",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Version 2 endpoint (URI-based).
     * Access via: /api/v2/status
     */
    @GetMapping("/v2/status")
    public ResponseEntity<Map<String, Object>> getStatusV2() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "version", "2.0",
            "timestamp", System.currentTimeMillis(),
            "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime()
        ));
    }
}
