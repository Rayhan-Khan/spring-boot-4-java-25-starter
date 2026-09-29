<div align="center">
  <h1>🚀 Base Project</h1>
  <p>Spring Boot base project: email/password (JWT) and Firebase authentication, PostgreSQL with Flyway, Swagger, Docker deployment and tests.<br>
  Use it as the starting point for new backend projects (see <a href="#-start-a-new-project-from-this-base">Start a new project</a>).</p>
</div>

---

## 📑 Table of Contents
- [Tech Stack](#-tech-stack)
- [Quick Start](#-quick-start)
- [Start a New Project from This Base](#-start-a-new-project-from-this-base)
- [Configuration](#-configuration)
- [Authentication](#-authentication)
- [API Conventions](#-api-conventions)
- [Database Migrations](#-database-migrations)
- [Testing](#-testing)
- [Dependency Updates](#-dependency-updates)
- [Project Structure](#-project-structure)
- [Deployment](#-deployment)
- [Server Setup (Ubuntu 24.04)](#-server-setup-ubuntu-2404)

---

## 🧰 Tech Stack

| Area | Technology |
|---|---|
| Language / build | Java 25, Gradle 9.8 (wrapper included) |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Security 7, Spring Data JPA / Hibernate 7) |
| Database | PostgreSQL, Flyway migrations, QueryDSL 7 (OpenFeign fork) |
| Auth & storage | Email/password with the app's own JWTs (jjwt, BCrypt) and/or Firebase Admin SDK (ID tokens, Cloud Storage) |
| API docs | springdoc-openapi 3 (Swagger UI) |
| Operations | Spring Boot Actuator, Docker, Docker Compose |
| Tests | JUnit 6, AssertJ, Mockito, Testcontainers |

Also on the classpath for later use: Spring Mail, MapStruct, Apache Tika, Gson.

---

## 🔄 Dependency Updates

Spring Boot 4.1.1 manages compatible versions of most runtime and test libraries through its BOM. Keep that BOM in place when updating the template. `build.gradle` temporarily overrides Jackson 2, Jackson 3, and Tomcat to fixed patch releases because the BOM versions have published advisories; remove those overrides when a Spring Boot maintenance release includes the fixes. Directly pinned libraries and the Gradle wrapper were checked against stable Maven Central releases on 29 September 2026. Run `./gradlew test` after every dependency update.

## ⚡ Quick Start

**Prerequisites**
- **JDK 25.** Gradle finds it automatically, so `JAVA_HOME` can keep pointing at another JDK.
- **Docker Desktop** (optional) for the local database and the integration tests.
- IDE: IntelliJ IDEA 2025.2 or newer, or VS Code with the Extension Pack for Java.

**1. Create your `.env`**
```bash
cp .env.example .env          # Windows Command Prompt: copy .env.example .env
```
Fill in the `DB_*` values, choose the login methods with `JWT_ENABLED` / `FIREBASE_ENABLED` (see [Authentication](#-authentication)), and set `JWT_SECRET` to the output of `openssl rand -base64 48`. Everything marked *optional* has a sensible default.

**2. Firebase key** (only when `FIREBASE_ENABLED=true`)
1. [Firebase Console](https://console.firebase.google.com/) → **Project settings** → **Service accounts**.
2. Click **Generate new private key**.
3. Save the file as `firebase-service-account.json` in the project root (it is gitignored), and set `BUCKET_NAME`.

With `FIREBASE_ENABLED=false`, skip this step: email/password login works on its own.

**3. Start a local PostgreSQL** (or point `.env` at an existing database)
```bash
docker compose -f docker-compose.dev.yml up -d
```
It creates the database with the `DB_NAME`, `DB_USER`, `DB_PASSWORD` and `DB_PORT` from `.env`.

**4. Run the app**
```bash
./gradlew bootRun             # Windows Command Prompt: gradlew bootRun
```
`bootRun` uses the `dev` profile. On first start, Flyway creates the tables in `DB_SCHEMA`.

**5. Open**
- Swagger UI: http://localhost:8000/swagger-ui.html
- Health check: http://localhost:8000/actuator/health

---

## 🆕 Start a New Project from This Base

1. **Copy** the project into a new folder. Leave out `build/`, `.gradle/`, `.idea/`, `.env` and `firebase-service-account.json`.
2. **Rename** it (project name, Java package, main class, Docker names, API title) in one step:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/rename-project.ps1 -Name crm-backend -Package com.acme.crm -Title "CRM Backend"
   ```
   | Parameter | Example | Result |
   |---|---|---|
   | `-Name` | `crm-backend` | Gradle project, `spring.application.name`, jar name, Docker image/container |
   | `-Package` | `com.acme.crm` | Java package (folders are moved); Gradle group becomes `com.acme` |
   | `-AppName` *(optional)* | `Crm` | Main class `CrmApplication` (default: first word of `-Name`) |
   | `-Title` *(optional)* | `CRM Backend` | README heading and Swagger title |
3. **Clean up** what the new project does not need: the example user and avatar features, and the base project's plans in `docs/`.
4. **Configure** `.env` (Quick Start steps 1–3), then run `./gradlew build` to check that everything compiles and the tests pass.
5. **Version control:** `git init`, first commit, push.

---

## ⚙️ Configuration

Settings come from environment variables. The `.env` file in the working directory is loaded automatically; real environment variables take precedence. See [`.env.example`](.env.example) for every variable and its default.

| Variable | Required | Purpose |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | yes | PostgreSQL connection |
| `DB_SCHEMA` | yes | Schema for all tables (created by Flyway) |
| `JWT_ENABLED`, `FIREBASE_ENABLED` | no | Login methods, see [Authentication](#-authentication) (both default `true`) |
| `JWT_SECRET` | when `JWT_ENABLED=true` | Base64, at least 256 bits: `openssl rand -base64 48`. Use a different one per project and environment |
| `BUCKET_NAME` | when `FIREBASE_ENABLED=true` | Firebase Storage bucket |
| `CORS_ALLOWED_ORIGINS` | no | Frontend origins allowed to call the API (default `http://localhost:3000`) |
| `MAX_UPLOAD_SIZE` | no | Max upload / request size in bytes (default 20 MB) |
| `AVATAR_MAX_SIZE`, `AVATAR_ALLOWED_FORMATS` | no | Avatar size limit and MIME types |
| `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | no | Token lifetimes in ms (defaults 15 minutes and 7 days) |
| `APP_BASE_URL`, `FIREBASE_URL`, `FIREBASE_CONFIG_PATH`, `USER_JOIN_DATE_FORMAT` | no | See `.env.example` |

**Profiles**

| | Default (`java -jar`, Docker) | `dev` (`gradlew bootRun`) |
|---|---|---|
| Stack traces in error responses | never | always |
| SQL logging | off | on |
| App log level | INFO | DEBUG |

To run `bootRun` without the dev profile, set `SPRING_PROFILES_ACTIVE` to another value (for example `default`).

---

## 🔐 Authentication

Each project chooses its login methods in `.env`. The settings are read once at startup; they add no work to individual requests.

| `JWT_ENABLED` | `FIREBASE_ENABLED` | Result |
|---|---|---|
| true | false | Email/password login; the app issues its own tokens |
| false | true | Firebase login only |
| true | true | Both. A Firebase login also returns the app's tokens, and protected endpoints accept either token |
| false | false | Only public endpoints work (warning at startup) |

**Endpoints** (`/api/v1/auth`, all public). A disabled method's endpoints answer `503`.

| Endpoint | Body | Method |
|---|---|---|
| `POST /register` | `firstName`, `lastName`, `email`, `password` (8–72 characters) | JWT |
| `POST /login` | `email`, `password` | JWT |
| `POST /refresh` | `refreshToken` | JWT |
| `POST /logout` | `refreshToken` | JWT |
| `POST /firebase/register` | `firstName`, `lastName`, `email`, `idToken` | Firebase |
| `POST /firebase/login` | `idToken` (also returns the app's tokens when JWT is enabled) | Firebase |
| `POST /firebase/resend-verification?email=` | | Firebase |
| `GET /check-email?email=` | | always |

**Tokens.** Login and registration return:
```json
{ "accessToken": "eyJ...", "refreshToken": "q3V...", "tokenType": "Bearer", "expiresIn": 900 }
```
- Send `Authorization: Bearer <accessToken>` on every request. The access token is a JWT signed with `JWT_SECRET` and lasts `JWT_ACCESS_EXPIRATION` (15 minutes).
- When it expires, call `/refresh` with the refresh token (valid `JWT_REFRESH_EXPIRATION`, 7 days). Each refresh token works once and is replaced by a new one. Reusing an old one ends all of that user's sessions, in case it was stolen.
- `/logout` revokes the refresh token. Only a SHA-256 hash of each refresh token is stored.
- With Firebase enabled, a Firebase ID token can also be sent as the bearer token. The user must have called `/firebase/login` once.

**In your code.** Controllers receive the logged-in user the same way for every login method:
```java
@GetMapping
public ResponseEntity<ApiResponse<CurrentUserResponse>> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
    return ok(success(currentUserService.getCurrentUser(currentUser.userId())));
}
```
Users have a role (`USER` by default, or `ADMIN`, stored in `users.role`). Restrict endpoints with `@PreAuthorize("hasRole('ADMIN')")`.

**Accounts.** Emails are unique across both methods. When a Firebase login uses a verified email that already belongs to an email/password user, the two are linked into one account. Passwords are stored as BCrypt hashes. Login errors never reveal whether an email exists.

Public paths: `/api/v1/auth/**`, Swagger (`/swagger-ui/**`, `/v3/api-docs/**`), `/actuator/health`, `/actuator/info`. Everything else needs a valid token.

---

## 🔌 API Conventions

**Response envelope.** Every response has the same shape:
```json
{ "status": "success", "message": "User registered successfully.", "data": { }, "meta": null, "errors": null }
```
```json
{ "status": "error", "message": "First name is required", "data": null, "meta": null,
  "errors": { "firstName": "First name is required" } }
```
Build responses with `ResponseBuilder.success(...)`, `paginatedSuccess(...)` and `error(...)`. Throw exceptions for errors; `GlobalExceptionHandler` turns them into this envelope.

**Status codes**

| Status | When |
|---|---|
| 400 | Validation failed (`errors` lists the fields), malformed JSON, `CustomMessagePresentException`, `IllegalArgumentException` |
| 401 | Missing, invalid or expired token; wrong email or password; invalid refresh token |
| 403 | `EmailNotVerifiedException`, access denied by method security (for example a missing role) |
| 404 | `ResourceNotFoundException`, unknown path |
| 409 | Database constraint violation (for example a duplicate unique value) |
| 500 | Any other exception. Clients get a generic message; details are only in the server log |
| 503 | The login method (or Firebase storage) is switched off in `.env` |

**CORS.** Allowed origins come from `CORS_ALLOWED_ORIGINS`. Bearer tokens are used instead of cookies, so credentials are not enabled.

---

## 🗃 Database Migrations

- Schema changes are Flyway migrations in [`src/main/resources/db/migration`](src/main/resources/db/migration), applied automatically on startup.
- Name new files `V<next number>__<description>.sql`, for example `V3__create_courses_table.sql`.
- Never edit a migration that has already run on a shared database; add a new one instead.
- Hibernate does not change the schema (`ddl-auto: none`).

---

## 🧪 Testing

```bash
./gradlew build      # compiles and runs all tests
./gradlew test       # tests only
```

| Test | Needs | Covers |
|---|---|---|
| `AuthRequestValidationTest`, `EmailValidatorTest`, `JwtGeneratorTest` | nothing | Validation rules, JWT signing and verification |
| `ApplicationIntegrationTest` | Docker running | Both login methods (Firebase mocked): Firebase login returning app tokens, account linking, user search, 401/400/404 responses, CORS, health |
| `JwtOnlyIntegrationTest` | Docker running | Email/password: register, login, wrong password, refresh rotation and reuse detection, logout, invalid tokens |
| `FirebaseOnlyIntegrationTest` | Docker running | Firebase login without app tokens; email/password endpoints answer 503 |
| `NoLoginMethodIntegrationTest` | Docker running | Both methods off: public endpoints work, login answers 503, protected endpoints 401 |

Each integration test starts the whole app against a throwaway PostgreSQL (Testcontainers). Without Docker they are skipped automatically, so the build still passes.

To test new endpoints end to end, extend `AbstractIntegrationTest` (see `JwtOnlyIntegrationTest`). Use `registerWithPassword(...)` to get a user and access token.

---

## 🗂 Project Structure

```
src/main/java/com/rayhan/base
├── BaseApplication.java  Application entry point
├── annotation/           Custom validation annotations (@ValidEmail, @ValidAvatar, @ValidEnum)
├── config/               App, Firebase, OpenAPI configuration and AuthSettings (login switches)
├── constant/             Shared constants
├── controller/           REST controllers (/api/v1/...)
├── dto/                  Request bodies
├── entity/               JPA entities and base classes (id, timestamps, soft delete)
├── enums/                Enums used by entities
├── exception/            Custom exceptions and GlobalExceptionHandler
├── repository/           Spring Data repositories (with QueryDSL support)
├── response/             Response DTOs
├── security/             Security config, bearer token filter, JwtGenerator, AuthenticatedUser
├── service/              Service interfaces and implementations (impl/)
├── utils/                ApiResponse, ResponseBuilder, pagination helpers
└── validation/           Validators for the custom annotations
src/main/resources
├── application.yml       Production-safe defaults
├── application-dev.yml   Local development overrides
└── db/migration/         Flyway migrations
scripts/rename-project.ps1  Rename the project for a new application
docs/                       Implementation plans
```

---

## 🐳 Deployment

**Docker Compose** (builds the image and runs it with the database from `.env`):
```bash
docker compose up -d --build
```
- The image is built with JDK 25 and runs on a JRE 25 Alpine image as a non-root user.
- It has a health check on `/actuator/health`, and the heap size follows the container's memory limit.
- `.env` provides the settings. `firebase-service-account.json` is mounted read-only; it is never copied into the image.

**Deploy script** (on the server, inside the project folder):
```bash
bash deploy.sh            # pulls the develop branch, rebuilds, restarts
bash deploy.sh main       # or another branch
```
It only removes dangling images afterwards; other applications' containers, images and volumes on the server are left alone.

---

## 🖥 Server Setup (Ubuntu 24.04)

The application is built inside Docker, so the server only needs Git and Docker.

**Git and Docker**
```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER      # log out and back in to use docker without sudo
```

**Clone with a GitHub token**
1. Create a token at [GitHub → Settings → Tokens](https://github.com/settings/tokens).
2. Clone and configure:
   ```bash
   git clone https://<username>:<token>@github.com/<organization>/<repository>.git
   cd <repository>
   git config user.email "your-email@example.com"
   git config user.name "YourUsername"
   ```

**Configure and start**
1. Create `.env` from `.env.example` and copy `firebase-service-account.json` into the project folder.
2. Run `bash deploy.sh`.
3. Check with `docker compose ps` and `curl http://localhost:8000/actuator/health`.

To run Java builds on the server outside Docker, install JDK 25 with `sudo apt install -y openjdk-25-jdk`.
