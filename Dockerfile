# Multi-stage Dockerfile for Spring Boot 3.5 with Java 25 (LTS) Virtual Threads
# Stage 1: Build stage with Maven
FROM eclipse-temurin:25-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml first (for better caching)
COPY mvnw* ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# Download dependencies (this layer will be cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:25-jre-alpine AS runtime

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create non-root user for security
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to spring user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring

# Expose port 8080
EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Container memory awareness. UseContainerSupport and G1GC are already JVM defaults,
# so only the heap percentage needs to be set. Tune further via JAVA_TOOL_OPTIONS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
