package com.example.template.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 configuration. Declares the JWT bearer scheme and applies it globally so Swagger UI's
 * "Authorize" button actually attaches the token to try-it-out requests. Public endpoints
 * (/auth/**, health/info) simply ignore the requirement.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Spring Service Template API",
        version = "1.0",
        description = """
            Spring Boot 3.5 microservice template:
            - JWT authentication & role-based authorization
            - Virtual threads
            - Circuit breaker (Resilience4j) and rate limiting (Bucket4j)
            - Distributed tracing and observability
            - Kubernetes-ready health probes
            """,
        contact = @Contact(name = "API Support", email = "support@example.com"),
        license = @License(name = "MIT License", url = "https://opensource.org/licenses/MIT")
    ),
    servers = @Server(url = "/", description = "Current host"),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT bearer token. Obtain one from POST /auth/login."
)
public class OpenApiConfig {
}
