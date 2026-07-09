package com.example.template.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExternalApiService {

    private final RestClient restClient;

    public ExternalApiService(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Calls an external API with circuit-breaker protection.
     * @param endpoint the external API endpoint to call
     * @return the response body, or a degraded response only when the circuit is open
     */
    @CircuitBreaker(name = "externalApi", fallbackMethod = "externalApiFallback")
    @Observed(name = "external.api.call", contextualName = "external-api-request")
    public String callExternalApi(String endpoint) {
        log.info("Calling external API: {}", endpoint);
        return restClient.get()
                .uri(endpoint)
                .retrieve()
                .body(String.class);
    }

    /**
     * Fallback for an OPEN circuit only (fast-fail). Genuine call failures are deliberately NOT
     * caught here — they propagate so the caller sees the real error, and Resilience4j records
     * them until the breaker opens, at which point this degraded response is returned. This avoids
     * the anti-pattern of turning every outage into a 200 OK.
     */
    public String externalApiFallback(String endpoint, CallNotPermittedException ex) {
        log.warn("Circuit 'externalApi' is open; returning degraded response for endpoint: {}", endpoint);
        return "Service temporarily unavailable. Please try again later.";
    }

    /**
     * Demo method that always fails, to show the circuit breaker opening.
     * @return never returns normally; the fallback responds instead
     */
    @CircuitBreaker(name = "failingService", fallbackMethod = "failingServiceFallback")
    @Observed(name = "failing.service.call", contextualName = "failing-service-demo")
    public String callFailingService() {
        log.info("Calling failing service (demo)");
        throw new RuntimeException("Service is down for maintenance");
    }

    /**
     * Demo fallback that deliberately catches every failure so the demo endpoint always shows the
     * fallback path. In production code, prefer the {@code CallNotPermittedException}-typed fallback
     * above so real errors are not hidden.
     */
    public String failingServiceFallback(Throwable throwable) {
        log.warn("Failing service fallback triggered: {}", throwable.getMessage());
        return "Demo service is currently unavailable - this is expected behavior for testing";
    }
}
