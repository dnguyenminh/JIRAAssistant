# ============================================================
# Stage 1: Build — Gradle + JDK 17
# ============================================================
FROM gradle:8-jdk17 AS build

WORKDIR /app

# Copy Gradle wrapper and config first for layer caching
COPY gradle/ gradle/
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml

# Copy module build files
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY server/build.gradle.kts server/build.gradle.kts
COPY server/core/build.gradle.kts server/core/build.gradle.kts
COPY server/dashboard/build.gradle.kts server/dashboard/build.gradle.kts
COPY server/analysis/build.gradle.kts server/analysis/build.gradle.kts
COPY server/docgen/build.gradle.kts server/docgen/build.gradle.kts
COPY server/agent/build.gradle.kts server/agent/build.gradle.kts
COPY server/chat/build.gradle.kts server/chat/build.gradle.kts
COPY server/mcp/build.gradle.kts server/mcp/build.gradle.kts
COPY server/knowledge-graph/build.gradle.kts server/knowledge-graph/build.gradle.kts
COPY server/user-mgmt/build.gradle.kts server/user-mgmt/build.gradle.kts
COPY server/testing-support/build.gradle.kts server/testing-support/build.gradle.kts
COPY frontend/build.gradle.kts frontend/build.gradle.kts


# Download dependencies (cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY shared/ shared/
COPY server/ server/
COPY frontend/ frontend/


# Build the server fat JAR
RUN gradle :server:fatJar --no-daemon

# Build the frontend JS bundle (Kotlin/JS production webpack)
RUN gradle :frontend:jsBrowserProductionWebpack --no-daemon

# ============================================================
# Stage 2: Runtime — JRE 17 Alpine
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /app/server/build/libs/jira-assistant-server-all.jar app.jar

# Copy frontend production bundle JS files to static root
COPY --from=build /app/frontend/build/dist/js/productionExecutable/ /app/static/

# Copy frontend CSS resources
COPY --from=build /app/frontend/src/jsMain/resources/ /app/static/

# Copy frontend index.html
COPY --from=build /app/frontend/index.html /app/static/index.html

# Create app directory
RUN mkdir -p /app && chown -R appuser:appgroup /app

USER appuser

# Environment defaults
ENV PORT=8080 \
    AI_PROVIDER_URL=http://ollama:11434 \
    JIRA_HOST=https://jira.example.com \
    JWT_SECRET=change-me-in-production \
    ENCRYPTION_KEY=change-me-in-production \
    STATIC_DIR=/app/static

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
