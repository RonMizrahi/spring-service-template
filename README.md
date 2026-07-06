# Spring Boot 3.5 Service Template

A thin, readable Spring Boot 3.5 (Java 25 LTS) template that demonstrates the common building
blocks of a service — auth, rate limiting, resilience, messaging, caching, observability — each
kept small enough to lift into a real project. It is a **reference / starting point**, not a
hardened production platform.

## Tech
- Spring Boot 3.5.16 on Java 25 (LTS) with virtual threads
- JWT authentication + role-based access control (method security)
- PostgreSQL (stg/prod) or in-memory H2 (dev)
- Redis caching and Kafka messaging (both feature-toggled)
- Resilience4j circuit breaker, Bucket4j rate limiting
- Micrometer + OpenTelemetry tracing to Zipkin
- springdoc OpenAPI / Swagger UI

## Profiles

Configuration is split by environment; pick one with `SPRING_PROFILES_ACTIVE` (defaults to `dev`).

| Profile | Database | Redis/Kafka | JWT secret | Seeded users | Schema |
|---------|----------|-------------|------------|--------------|--------|
| `dev`   | H2 in-memory | off by default (localhost when on) | dev default baked in | `admin`/`admin`, `user`/`user` | `create-drop` |
| `stg`   | PostgreSQL (env) | on | from `JWT_SECRET` (required) | none | `update` |
| `prod`  | PostgreSQL (env) | on | from `JWT_SECRET` (required) | none | `validate` (own your migrations) |

`stg`/`prod` inject secrets/hosts from the environment (docker/helm/etc.) and **fail fast** if
`JWT_SECRET` (and the Postgres credentials) are missing.

## Quick start

### Local, no dependencies (dev profile, H2)
```sh
./mvnw spring-boot:run
# → http://localhost:8080, seeds admin/admin and user/user
```

### Full local stack (Postgres + Redis + Kafka + Zipkin) via Docker
```sh
docker compose up --build -d          # core services (runs the stg profile)
docker compose --profile gui up -d    # + Adminer, Redis Commander, Kafka UI
docker compose logs -f app
```

Services: app `:8080` · Postgres `:5432` · Redis `:6379` · Kafka `:9092` · Zipkin `:9411` ·
Adminer `:8083` · Redis Commander `:8081` · Kafka UI `:8082` (GUIs need the `gui` profile).

### Authenticate
```sh
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# → {"token":"..."}  (use as: Authorization: Bearer <token>)
```

### Health / docs
```sh
curl http://localhost:8080/actuator/health
open http://localhost:8080/swagger-ui.html
```

## Component map

| Component | Files |
|-----------|-------|
| JWT auth | `config/SecurityConfig`, `config/JwtUtil`, `interceptor/JwtAuthenticationFilter`, `service/AuthService`, `controller/AuthController` |
| Role annotations | `config/AdminOnly`, `config/UserOnly` |
| Request/response logging + correlation id | `interceptor/RequestResponseLoggingInterceptor` (registered in `config/WebConfig`) |
| Rate limiting (per subscription plan) | `interceptor/SubscriptionRateLimitInterceptor`, `model/SubscriptionPlan` |
| Circuit breaker + outbound HTTP | `service/ExternalApiService`, `config/HttpClientConfig`, `controller/CircuitBreakerDemoController` |
| Redis caching (toggle `app.cache.enabled`) | `config/RedisConfig`, `cache/RedisCacheService` |
| Kafka messaging (toggle `app.messaging.enabled`) | `event/KafkaEventPublisher`, `event/KafkaEventListener` |
| Async on virtual threads | `service/LoginAuditService` (`@EnableAsync` on the app) |
| Custom metrics (AOP) | `aspect/PerformanceMonitoringAspect`, `config/PerformanceMonitoringConfig` |
| Error handling (RFC 7807) | `exceptions/GlobalExceptionHandler`, `exceptions/CustomException` |
| Health / OpenAPI | `config/DatabaseHealthIndicator`, `config/OpenApiConfig` |

Deeper notes live in [`docs/`](docs/) (circuit breaker, performance dashboard) and
[`docker/`](docker/).

## Configuration & secrets
- Secrets are env placeholders: `JWT_SECRET`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. `dev` supplies a
  local JWT default so it runs with zero setup; `stg`/`prod` require the real value.
- Copy `.env.example` to `.env` for Docker Compose.
- Feature toggles: `app.cache.enabled`, `app.messaging.enabled`, `app.init.add-admin`.

## Notes for production
- `prod` uses `ddl-auto: validate` — the schema is not managed by Hibernate. `docker/postgres/init-sql-schema.sql`
  is the baseline migration; carry schema changes forward as ordered SQL scripts.
- Never enable `app.init.add-admin` outside local dev.
- Actuator: `dev`/`stg` expose metrics behind authentication; `prod` exposes only `health` and `info`.
