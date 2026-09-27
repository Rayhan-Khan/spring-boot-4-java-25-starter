# Upgrade to Java 25 and Spring Boot 4.1

**Date:** 2026-09-24
**Status:** Done. Compiles on Java 25 and starts up to the database connection; manual check with a real database and Firebase still pending.

## Goal

Build and run the backend on Java 25 (LTS). The previous stack (Gradle 8.8, Spring Boot 3.4.0) cannot use Java 25, and Spring Boot 3.4 OSS support ended on 2025-12-31, so the framework is upgraded at the same time.

## Why the old stack blocks Java 25

| Component | Old | Problem |
|---|---|---|
| Gradle | 8.8 | Cannot run on or compile for Java 25 (needs 9.1+) |
| Lombok (managed by Boot 3.4.0) | 1.18.36 | Java 25 support added in 1.18.40 |
| Byte Buddy (Hibernate proxies) | 1.15.10 | Does not support Java 25 class files |
| Spring Boot | 3.4.0 | OSS support ended; 3.5 ended 2026-06-30 |

## Target versions

| Component | Old | New |
|---|---|---|
| Java toolchain | 17 | 25 |
| Gradle wrapper | 8.8 | 9.7.1 |
| Spring Boot | 3.4.0 | 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) |
| springdoc-openapi | 2.7.0 | 3.1.1 |
| QueryDSL | `com.querydsl` 5.0.0 (unmaintained) | `io.github.openfeign.querydsl` 7.7 (maintained fork, same `com.querydsl` packages) |
| MapStruct | 1.5.5.Final | 1.6.3 |
| Firebase Admin SDK | 9.1.1 | 9.10.0 |
| Docker build / runtime images | `gradle:8.2-jdk17` / `eclipse-temurin:17-jre-alpine` | `eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre-alpine` |

## Steps

### Build
- [x] Upgrade the Gradle wrapper to 9.7.1.
- [x] `build.gradle`: Spring Boot 4.1.1 plugin, Java toolchain 25.
- [x] Rename `spring-boot-starter-web` to `spring-boot-starter-webmvc` (Boot 4 name).
- [x] Flyway: replace `flyway-core:11.0.0` with `spring-boot-starter-flyway` and a Boot-managed `flyway-database-postgresql`. In Boot 4, Flyway only auto-runs with the starter.
- [x] Validation: replace `jakarta.validation-api:3.0.2` and `hibernate-validator:6.1.0.Final` (a `javax` version) with `spring-boot-starter-validation`.
- [x] Remove unused or obsolete libraries: `jackson-datatype-hibernate5:2.9.8` and `jackson-datatype-jsr310:2.15.2` (Jackson 3 has `java.time` support built in), and the duplicate test-only `postgresql:42.6.0`.
- [x] Let Boot manage the PostgreSQL driver version.
- [x] Swap QueryDSL to the OpenFeign fork.
- [x] Remove the `-Aquerydsl.generatedSourcesDir` compiler argument: no processor reads it, and it uses `buildDir`, which Gradle 9 deprecates.
- [x] Use `spring-boot-starter-security-test` for security tests.

### Code
- [x] `FirebaseConfig`: `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`. Spring 7 no longer calls `javax` lifecycle annotations, so Firebase would never initialize.
- [x] `SecurityConfig`: `@EnableGlobalMethodSecurity` (removed in Spring Security 7) → `@EnableMethodSecurity`.
- [x] Jackson 3 (`com.fasterxml.jackson.*` → `tools.jackson.*`):
  - [x] `FirebaseAuthenticationFilter` and `CustomAuthEntryPoint` use the Jackson 3 `ObjectMapper`.
  - [x] `GlobalExceptionHandler`: `JsonParseException` → the Jackson 3 equivalent.
  - [x] `AppConfig`: remove the custom `ObjectMapper` bean so Boot's auto-configured `JsonMapper` is used.
- [x] Annotations (`com.fasterxml.jackson.annotation.JsonIgnore`) keep the same package in Jackson 3, so no change.

### Deployment and docs
- [x] Dockerfile: Java 25 build and runtime images.
- [x] README: Java 25 install instructions.

## Behavior changes to expect

- **Request validation starts working.** Before, only a `javax` validator was on the classpath, so `@Valid` / `@NotBlank` / `@Email` on requests were silently skipped. With `spring-boot-starter-validation`, invalid requests now get a 400 response.
- **`spring.jackson.*` settings in `application.yml` take effect.** The custom `ObjectMapper` bean used to replace Boot's configured one.

## Verification

- [x] `./gradlew clean build -x test` succeeds with the Java 25 toolchain.
- [x] Compiled classes target Java 25 (class file version 69).
- [x] Dependency tree shows Lombok ≥ 1.18.40, Byte Buddy with Java 25 support, and no leftover `com.querydsl` 5.x or `javax.validation`.
- [ ] Manual (needs a database and Firebase): start the app, open `/swagger-ui/index.html`, and call `POST /api/v1/auth/login` with a Firebase ID token.

## Rollback

A copy of the project from before the upgrade is in the Claude scratchpad (`backup-before-java25`), excluding `build/` and `.gradle/`.
