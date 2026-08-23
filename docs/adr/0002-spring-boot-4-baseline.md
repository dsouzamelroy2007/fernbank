# 2. Target Spring Boot 4 / Spring Security 7 instead of 3.3

## Status

Accepted

## Context

The original plan called for Spring Boot 3.3+ and Spring Security 6. At scaffold time
(2026-08-19), start.spring.io no longer generates Boot 3.3.x — the oldest version it
offers is 4.0.7. Boot 4 brings Spring Framework 7 and Spring Security 7, restructured
starter artifact names (e.g. `spring-boot-starter-webmvc` instead of
`spring-boot-starter-web`, per-feature test starters), and requires Java 17+ (we're
already on 21).

Two options: hand-write a 3.3.x Gradle build (drifts from anything Initializr would
actually generate, more maintenance surface), or target the current generated baseline
and treat later phases' "Spring Security 6" references as "whatever Security major ships
with the Boot version in use."

## Decision

Target Spring Boot 4.0.7 / Spring Security 7, generated via start.spring.io. Verified: a
full `./gradlew build` and `./gradlew test` (including the Testcontainers-backed test)
pass with springdoc-openapi 2.8.6, MapStruct 1.6.3, JaCoCo, and Spotless added on top of
the generated project.

## Consequences

- Phase 2 (security core) prompts should be read as targeting Spring Security 7's APIs,
  not 6's. Check for API renames when following historical Security 6 documentation or
  examples.
- Dependency versions (springdoc, MapStruct) were picked for current Maven Central
  availability at scaffold time, not for confirmed Boot 4 compatibility from official
  release notes — re-verify if springdoc's auto-configuration behaves unexpectedly.
- Frontend equivalent: Next.js is on 16.3.1 (latest via `create-next-app`, not 15 as
  originally planned). Next 16 ships an `AGENTS.md` in the generated project noting
  breaking changes vs. training-data assumptions — read
  `frontend/node_modules/next/dist/docs/` before writing App Router code that relies on
  Next 15 behavior.
