# Spring Service Template — Code Review

**Date:** 2026-07-06
**Reviewer:** Claude (hand review + multi-agent review with adversarial verification)
**Target:** `spring-service-template` — Spring Boot 3.5.3 / Java 24, ~3,200 lines. A hand-written reference template for "how to write common components in Spring." Stated goals: **as thin as possible, very clear, best practices, prefer standard library / Spring built-ins, only large well-known third-party deps.**

## Method & verification status

Two passes were combined:

1. **Hand review** — I read every main-source Java file, all config/resources, the Dockerfile, both compose files, and the tests directly.
2. **Multi-agent review** — 6 dimension reviewers (security, correctness, Spring idioms, testing, build/DevOps, template clarity) plus adversarial verifiers and a missing-components analysis.

The workflow was interrupted by a session token limit, so verification completed **fully only for the security dimension**. Findings are therefore tagged:

- **[verified]** — adversarially verified by an independent agent (or confirmed by me on a direct read of the code). Every P0 and most P1 items are in this category.
- **[reviewer]** — raised by a dimension reviewer but not independently re-verified this session. Still evidenced with file:line, but double-check before acting.
- **Missing components** were derived from my own analysis, since the two missing-components agents failed on the token limit before returning.

Line numbers reference the files as they are today.

---

## Executive summary

The template is well-scoped and the *selection* of components is genuinely good — JWT auth, role meta-annotations, rate limiting, circuit breaker, tracing, actuator, an AOP metrics aspect, request-logging with correlation IDs, a Kafka/Redis profile, and Docker tooling are exactly the "common components" a reference repo should show.

The problem is that **several of those components are currently broken or dead**, and because it's a template, every defect gets copied verbatim into real services. The most serious:

- **It does not build.** The Dockerfile copies a Maven wrapper (`mvnw`/`.mvn/`) that isn't in the repo, so the README's headline `docker compose --profile full up --build` fails at step one.
- **The flagship metrics aspect never fires** — its pointcuts point at a package (`com.example.accesscontrol`) that no longer exists, so every custom metric silently reads zero.
- **Two profiles crash on startup** — the `postgres` profile (invalid `spring.config.activate.on-profile` placement) and the `kafka-redis` profile (missing `RedisTemplate<String,Object>` bean + broken JAAS config).
- **Security defaults don't fail safe** — a committed JWT secret and a default `test-admin`/`password` admin are created out of the box.
- **The data-init guard is broken** and will crash the app on the second startup against any persistent database.

None of these are hard to fix, and none require abandoning the "thin" goal — most fixes *remove* code. Work the P0 list first (make it build and run), then P1 (security/correctness), then P2 (idioms), then the additions in P3.

**Finding counts (deduplicated):** 8 critical · 25 high · 46 medium · 38 low across the raw reviewer output; consolidated below into P0–P3.

---

## P0 — Broken: the template does not build or run as documented

### 1. Docker build fails — Maven wrapper is missing `[verified]`
**`Dockerfile:9-20`** copies `mvnw*` and `.mvn/` and runs `./mvnw dependency:go-offline` / `./mvnw clean package`, but the repo contains no `mvnw`, `mvnw.cmd`, or `.mvn/` directory. `COPY .mvn/ .mvn/` fails immediately, so the README's recommended first command (`docker compose --profile full up --build -d`, `README.md:27`) cannot succeed. Traces of the lost wrapper remain: `.gitattributes:1` sets EOL for `/mvnw`, `.gitignore` un-ignores the wrapper jar, `.dockerignore:24` excludes it, and `docker/README.md:96` tells users to run `./mvnw clean compile`. `README.md:62-65` also uses bare `mvn`, so a fresh clone can't build without a system Maven install.
**Fix:** Run `mvn wrapper:wrapper` and commit `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`; switch README commands to `./mvnw`. (Alternative: change the builder stage to `FROM maven:3.9-eclipse-temurin-24` and call `mvn` directly.) Then actually run the documented Docker path once to confirm it works end to end.

