FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --version
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Distroless over a jlink-trimmed custom runtime: jlink needs jdeps to see the real
# module graph, and jdeps can't look inside a Spring Boot fat jar's BOOT-INF/lib
# without first exploding it - doable, but the module list it infers is easy to get
# subtly wrong (a missed JCE/TLS module would silently break Postgres SSL or JWT
# ECDSA verification, both security-critical paths here). Distroless gets most of the
# same hardening - no shell, no package manager, runs as a non-root user by default -
# with a stock, fully-supported JRE, at much lower risk for a comparatively small size
# difference.
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER nonroot
EXPOSE 8080
# No Docker-level HEALTHCHECK here: distroless has no shell and no curl/wget to run
# one. Render's own network-level health check (render.yaml's healthCheckPath) covers
# production; locally, docker-compose's other services still start via depends_on, and
# /actuator/health is reachable from the host exactly as it's been used throughout this
# project's own verification passes.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
