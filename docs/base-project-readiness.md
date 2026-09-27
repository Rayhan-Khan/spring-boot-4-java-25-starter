# Base project readiness

**Date:** 2026-09-24
**Status:** Done. `gradlew build` passes with 55 tests (30 integration tests against PostgreSQL in Docker). Authentication was extended afterwards; see `auth-jwt-and-firebase.md`.

## Goal

Make `base-project` a reusable starting point for any Spring Boot project: download it, rename it with one script, fill in `.env`, and run. Nothing insecure or broken should be copied into new projects.

Firebase stays as the authentication and storage provider. Libraries that are not used yet but are cheap and likely useful (mail, MapStruct, Tika, Gson, jjwt) stay.

## Steps

### Security and correctness
- [x] `JwtGenerator`: the secret was hard-coded in `application.yml` and tokens were never signed. Read the secret from `JWT_SECRET`, sign with HMAC-SHA, update jjwt to 0.13.0, and stop putting tokens in exception messages. The app still starts without `JWT_SECRET`; only using `JwtGenerator` requires it.
- [x] Stop logging Firebase ID tokens (`AuthServiceImpl`) and lower per-request filter logs to DEBUG.
- [x] Firebase wiring: replace `@PostConstruct` + `FirebaseAuth.getInstance()` with `FirebaseApp` / `FirebaseAuth` / `Storage` beans. This removes the hidden startup-order dependency and makes Firebase mockable in tests (`firebase.enabled=false`).
- [x] `FirebaseAuthenticationFilter` was both a `@Component` and created with `new` in `SecurityConfig`, so it ran twice per request. Create it only in `SecurityConfig`.
- [x] Invalid Firebase token at login returns 401 instead of 400.
- [x] `GlobalExceptionHandler`: extend `ResponseEntityExceptionHandler` so standard Spring errors get correct status codes (400, 404, 405, 413, 415...). Unexpected errors return 500 with a generic message (not 400 with internal details). Add 401, 403, 409 and constraint-violation handling. Parameter validation errors show the real message, not always "Invalid email format".
- [x] `SecurityConfig`: permit `/error` (otherwise real errors show as 401), permit health endpoints, drop Springfox leftovers, keep default frame protection.
- [x] Remove `spring.main.allow-bean-definition-overriding`.
- [x] Remove multipart `location: /tmp` (fails on Windows).
- [x] `AvatarValidator`: size message uses the configured limit, not a hard-coded "5MB".

### Configuration
- [x] Replace the custom `DotenvLoader` with Spring Boot's `spring.config.import: optional:file:.env[.properties]`, so tests and IDE runs read `.env` too.
- [x] Production-safe `application.yml` (no stack traces, no SQL logging, actuator exposes only health and info) plus `application-dev.yml` (stack traces, SQL, debug logs). `gradlew bootRun` uses `dev` by default; `java -jar` and Docker use the safe defaults.
- [x] Default values for non-secret settings (upload limits, formats, date format, Firebase URL) so fewer `.env` entries are required.
- [x] Move Swagger settings from `logging.doc` (ignored) to `springdoc`.
- [x] Wire `CORS_ALLOWED_ORIGINS` and `MAX_UPLOAD_SIZE`; expose `APP_BASE_URL` as `app.base-url`.

### API responses
- [x] Replace json-simple (last release 2012; pulls JUnit 4 into production) with a typed `ApiResponse<T>` record. The JSON keys stay the same: `status`, `message`, `data`, `meta`, `errors`.

### Operations
- [x] Add Spring Boot Actuator (`/actuator/health`, liveness/readiness probes).
- [x] Dockerfile: run as a non-root user, container-aware memory limit, health check. Add `.dockerignore` so `.env` and keys are not sent to the Docker build.
- [x] `docker-compose.dev.yml`: local PostgreSQL that reads its settings from `.env`.
- [x] `docker-compose.yml`: remove the obsolete `version` key.
- [x] `deploy.sh`: replace `docker system prune -f` (deletes all unused Docker data on the server) with `docker image prune -f`, use `docker compose` v2, make the branch configurable.

### Tests
- [x] Integration tests with Testcontainers PostgreSQL and mocked Firebase: migrations, register, authenticated user search (QueryDSL), 401 without a token, validation errors, CORS, health. Skipped automatically when Docker is not running.
- [x] Unit test for `JwtGenerator` (sign and read back, reject tampered tokens).

### Reuse
- [x] `scripts/rename-project.ps1`: rename project, package, main class, Docker names and titles in one command. Tested with Windows PowerShell 5.1 (PowerShell 7 was not available for testing).
- [x] README: quick start, starting a new project from the base, configuration, testing, deployment.
- [x] `.env.example`: document `JWT_SECRET` and the now-used settings.

### Found and fixed while implementing
- [x] `sortBy=email` (without `,asc`/`,desc`) crashed with an index error; sorting by an unknown field now returns 400 instead of 500.
- [x] Removed `spring.flyway.ignore-unsupported-database` (not a Spring Boot property, silently ignored) and set `baseline-on-migrate: false`, so Flyway fails instead of skipping migrations on an unmanaged, non-empty schema.
- [x] `.gitattributes` keeps `gradlew` and `*.sh` on LF line endings, so they run on Linux servers even when committed from Windows.
- [x] Mockito is loaded as a Java agent in tests (newer JDKs block self-attaching agents).
- [x] Only the executable jar is built (no `*-plain.jar`).
- [x] The rename script also deletes `build/` and `.idea/`, which still refer to the old names.

### Firebase optional (added after the first real run)
The first run against the real database stopped at startup because the Firebase key was still the example file.
- [x] `FIREBASE_ENABLED=false` starts the app without Firebase beans. `AuthServiceImpl`, `FirebaseStorageServiceImpl` and `SecurityConfig` look Firebase up through `ObjectProvider` only when needed.
- [x] Features that need Firebase throw `FirebaseNotConfiguredException`, returned as 503. Protected endpoints return 401, because no token can be verified.
- [x] `BUCKET_NAME` is only required when Firebase is enabled; startup errors about the key or bucket mention the switch.
- [x] `FirebaseDisabledIntegrationTest` (5 tests) starts the app with no Firebase at all.

### JWT settings and env files (requested after the first real run)
- [x] `JWT_SECRET` is Base64 with at least 256 bits (`openssl rand -base64 48`); `JwtGenerator` decodes it and rejects shorter or non-Base64 values.
- [x] `JWT_ACCESS_EXPIRATION` (default 15 minutes) and `JWT_REFRESH_EXPIRATION` (default 7 days) replace the hard-coded lifetimes.
- [x] `.env` and `.env.example` have identical keys, order and comments; `.env.example` holds only placeholders.

## Verification

- [x] `gradlew build` passes, including the integration tests with Docker running.
- [x] The rename script works on a copy (`crm-backend` / `com.acme.crm`) and the renamed copy passes all 29 tests. A nested package (`com.mss.base` → `com.mss.base.core`) also compiles.
- [x] The Docker image builds, runs as a non-root user and has the health check.
- [x] The app starts on the `dev` profile with settings read from `.env`, up to the (fake) database connection.
- [x] Real run with `FIREBASE_ENABLED=false` against the real database: Flyway validated the schema, the app started, and health, Swagger and `check-email` answered 200; login answered 503 and protected endpoints 401.
- [ ] Manual: run with a real Firebase key and go through register, login and user search in Swagger.

## Not in scope

- Git repository and CI: add them when the project is pushed to a remote.
- `lms-frontend` base readiness.