### 2. PerformanceMonitoringAspect targets a nonexistent package — all custom metrics are dead `[verified]`
**`aspect/PerformanceMonitoringAspect.java:29,59,79`** — all three pointcuts reference `com.example.accesscontrol.service.*` (a leftover from the project's former name; `application.properties.example:5` still says `spring.application.name=accesscontrol`), but the real services live in `com.example.template.service`. No join point ever matches, so `auth.login.attempts/successes/failures`, `external.api.*`, and `circuit.breaker.trips` never increment. `docs/performance-monitoring-dashboard.md` documents Grafana panels and alerts for metrics that are permanently zero. `MetricsEndpointAccessibilityTest` passes only because `BusinessMetrics` registers the meters eagerly in its constructor, and `PerformanceMonitoringAspectTest` only asserts the bean is non-null — the worst kind of silent breakage.
**Extra nuance (correct):** the `*Fallback(..)` pointcut (line 79) could never work even with the right package — Resilience4j invokes fallbacks reflectively on the target, bypassing the Spring AOP proxy.
**Fix:** Correct the package to `com.example.template.service`, delete the fallback advice (count trips from Resilience4j's built-in `resilience4j.circuitbreaker.calls` metrics instead), and add one behavioral test asserting `auth.login.attempts` increases after a login so a future rename fails loudly. Consider dropping the aspect entirely in favor of the `@Observed` annotations already on the services — one metrics mechanism is clearer than two.

### 3. `postgres` profile crashes on startup — invalid config-activation property `[verified]`
**`application-postgres.yaml:2-5`** uses `spring.config.activate.on-profile: postgres` inside a *profile-specific* file. Since Boot 2.4 that property is illegal in `application-<profile>.yaml` and throws `InvalidConfigDataPropertyException` at startup. `docker-compose.override.yml:14` sets `SPRING_PROFILES_ACTIVE=postgres` and compose auto-applies the override, so the documented Docker flow boots straight into this crash. Note `application-kafka-redis.yaml` correctly relies on the filename convention — the two files are inconsistent.
**Fix:** Remove `spring.config.activate.on-profile` (keep the `spring:` root). The filename already binds the document to the `postgres` profile.

### 4. `kafka-redis` profile crashes — `RedisCacheService` needs a bean nobody provides `[verified: code read; not runtime-executed]`
**`cache/RedisCacheService.java:10-12`** injects `RedisTemplate<String, Object>`. Boot's `RedisAutoConfiguration` only provides `RedisTemplate<Object,Object>` and `StringRedisTemplate`, and Spring's autowiring is generics-aware, so `<Object,Object>` is not a candidate for `<String,Object>` — activating the `kafka-redis` profile fails with `NoSuchBeanDefinitionException`. Even if wired to the default template, the JDK serializer produces binary keys/values and `set()` applies no TTL, so entries live forever.
**Fix:** Inject `StringRedisTemplate` and store JSON strings (thinnest), or define a small `@Bean RedisTemplate<String,Object>` with `StringRedisSerializer` keys + `GenericJackson2JsonRedisSerializer` values, and add a `set(key, value, Duration ttl)` overload. Better still, demonstrate `@EnableCaching`/`@Cacheable` (see P3) instead of a hand-rolled pass-through wrapper.

### 5. `DataInitialization` existence guard is broken — crashes on the 2nd startup against a real DB `[verified]`
**`config/DataInitialization.java:53`** checks `findByUsername("admin")` but lines 58/67 create `test-admin` and `test-user`. The guard can never match the users it creates, so seeding runs on every boot. It only appears to work because both H2 and the postgres profile use `ddl-auto: create-drop` (which wipes data each run). The moment someone copies this with a persistent `ddl-auto=update/validate`, the second startup violates the unique username constraint (`User.java:29`) inside the `ApplicationReadyEvent` listener and aborts startup. The javadoc (lines 43-47) and log (line 73) also say "admin", and lines 64-65 have a duplicate copy-paste comment.
**Fix:** Check `findByUsername("test-admin")` (extract the usernames to constants used in both places), fix the javadoc/log strings, remove the duplicate comment.

### 6. Kafka publisher and listener use different default topics — messages are lost `[verified]`
**`event/KafkaEventPublisher.java:22`** defaults `app.kafka.topic` to `topic2`; **`event/KafkaEventListener.java:16`** defaults it to `test-topic`. `app.kafka.topic` is set in no yaml/properties file, so out of the box the publisher writes `topic2` and the listener reads `test-topic` — every event is silently dropped (broker auto-create hides even an error). A produce/consume demo must round-trip by default.
**Fix:** Set `app.kafka.topic` once in `application-kafka-redis.yaml` and reference `${app.kafka.topic}` in both places without inline fallbacks — one source of truth.

### 7. `kafka-redis` SASL config is doubly broken `[verified]`
**`application-kafka-redis.yaml:13`** — the JAAS line ends with `password='${}'`, an empty placeholder that fails property resolution when the profile is active. Structurally, the `jaas.config` block is nested under `session:` (lines 9-13), so it binds to the nonexistent Kafka property `session.jaas.config` instead of `sasl.jaas.config`; combined with `security.protocol: SASL_SSL` the client can never authenticate. And the default `bootstrap-servers: localhost:9092` points at the compose broker, which is PLAINTEXT — SASL_SSL against it can't work either. This file exists specifically to teach Confluent Cloud SASL and teaches it wrong.
**Fix:** Move the entry to `spring.kafka.properties.sasl.jaas.config`, use real env placeholders (`username='${KAFKA_USERNAME:}' password='${KAFKA_PASSWORD:}'`), and make `security.protocol` configurable (`${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}`) so the same profile works against local Docker Kafka and Confluent Cloud.

### 8. Compose Kafka is unreachable in-network; Kafka UI points at a nonexistent listener `[verified]`
**`docker-compose.yml:89`** — `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092`. The app bootstraps at `kafka:9092` (line 16); the broker then redirects clients to `localhost:9092`, which inside the app container is the app itself, so all produce/consume fails. `kafka-ui` is pointed at `kafka:29092` (line 148), a listener that doesn't exist (only 9092 and controller 9093 are defined). This is the classic Docker dual-listener mistake.
**Fix:** Use the standard dual-listener pattern — `KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093`, `KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092`, matching `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` and `KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL`. Point the app and kafka-ui at `kafka:29092`, keep `localhost:9092` for host tools.

### 9. `data.sql` seeds plaintext passwords labeled as bcrypt — the seeded users can never log in `[verified]`
**`data.sql:2-6`** — the comment says "Passwords are bcrypt-hashed for `password`", but the values are literal `password1`…`password14`. `AuthService` uses `BCryptPasswordEncoder.matches()`, which returns false (with a "does not look like BCrypt" warning) for non-bcrypt values, so `admin1`/`user1`/etc. are dead accounts. The comment actively teaches storing plaintext credentials. The role inserts (lines 9-12) also hard-code `user_id` 1-4, which only works because `create-drop` resets identity counters.
**Fix:** Either seed a real bcrypt hash of `password` (with the plaintext noted in the comment) or — better — delete `data.sql` entirely, since `DataInitialization` already seeds working users through the encoder. Two competing seeding mechanisms is itself confusing in a thin template.

---

## P1 — Security & correctness

### 10. Hardcoded JWT secret ships as the working default and does not fail safe `[verified]`
**`application.yaml:33`** sets `security.jwt.secret` to a 75-char literal wired straight into `JwtUtil` (`JwtUtil.java:40`). It's self-labeled as a placeholder, but it's ≥32 chars so `JwtUtil`'s length guard (lines 42-44) passes and the app boots signing/verifying HS256 tokens with a key committed to the repo. Anyone deploying verbatim runs a service where an attacker can forge a valid token for any username. `docker-compose.yml` externalizes DB/Redis/Kafka settings via env vars but *not* the JWT secret — an internal inconsistency.
**Fix:** Use `secret: ${JWT_SECRET}` with no default so startup fails fast when unset (or a dev-only default under a `local` profile plus a startup check that rejects the known placeholder). Pass `SECURITY_JWT_SECRET` through compose. Also prefer `jwtSecret.getBytes(StandardCharsets.UTF_8)` at `JwtUtil.java:45` over the platform-default charset.

### 11. Default admin (`test-admin`/`password`) created on every deployment by default `[verified]`
**`application.yaml:109`** sets `app.init.add-admin: true`, so `DataInitialization` (P0 #5) runs by default and creates `test-admin`/`password` with `ROLE_ADMIN` plus `test-user`/`password`. Credentials are also documented in `README.md:72-91`, so every verbatim copy deploys with known admin credentials.
**Fix:** Default `app.init.add-admin: false` (enable only in a dev/local profile), read the bootstrap password from `${APP_INIT_ADMIN_PASSWORD}` instead of hardcoding, and fix the broken guard (P0 #5).

### 12. JWT filter returns 500 (not 401) on any invalid/expired token `[verified]`
**`interceptor/JwtAuthenticationFilter.java:37`** calls `jwtUtil.getUsernameFromToken(token)` *before* any validation. `parseSignedClaims` (`JwtUtil.java:73-74`) throws `SignatureException`/`ExpiredJwtException`/`MalformedJwtException` for any bad token, and `doFilterInternal` has no try/catch — so every request with a malformed, expired, or wrongly-signed Bearer token blows up as a 500 with a stack trace, instead of proceeding unauthenticated to a clean 401. The later `validateToken(token)` (line 42) is dead code for invalid tokens and a redundant second parse for valid ones. This is the most-copied JWT pattern in the repo and it's broken.
**Fix:** Parse once inside a try/catch — `try { username = jwtUtil.getUsernameFromToken(token); } catch (JwtException | IllegalArgumentException e) { log.debug(...); }` — then continue the chain unauthenticated on failure. Drop the separate `validateToken` call and load `UserDetails` only after a successful parse. Add negative-path tests (see #23).

### 13. `/actuator/**` is fully `permitAll` while metrics/env/circuitbreakers are exposed `[verified]`
**`config/SecurityConfig.java:73`** permits all of `/actuator/**` anonymously, and `application.yaml:36-53` exposes `health,info,circuitbreakers,metrics` with `management.info.env.enabled: true`. Anonymous callers can read JVM/datasource metrics, auth timing counters (an enumeration oracle), resilience state, and environment info. `docs/performance-monitoring-dashboard.md:166-178` claims these require admin auth and its examples use `curl -u admin:admin123` (Basic auth that is configured nowhere) — docs and code teach opposite postures. The wildcard also silently exposes anything added to the list later (`env`, `heapdump`, `loggers`).
**Fix:** Permit only `/actuator/health/**` and `/actuator/info`; require ADMIN for the rest (or serve management on a separate port). Update `MetricsEndpointAccessibilityTest`, which currently asserts anonymous 200 and cements the anti-pattern. Also drop the `url` detail from `DatabaseHealthIndicator.java:58` so the JDBC URL isn't leaked via health details.

### 14. JWT filter chain isn't stateless `[verified]`
**`config/SecurityConfig.java:71`** disables CSRF (only safe for a stateless bearer API) but never sets `sessionManagement(s -> s.sessionCreationPolicy(STATELESS))`, so Spring Security may still create `JSESSIONID` sessions — wrong for JWT and the #1 thing a JWT template must demonstrate. There's also no explicit `authenticationEntryPoint`, so unauthenticated requests to protected endpoints get a 403 (via `Http403ForbiddenEntryPoint`) instead of 401. `@EnableMethodSecurity(prePostEnabled = true, ...)` (line 19) is partly redundant (it defaults to true).
**Fix:** Add `.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` and `.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`. Two lines, and the CSRF-disable then documents a correct recipe.

### 15. Bean Validation is impossible as configured `[verified]`
**`pom.xml:139-143`** declares `jakarta.validation-api` (API only, explicit version) but not `spring-boot-starter-validation`, so there's no Hibernate Validator on the classpath — `@Valid`/`@NotBlank` would silently do nothing. Consistently, `LoginReq` (`model/LoginReq.java:3`) is a bare record and `AuthController.login` (`AuthController.java:27`) takes `@RequestBody LoginReq` with no `@Valid`. The template ships the "validation silently no-ops" trap.
**Fix:** Replace `jakarta.validation-api` with `spring-boot-starter-validation` (parent-managed version, drop the version property), annotate the record (`@NotBlank String username, @NotBlank String password`), add `@Valid` on the controller param, and handle `MethodArgumentNotValidException` (400) in the exception handler.

### 16. `UserDetailsService` throws bare `RuntimeException` `[verified]`
**`config/SecurityConfig.java:43`** — the lambda throws `new RuntimeException("User not found")`. The contract is `UsernameNotFoundException`, which Spring Security folds into the normal auth-failure flow. A bare `RuntimeException` bypasses that and, combined with the JWT filter (#12) loading the user before validating, a valid token for a since-deleted user propagates as a 500.
**Fix:** Throw `new UsernameNotFoundException(username)`.

### 17. Circuit-breaker fallback swallows all failures and returns HTTP 200 text `[reviewer]`
**`service/ExternalApiService.java:48`** — `fallbackResponse(String, Throwable)` catches *every* throwable (a 404 from the remote, DNS failure, or `CallNotPermittedException` when the breaker is open) and returns a 200 body `"Service temporarily unavailable..."`. Callers can't distinguish success from outage, and downstream JSON consumers get English prose. Consequently the try/catch in `CircuitBreakerDemoController.callExternalApi` (lines 35-41) is unreachable. This teaches "resilience = hide failures," the classic circuit-breaker misuse.
**Fix:** Add a fallback overload taking `CallNotPermittedException` for the open-circuit case, let genuine errors surface (or translate to a 503), and return 503 from the controller for outages.

### 18. Demo endpoint fetches an arbitrary caller-supplied URL (SSRF pattern) `[reviewer]`
**`controller/CircuitBreakerDemoController.java:33`** passes the raw `endpoint` query param straight into `restClient.get().uri(endpoint)` (`ExternalApiService.java:30-31`). `@IAdminRole` limits exposure, but the copy-paste lesson is "accept a URL and fetch it," which becomes textbook SSRF the moment the role is relaxed or the service method reused.
**Fix:** Call a fixed demo URL or validate against a hardcoded host allowlist, with a one-line "never fetch caller-supplied URLs" comment.

### 19. Rate limiter: bucket frozen at first request, per-request DB hit, unbounded map, login unprotected `[reviewer]`
**`interceptor/SubscriptionRateLimitInterceptor.java`** —
- `computeIfAbsent` (line 38) caches a bucket with the plan read at first request and never invalidates it, so a plan upgrade/downgrade never takes effect until JVM restart — yet the DB is queried on *every* request (line 35) precisely to read the current plan, then ignored.
- The `userBuckets` map is never evicted (one `Bucket` per username forever → unbounded memory).
- The principal placed in the `SecurityContext` by the JWT filter *is* the `User` entity (`User implements UserDetails`), so the per-request query is redundant.
- `getAuthentication()` (line 32) is dereferenced with no null check; it's non-null today only because Spring's anonymous filter runs, so any chain reordering makes it NPE on `/**`.
- WebConfig registers it on `/**` with `excludePathPatterns("/auth/**")` commented out (`WebConfig.java:30`), so a FREE-plan `test-user` (capacity 2) hits 429 while exploring; and `/auth/login` brute-force isn't throttled at all (anonymous principals skip the check).
- The 429 writes a bare string with no `Content-Type` and no `Retry-After`.
**Fix:** Read the plan from the authenticated `User` principal (no query), key the map by `username|plan` (or evict on change), guard the null authentication, set `Content-Type` + `Retry-After` on 429, and add a comment that production multi-instance services need `bucket4j-redis`. Add a small IP-keyed bucket for `/auth/login` or a comment saying brute-force protection is out of scope.

### 20. Login leaks username existence via timing `[reviewer]`
**`service/AuthService.java:28-34`** runs bcrypt only when the user exists; unknown usernames return in microseconds while known ones cost ~100ms. The uniform error message is good, but the timing gap still enables enumeration.
**Fix:** On the not-found path, run `passwordEncoder.matches(password, DUMMY_BCRYPT_HASH)` before throwing so both paths cost one bcrypt verification.

### 21. Kafka listener swallows all processing exceptions (ack-and-drop) `[reviewer]`
**`event/KafkaEventListener.java:23`** — the `catch (Exception ex) { log.error(...) }` means any future processing that throws is logged and the offset committed, so the message is lost with no retry/DLQ. As written it wraps only a `log.info` (dead code today), but it teaches ack-and-drop as the default.
**Fix:** Remove the try/catch and let exceptions reach Spring Kafka's `DefaultErrorHandler` (built-in retry/backoff), optionally with a `DeadLetterPublishingRecoverer`. A one-line comment pointing at `DefaultErrorHandler` teaches the right pattern.

---

## P2 — Idioms, correctness-of-example, and polish

### 22. Inconsistent dependency injection — field `@Autowired` in 7 classes `[reviewer]`
Field injection in `AuthService:18-23`, `AuthController:23`, `CircuitBreakerDemoController:23`, `ExternalApiService:15`, `SubscriptionRateLimitInterceptor:26`, `WebConfig:15-19`, `PerformanceMonitoringAspect:23`; constructor injection everywhere else (`JwtUtil`, Kafka classes, `RedisCacheService`, and Lombok `@RequiredArgsConstructor` in `DataInitialization`/`DatabaseHealthIndicator`). A style reference shouldn't teach both, and field injection is the one Spring's docs discourage. (The bundled copilot instructions even mandate constructor injection — which the code then violates.)
**Fix:** Standardize on constructor injection (plain or Lombok `@RequiredArgsConstructor`, pick one) with `final` fields; drop the `@Autowired`.

### 23. `GlobalExceptionHandler` — wrong advice type, misnamed, never-thrown exception, no ProblemDetail `[reviewer]`
**`exceptions/GlobalExceptionHandler.java`** — for a REST service this should be `@RestControllerAdvice`; the only handler is for `CustomException` (thrown nowhere in the codebase) yet named `handleAllExceptions` and hardcodes 500/"Internal Server Error"; it echoes `ex.getMessage()` verbatim (info leak); and there's no handler for validation errors. Meanwhile `AuthController.login` hand-rolls its own 401 Map (`AuthController.java:31-33`) — the exact duplication a global advice removes. Boot 3's idiom is RFC 7807 `ProblemDetail`.
**Fix:** Use `@RestControllerAdvice extends ResponseEntityExceptionHandler`; return `ProblemDetail` for `BadCredentialsException` (401) and `MethodArgumentNotValidException` (400); give `CustomException` an `HttpStatus` field (or delete it); remove the try/catch from `AuthController`.

### 24. `VirtualThreadConfig` is unnecessary and suppresses Boot's task executor `[reviewer]`
**`config/VirtualThreadConfig.java:41`** — with `spring.threads.virtual.enabled: true` already set (`application.yaml:8`), Boot 3.2+ auto-configures the Tomcat executor and a virtual-thread `applicationTaskExecutor` for `@Async`. Defining an `Executor` bean makes `TaskExecutionAutoConfiguration` back off (`@ConditionalOnMissingBean(Executor.class)`), replacing Boot's context-propagating, shutdown-aware executor with a raw one that loses `TaskDecorator`/observation propagation and breaks MVC async. There are also zero `@Async` methods, so `@EnableAsync` is dead weight. This teaches exactly the wrong lesson for a "prefer built-ins" template.
**Fix:** Delete the class; the single property is the whole story. Add a comment next to it in `application.yaml`. If you want to demonstrate `@Async`, add `@EnableAsync` on the app class plus one real `@Async` method.

### 25. `RestClient.create()` bypasses Boot's auto-configured builder `[reviewer]`
**`config/WebConfig.java:34`** — `RestClient.create()` ignores Boot's `RestClient.Builder`, which is wired with the `ObservationRegistry` (`http.client.requests` metrics + W3C trace-context propagation) and has no connect/read timeouts. So the template's flagship tracing feature doesn't propagate trace headers on the very outbound calls `ExternalApiService` makes, and those calls can hang forever.
**Fix:** `RestClient restClient(RestClient.Builder builder) { return builder /* .requestFactory(withTimeouts) */ .build(); }`, in its own small `@Configuration` (not mixed into the `WebMvcConfigurer`). Add explicit connect/read timeouts via `ClientHttpRequestFactorySettings`.

### 26. `BusinessMetrics` — dead gauges and odd structure `[reviewer]`
**`config/PerformanceMonitoringConfig.java`** —
- `jvm.threads.virtual.count` (lines 88-90, 172-187) walks `ThreadMXBean.getAllThreadIds()`, which by spec only returns *platform* threads, so it's always ~0 while doing an O(all threads) scan per scrape.
- The Hikari gauges (lines 93-112) call `getHikariPoolMXBean()` at construction, before the pool starts lazily, so they capture `null` and read 0 forever — and they duplicate Boot's built-in `hikaricp.connections.*` (already enabled at `application.yaml:77`).
- `activeUsers` (line 77) increments on login but nothing decrements it, so it counts cumulative logins, not active users; `databaseConnections` (line 83) is never set.
- `BusinessMetrics` is a nested `@Component` inside an otherwise-empty `@Configuration`, forcing the awkward `PerformanceMonitoringConfig.BusinessMetrics` import (`PerformanceMonitoringAspect.java:9`).
**Fix:** Promote `BusinessMetrics` to a top-level `metrics/BusinessMetrics.java`, delete the empty wrapper, delete the virtual-thread and Hikari gauges (rely on Boot's built-ins) and the unused `activeUsers`/`databaseConnections`, keeping just the login counters that demonstrate the pattern.

### 27. Graceful-shutdown timeout is at the wrong YAML level (silent no-op) `[verified]`
**`application.yaml:23-24`** puts `lifecycle.timeout-per-shutdown-phase: 30s` at the document root; the real key is `spring.lifecycle.timeout-per-shutdown-phase` (`application.properties.example:29` has it right). Spring ignores the unknown root key — invisible today only because 30s is the default. A copier who changes the value gets no effect.
**Fix:** Indent `lifecycle:` under the existing `spring:` block.

### 28. App-name typo `sprint-boot` propagates into all logs and traces `[verified]`
**`application.yaml:5`** — `name: sprint-boot-3.5-template`. `spring.application.name` is stamped into every log line (`logging.pattern.level`, line 132) and every Zipkin trace.
**Fix:** Rename to `spring-service-template` (matching the artifactId).

### 29. Console log pattern never renders trace IDs `[verified: code read]`
**`application.yaml:129-132`** — `logging.pattern.console` is fully custom and uses `%5p`, so the `logging.pattern.level` you defined (with `%X{traceId}`/`%X{spanId}`) is never applied to console output. The tracing story the template advertises doesn't actually show correlation IDs in the logs it prints.
**Fix:** Either drop the custom console pattern (let Boot's default use `LOG_LEVEL_PATTERN`, which includes your level pattern) or embed `%X{traceId:-},%X{spanId:-}` directly in the console pattern.

### 30. `application.properties.example` is a stale, drifting duplicate `[reviewer]`
**`application.properties.example`** duplicates most of `application.yaml` and has already drifted: `spring.application.name=accesscontrol` (line 5), admin credentials documented as `admin/admin123` (line 93) vs the actual `test-admin/password`, and mixed properties/YAML syntax. `.example` config files aren't a Spring convention — the convention is committing `application.yaml` with `${ENV:default}` placeholders, which `application-postgres.yaml` already does. Two config sources guarantee drift.
**Fix:** Delete it; move the one real secret (JWT) to an env placeholder in `application.yaml`.

### 31. Package placement teaches the wrong mental model `[reviewer]`
`config/` holds non-configuration beans — `JwtUtil` (a token service), `DataInitialization` (a startup runner), `DatabaseHealthIndicator` (an actuator component), and the role annotations. `interceptor/` mixes a `jakarta.servlet` `Filter` (`JwtAuthenticationFilter`) with Spring MVC `HandlerInterceptor`s — different mechanisms, different lifecycles. So "the JWT setup" is scattered across `config/`, `interceptor/`, `model/`, and `service/`.
**Fix:** Create a `security/` package holding `SecurityConfig`, `JwtUtil`, `JwtAuthenticationFilter`, and the role annotations, so "lift the JWT setup" is a copy of one directory. Keep `config/` strictly for `@Configuration`.

### 32. `IAdminRole`/`IUserRole` naming + dead null-checks `[reviewer]`
The meta-annotation pattern is good Spring, but the `I` prefix is a C# interface convention (these are annotations). In `AuthController` the `authentication == null` guards (lines 40-42, 52-54) are unreachable — `@PreAuthorize` already rejects unauthorized callers — and use raw `.status(401)/.status(403)` magic numbers.
**Fix:** Rename to `@AdminOnly`/`@UserOnly` (or `@RequireAdmin`), delete the unreachable branches, use `HttpStatus` constants.

### 33. `ApiVersionController` fabricates uptime `[reviewer]`
**`controller/ApiVersionController.java:50-53`** — `getStartTime()` returns `System.currentTimeMillis() - 60000` with a "in real app this would be tracked properly" comment. The real value is a one-liner: `ManagementFactory.getRuntimeMXBean().getUptime()`. A template should never ship a "this is fake" comment where the real thing costs nothing. The hardcoded `"health": "UP"` also duplicates and can contradict actuator. Responses use untyped `Map<String,Object>` instead of a record DTO.
**Fix:** Use `getRuntimeMXBean().getUptime()`, drop the `health` field, return small records (e.g. `record StatusV2(String status, String version, Instant timestamp, long uptimeMs) {}`).

### 34. `OpenApiConfig` overpromises relative to the code `[reviewer]`
**`config/OpenApiConfig.java`** — the `APIKEY` scheme (lines 58-62) is incomplete (no `in`/`paramName`) and backed by nothing; the `https://api.example.com` server (lines 45-48) is fictional; and no `@SecurityRequirement` is attached to any controller, so Swagger's Authorize button doesn't gate try-it-out requests. Declared version `2.0` is unrelated to the artifact version.
**Fix:** Delete the API-Key scheme and fake server; add `security = @SecurityRequirement(name = "Bearer Authentication")` on `@OpenAPIDefinition` so the JWT scheme actually wires to secured endpoints.

### 35. `postgres` profile uses `ddl-auto: create-drop` — data destroyed every restart `[reviewer]`
**`application-postgres.yaml:14`** — the "looks like production" profile drops the schema on shutdown, making the `postgres_data` volume and the mounted init script pointless, and contradicting the README's "persistent storage." This is the single most dangerous property to copy into production.
**Fix:** `ddl-auto: validate` with a real schema (see P3 migrations) or `update` for a dev template, with a comment that production should use Flyway/Liquibase.

### 36. Java 24 is a non-LTS release `[reviewer]`
**`pom.xml:32`** (`java.version` 24) + `eclipse-temurin:24-*-alpine` (`Dockerfile:3,23`). Java 24 is a 6-month non-LTS (GA March 2025, superseded Sept 2025), so its Temurin tags no longer receive security patches. Everything used here (virtual threads, Boot 3.5) runs identically on Java 21 LTS. README also implies virtual threads need 24 (they've been final since 21).
**Fix:** Move to Java 21 LTS (set `java.version` to 21, use `temurin:21-*-alpine`). The `maven.compiler.proc=full` Lombok workaround can stay harmlessly.

### 37. `pom.xml` hygiene `[reviewer]`
`jakarta.validation-api` is dead weight (see #15) and its explicit version overrides the Boot BOM; Lombok is pinned to `1.18.38` which the BOM already manages; `<project.version>0.0.1-SNAPSHOT</project.version>` (line 33) shadows a reserved Maven property and can leave `@project.version@` filtering stale.
**Fix:** Replace `jakarta.validation-api` with `spring-boot-starter-validation` (no version); drop `<lombok.version>` and the Lombok version tag (keep `provided`); delete the `<project.version>` and `<jakarta.validation.version>` properties.

### 38. Dockerfile runtime flags are cargo-cult `[reviewer]`
**`Dockerfile:52`** — `-XX:+UseContainerSupport` (default since JDK 10), `-XX:+UseG1GC` (default collector), and `-XX:+UnlockExperimentalVMOptions` (unlocks nothing here) all do nothing; only `-XX:MaxRAMPercentage=75.0` earns its place. The `sh -c "java $JAVA_OPTS ..."` form (line 58) exists only to expand `JAVA_OPTS`, and `dumb-init` exists to compensate for that shell wrapper.
**Fix:** Trim to `-XX:MaxRAMPercentage=75.0`; use exec-form `ENTRYPOINT ["java","-jar","app.jar"]` and let users tune via `JAVA_TOOL_OPTIONS`. Optionally demonstrate Boot's layered-jar extraction (`-Djarmode=layertools`) — the documented Spring Docker best practice, ~6 lines. (The non-root user and slim JRE stage are good — keep them.)

### 39. Compose: unpinned `:latest` tags, missing health-gated `depends_on`, LAN-exposed ports `[reviewer]`
- Five images use `:latest` (`docker-compose.yml:79,108,128,144,161`) while postgres/redis are pinned — inconsistent and non-reproducible; `cp-kafka:latest` is the riskiest (KRaft/CLUSTER_ID format can change between pulls).
- The app's only `depends_on` is `zipkin` (lines 18-19), short-form, despite postgres/redis/kafka all defining healthchecks that are never used — teaching that healthchecks and `depends_on` are unrelated.
- Ports bind `0.0.0.0` (Postgres 5432, passwordless Redis 6379, GUIs 8081-8083), and the auto-applied override exposes unauthenticated JDWP on 5005 (`suspend=n` = remote code execution for anyone who can reach it).
**Fix:** Pin majors (`cp-kafka:7.7.x`, `zipkin:3`, `adminer:4`, `kafka-ui:v0.7.2`); use `depends_on: { postgres: { condition: service_healthy }, ... }`; bind dev ports to `127.0.0.1:`.

### 40. `docker-compose.override.yml` promises hot reload that can't work `[reviewer]`
**`docker-compose.override.yml:8-13`** mounts `./target/classes` and sets `SPRING_DEVTOOLS_RESTART_ENABLED=true`, but (1) `spring-boot-devtools` isn't in `pom.xml`, (2) devtools disables itself under `java -jar`, and (3) the mounted dir is never on the classpath. The debug flags also use legacy `-Xdebug -Xrunjdwp` instead of `-agentlib:jdwp`. `docker/README.md:101` repeats the false claim.
**Fix:** Delete the classes volume and devtools env vars; keep the override focused on remote debug via `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`. Document that hot reload means running `./mvnw spring-boot:run` on the host against compose-managed dependencies.

### 41. The `kafka-redis` profile is never activated by any documented path `[reviewer]`
`RedisCacheService`, `KafkaEventListener`, `KafkaEventPublisher` are all `@Profile("kafka-redis")`, but nothing activates it: compose defaults to `default` (`docker-compose.yml:10`), the override forces `postgres` (`docker-compose.override.yml:14`), and the README never mentions the profile. So the advertised Redis caching and Kafka messaging silently never run while the containers do. `application-postgres.yaml:22-45` also duplicates Redis/Kafka client config that nothing consumes.
**Fix:** Have the `full` compose profile set `SPRING_PROFILES_ACTIVE=postgres,kafka-redis` (after fixing the kafka-redis yaml), document the profile in the README, and remove the duplicated Redis/Kafka block from `application-postgres.yaml`.

### 42. The `minimal` compose profile can't start the app `[reviewer]`
`docker-compose.override.yml:14` hard-sets `SPRING_PROFILES_ACTIVE=postgres` unconditionally, and `docker-compose.yml:11` always injects a postgres `SPRING_DATASOURCE_URL`. In `minimal` (app + zipkin only) no postgres runs, so the app crash-loops; and under the plain `default` profile the postgres URL collides with `application.yaml`'s H2 driver/dialect.
**Fix:** Don't set a datasource URL default in the base app env; let the `default` profile use H2 and inject the postgres URL/profile only when postgres actually runs. Remove `driverClassName`/`database-platform` from `application.yaml` so Spring can deduce the driver from any overridden URL.

---

## Testing findings `[reviewer]`

The best test in the suite is `AuthControllerIntegrationTest` — a genuine integration test (real filter chain, real JWT, real BCrypt, H2 via MockMvc) with meaningful role-based assertions. Keep that as the model. The rest has recurring problems:

- **No test resources/profile** — `src/test/resources` doesn't exist, so every `@SpringBootTest` boots the production `application.yaml`: tracing at 100% sampling exporting to a dead Zipkin (connection-failure noise + shutdown delay), `show-sql` on, and a single fixed `jdbc:h2:mem:testdb` shared across all ~5 cached contexts. Add `src/test/resources/application-test.yaml` disabling tracing/`show-sql` and using a per-context DB name. **Highest-value, two lines.**
- **No Testcontainers** — the advertised Postgres/Redis/Kafka have zero integration coverage; all "integration" tests run on H2 while the real profile uses Postgres. `RedisCacheServiceTest` is pure mock-echo (`verify(valueOperations).set(...)` restates the one-line impl). Add `spring-boot-testcontainers` + Postgres/Kafka modules with `@ServiceConnection` — the single biggest teaching gap.
- **`@SpringBootTest` overuse, zero slice tests** — `PerformanceMonitoringConfigTest`/`AspectTest` stack `@ExtendWith(MockitoExtension)` + `@SpringJUnitConfig` + `@SpringBootTest` then use no Spring bean; `SecurityConfigTest` boots the whole app to test that BCrypt encodes. No `@WebMvcTest` or `@DataJpaTest` anywhere. Add one of each (`ApiVersionController` for `@WebMvcTest`, `UserRepository` for `@DataJpaTest`) and strip the others to plain JUnit+Mockito.
- **Test helper `JwtConfig`** — a `@TestConfiguration` named like production config, in the same package as the real `JwtUtil`, that parses the token by substring (`indexOf(":\"")`) and silently returns error text as a "token" on a failed login. Rename to `AuthTestSupport`, assert `status().isOk()`, parse with `ObjectMapper`.
- **`ExternalApiServiceTest` hits live `httpbin.org`** — flaky/offline-failing, and its "circuit opens" test makes only 3 calls against a `minimum-number-of-calls: 5` config and never asserts breaker state. Use `MockRestServiceServer`; for the breaker test make ≥5 failing calls and assert `getState() == OPEN` with a `@BeforeEach` reset.
- **Missing negative-path tests** — no test sends a malformed/expired token (would expose #12's 500), no-header access, an unknown-user token, or blank login fields.
- **Latent flakiness** — the live rate limiter + FREE-plan `test-user` leaves the suite exactly at the 429 threshold; one added `test-user` request breaks unrelated tests. Seed test users with a high-capacity plan or reset the interceptor in `@BeforeEach`.
- **Mock-echo assertions** — `RequestResponseLoggingInterceptorTest.testXForwardedForHandling` re-stubs `X-Forwarded-For` to `null` (so the named scenario never runs) and only `verify()`s that a getter was called; `AuthServiceTest` misses the wrong-password branch; `CircuitBreakerIntegrationTest` asserts only static strings.
- **Coverage gaps (zero direct tests):** `JwtUtil`, `JwtAuthenticationFilter`, `GlobalExceptionHandler`, `KafkaEventPublisher`, `KafkaEventListener`, `DataInitialization`, `WebConfig`, `UserRepository`. A trivial `DataInitialization` test would have caught the #5 guard bug.
- **Inconsistent style** — two Mockito bootstrapping styles (`openMocks` vs `@ExtendWith(MockitoExtension.class)`), mixed `@DisplayName` vs bare `testXxx` naming, no failsafe/surefire split so slow/online tests run in `mvn test`.

---

## Missing components (from my analysis — the missing-components agents didn't complete)

These are patterns a "common Spring components" template should arguably demonstrate, none of which are present, all of which fit the thin-template goal:

| Component | Why it belongs | Priority |
|---|---|---|
| **DB migrations (Flyway/Liquibase)** | Replaces the taught anti-pattern of `ddl-auto` + `data.sql` in a "production-ready" template. Boot auto-configures Flyway from one dependency. | Essential |
| **Maven wrapper (`mvnw`)** | Already assumed by the Dockerfile and docs; its absence breaks the build (P0 #1). | Essential |
| **`spring-boot-starter-validation` + `@Valid` example** | The template ships the validation API but no implementation and no working example (#15). | Essential |
| **CI workflow** | `.github` holds only a copilot-instructions example; no proof it builds/tests. ~20 lines: checkout, setup-java (cache: maven), `./mvnw -B verify`, optionally `docker build .`. A scratchpad shows CI was planned. | Recommended |
| **Testcontainers integration tests** | The advertised infra has no real integration coverage; `@ServiceConnection` is the modern Boot pattern readers most need. | Recommended |
| **`logback-spring.xml` structured/JSON logging** | Cloud-native services log JSON; also fixes #29 (trace IDs not in console). | Recommended |
| **RFC 7807 `ProblemDetail` error responses** | Boot 3's standard error shape; the current handler teaches the opposite (#23). | Recommended |
| **Declarative caching (`@EnableCaching`/`@Cacheable`)** | The idiomatic Spring caching pattern vs the manual `RedisCacheService` wrapper. | Recommended |
| **RestClient timeout configuration** | `RestClient.create()` has no timeouts (#25) — a real outbound-call footgun. | Recommended |
| **A CRUD REST resource with pagination/sorting + DTO mapping** | The repo has auth + demo endpoints but no example of the most common thing services do: a paged CRUD resource returning DTOs (not entities). | Optional |
| **`spring-boot-devtools` dependency** | Referenced by the compose override but absent from `pom.xml` (#40). | Optional |
| **`.env.example` for compose** | `docker/README.md:57` tells users to `cp .env.example .env`, but no such file exists. | Optional |
| **In-process `ApplicationEventPublisher` example** | The synchronous counterpart to the Kafka demo — a common component worth contrasting. | Optional |
| **Prometheus registry** | Only the in-memory `simple` metrics export is enabled; most deployments scrape Prometheus. | Optional |

---

## Cleanup — remove working artifacts that dilute a thin template

- **`scratchpads/`** — two AI planning docs (~465 lines) full of stale `com.example.accesscontrol` references and process scaffolding ("Questions Asked [3/3 REQUIRED]", confidence ratings). Delete or gitignore.
- **`image.png`** — a 19 KB screenshot at the repo root, referenced mid-bullet-list at `README.md:43`. Move to `docs/` with a descriptive name and a deliberate reference, or drop it.
- **`.github/copilot-instructions.md-example`** — a 43-line AI persona prompt (with typos like "speciallized") whose own rules (constructor injection, DTO validation, never expose entities) the template code violates. Delete or trim to rules the code actually follows.
- **`application.properties.example`** — see #30, delete.
- **README** — no component map, empty `## API Usage` section, a headingless troubleshooting block dangling under "Key Features", the feature list duplicated verbatim (lines 5-14 vs 99-108), `docs/` never linked, and false claims of "auto-initialized schema" (the init SQL file is all comments) and "persistent storage" (create-drop wipes it). Add a Components table mapping each feature to its files + docs.

---

## Prioritized action list

**P0 — make it build and run (do these first):**
1. Commit the Maven wrapper (or switch to a Maven base image) — #1
2. Fix the aspect pointcut package `accesscontrol` → `template` — #2
3. Remove `spring.config.activate.on-profile` from `application-postgres.yaml` — #3
4. Fix `RedisCacheService` bean type (StringRedisTemplate or a JSON `RedisTemplate` bean) — #4
5. Fix the `DataInitialization` guard (`admin` → `test-admin`) — #5
6. Unify the Kafka topic default — #6
7. Fix the kafka-redis JAAS config — #7
8. Fix compose Kafka listeners + kafka-ui — #8
9. Delete `data.sql` (or seed real bcrypt hashes) — #9

**P1 — security & correctness:**
10-21 — JWT secret env-injection + fail-fast; default admin off by default; JWT-filter try/catch (401 not 500); lock down `/actuator/**`; STATELESS session + 401 entry point; real validation; `UsernameNotFoundException`; circuit-breaker fallback semantics; SSRF-safe demo; rate-limiter fixes; login timing; Kafka listener error handling.

**P2 — idioms & polish:**
22-42 — constructor injection everywhere; `@RestControllerAdvice` + `ProblemDetail`; delete `VirtualThreadConfig`; `RestClient.Builder` + timeouts; trim `BusinessMetrics`; `spring.lifecycle` placement; fix `sprint` typo + console trace pattern; delete `.properties.example`; `security/` package; rename role annotations; real uptime; OpenAPI cleanup; `create-drop`; Java 21 LTS; pom hygiene; Dockerfile flags; compose pins/health/ports; devtools; profile activation.

**P2.5 — tests:** add `application-test.yaml`, Testcontainers, `@WebMvcTest`/`@DataJpaTest` slices, negative-path token tests, fix `JwtConfig` helper, drop the httpbin test, cover the untested classes.

**P3 — additions:** Flyway, CI workflow, working validation example, ProblemDetail, `@Cacheable`, structured logging, a paginated CRUD resource, `.env.example`.

---

*Verification note: the security-dimension findings (#10, #11, #12) were adversarially verified by an independent agent; all P0 items and most P1/P2 structural items were confirmed by direct reading of the code during this review. Items tagged `[reviewer]` were surfaced by a dimension reviewer with file:line evidence but not independently re-verified in this session due to a token limit — spot-check before acting. The `RedisCacheService` bean-mismatch (#4) is confirmed by code inspection but was not executed at runtime; verify with a quick `mvn spring-boot:run -Dspring-boot.run.profiles=kafka-redis` once dependencies are available.*
