# Authentication with JWT, Firebase, or both

**Date:** 2026-09-24
**Status:** Done. `gradlew build` passes with 55 tests; restart the running app to apply migration V3.

## Goal

Each project built from this base chooses its login method in `.env`:

| `JWT_ENABLED` | `FIREBASE_ENABLED` | Result |
|---|---|---|
| true | false | Email + password login; the app issues its own JWTs |
| false | true | Firebase login only (today's behavior) |
| true | true | Both. Firebase sign-in (Google, phone, etc.) can be exchanged for the app's JWTs, and protected endpoints accept either token |
| false | false | App starts; only public endpoints work (warning at startup) |

## Design

### One identity for the logged-in user
Today controllers receive the Firebase user ID as the principal. Replace it with an `AuthenticatedUser` record (`userId`, `email`, `role`, `provider`) that both login methods produce. Services look users up by the internal `userId`, so they no longer depend on Firebase.

### Tokens
- **Access token:** JWT signed with `JWT_SECRET` (HS256+), lifetime `JWT_ACCESS_EXPIRATION` (15 min). Claims: `sub` = user ID, `email`, `role`.
- **Refresh token:** a random opaque value (not a JWT), stored only as a SHA-256 hash in a new `refresh_tokens` table, lifetime `JWT_REFRESH_EXPIRATION` (7 days). Each refresh replaces the old token (rotation); logout revokes it. This makes sessions revocable, which a stateless JWT refresh token cannot be.

### Request authentication
One filter reads `Authorization: Bearer <token>` and picks the verifier by the token's algorithm:
- `HS*` (the app's JWT) → verify with `JWT_SECRET` (when JWT is enabled).
- `RS256` (Firebase ID token) → verify with Firebase (when Firebase is enabled), then load the user by Firebase UID. A Firebase user who has never logged in to this backend gets 401 until they call the Firebase login endpoint once.

### Endpoints (`/api/v1/auth`)
| Endpoint | Method | Enabled when |
|---|---|---|
| `POST /register` | email, password, first/last name → creates user, returns tokens | JWT |
| `POST /login` | email, password → access + refresh token | JWT |
| `POST /refresh` | refresh token → new access + refresh token | JWT |
| `POST /logout` | refresh token → revoked | JWT |
| `POST /firebase/register` | Firebase ID token + names (today's `/register`) | Firebase |
| `POST /firebase/login` | Firebase ID token (today's `/login`); also returns the app's tokens when JWT is enabled | Firebase |
| `POST /firebase/resend-verification` | today's `/resend-verification` | Firebase |
| `GET /check-email` | unchanged | always |

A disabled method's endpoints answer 503 with "... login is not enabled on this server".

The existing `/register`, `/login` and `/resend-verification` move under `/firebase/`. No frontend uses them yet.

### Database (migration `V3`)
- `users.password_hash` (nullable; BCrypt, via Spring Security's delegating encoder).
- `users.firebase_user_id` becomes nullable (email/password users have none).
- `users.role` (`USER` default, `ADMIN`), exposed as `ROLE_USER` / `ROLE_ADMIN`, so `@PreAuthorize("hasRole('ADMIN')")` works.
- New `refresh_tokens` table: `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`.
- Email stays unique across both methods. A Firebase login with a verified email that already exists as an email/password user links the Firebase UID to that user.

### Rules
- Passwords: 8–72 characters (72 is BCrypt's limit).
- Login errors never reveal whether the email exists ("Invalid email or password").
- With `JWT_ENABLED=true`, startup fails if `JWT_SECRET` is missing or weaker than 256 bits.

### Configuration
- New `JWT_ENABLED` (default `true`) in `.env` and `.env.example` (kept identical).
- Your current `.env`: `JWT_ENABLED=true`, `FIREBASE_ENABLED=false` → email/password login works without Firebase.

## Steps
- [x] `AuthenticatedUser` principal; controllers and `CurrentUserService` use `userId`.
- [x] Migration V3 (password, nullable Firebase UID, role, refresh tokens) + entity/repository changes.
- [x] `TokenService` (issue/rotate/revoke refresh tokens) on top of `JwtGenerator`.
- [x] Unified bearer-token filter (app JWT or Firebase ID token).
- [x] Email/password endpoints: register, login, refresh, logout.
- [x] Move Firebase endpoints under `/firebase/`; Firebase login returns app tokens when JWT is enabled.
- [x] `JWT_ENABLED` switch, startup validation, 503 for disabled methods.
- [x] Tests for every mode: JWT only, Firebase only, both, neither.
- [x] README (auth section), `.env.example` and `.env`.

## Implementation notes
- `AuthSettings` reads both switches once at startup and logs the result ("Login methods: email/password (JWT) enabled, Firebase disabled"). Firebase counts as enabled when a `FirebaseAuth` bean exists, so tests can supply a mock.
- `SecurityConfig` creates `BearerTokenAuthenticationFilter` with only the enabled verifiers; the filter picks one by the token's `alg` header (`HS*` → app JWT, `RS256` → Firebase).
- App JWT requests need no database lookup; Firebase ID tokens need one (to map the Firebase UID to the user).
- `JwtGenerator` was reduced to access tokens (`generateAccessToken`, `parseAccessToken`); the unused `usertype` methods were removed.
- Firebase endpoints moved to `FirebaseAuthController`; `FirebaseAuthService` replaced the old Firebase-based `AuthService`. DTOs renamed: `FirebaseRegisterRequest`, `FirebaseLoginRequest`; the unused duplicate `SocialLoginRequest` was removed.
- Firebase logins no longer overwrite names on every login; names from the Firebase profile are used only for new users.

## Verification
- [x] 55 tests pass: `ApplicationIntegrationTest` (both methods), `JwtOnlyIntegrationTest`, `FirebaseOnlyIntegrationTest`, `NoLoginMethodIntegrationTest`, plus unit tests.
- [x] Speed, measured against a throwaway PostgreSQL, 1,000 requests each on one connection:

  | Request | JWT on | JWT off |
  |---|---|---|
  | Public endpoint, no token (median) | 10.66 ms | 11.56 ms / 9.62 ms (two runs) |
  | Public endpoint with access token (median) | 11.50 ms | — |
  | `/current-user` with access token (median) | 12.03 ms | — |

  The switches make no measurable difference; two identical runs varied by about 2 ms. Verifying an access token costs about 1 ms.
- [ ] Manual: restart the app with the real database (applies V3), then register and log in through Swagger.

## Not in scope (can be added later)
- Email verification and password reset for email/password users (needs mail server settings).
- Login rate limiting / account lockout.
- Social login without Firebase (Google/GitHub OAuth directly).
