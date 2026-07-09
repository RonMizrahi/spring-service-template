package com.example.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.template.config.AdminOnly;
import com.example.template.service.ExternalApiService;

import lombok.extern.slf4j.Slf4j;

/**
 * Controller to demonstrate circuit breaker functionality.
 */
@RestController
@RequestMapping("/api/demo")
@Slf4j
public class CircuitBreakerDemoController {

    private final ExternalApiService externalApiService;

    public CircuitBreakerDemoController(ExternalApiService externalApiService) {
        this.externalApiService = externalApiService;
    }

    /**
     * Demo endpoint to test external API calls with circuit breaker.
     *
     * <p>SECURITY NOTE: this fetches a caller-supplied URL server-side, which is an SSRF vector.
     * It is admin-only and exists purely to demonstrate the circuit breaker. In real code, never
     * fetch an arbitrary user-supplied URL — use a fixed endpoint or a strict host allowlist.</p>
     */
    @GetMapping("/external-api")
    @AdminOnly
    public ResponseEntity<String> callExternalApi(@RequestParam(defaultValue = "https://httpbin.org/get") String endpoint) {
        log.info("Demo: Calling external API with circuit breaker protection");
        try {
            return ResponseEntity.ok(externalApiService.callExternalApi(endpoint));
        } catch (Exception e) {
            log.error("Demo: Error calling external API", e);
            return ResponseEntity.status(502).body("Upstream call failed: " + e.getMessage());
        }
    }

    /**
     * Demo endpoint to test a failing service circuit breaker.
     * @return the fallback response
     */
    @GetMapping("/failing-service")
    @AdminOnly
    public ResponseEntity<String> callFailingService() {
        log.info("Demo: Calling failing service to demonstrate circuit breaker");
        return ResponseEntity.ok(externalApiService.callFailingService());
    }

    /**
     * Get circuit breaker information.
     * @return information about circuit breaker usage
     */
    @GetMapping("/circuit-breaker-info")
    @AdminOnly
    public ResponseEntity<String> getCircuitBreakerInfo() {
        return ResponseEntity.ok(
            "Circuit Breaker Demo Endpoints:\n" +
            "- GET /api/demo/external-api?endpoint=<url> - Test external API with circuit breaker\n" +
            "- GET /api/demo/failing-service - Test always-failing service\n" +
            "- GET /actuator/circuitbreakers - View circuit breaker metrics\n" +
            "\nTry calling the failing-service endpoint multiple times to see the circuit breaker open!"
        );
    }
}
