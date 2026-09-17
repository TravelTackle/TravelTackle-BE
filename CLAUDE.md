# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run the application (requires JWT_SECRET; see "Required environment" below)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "Timeout.travel_tackle.<TestClassName>"

# Generate QueryDSL Q-classes (run after adding/modifying @Entity classes)
./gradlew compileJava

# Clean build outputs AND generated Q-classes
./gradlew clean

# Frontend (React + Vite SPA, in frontend/)
cd frontend && npm install && npm run dev   # dev server on :5173, proxies /api -> :8080
cd frontend && npm run build                # production build to frontend/dist
```

> After `./gradlew clean`, always re-run `./gradlew compileJava` to regenerate Q-classes before building.

> **`AGENTS.md` is a sibling copy of the build/QueryDSL guidance for Codex.** When you change build commands or QueryDSL setup, update both files.

## Architecture

**Stack**: Spring Boot 4.1.0 · Java 21 · Spring Data JPA · H2 (in-memory) · Lombok · QueryDSL 6.0 (OpenFeign, Jakarta) · Spring Security (OAuth2 resource server + OAuth2 client) · Caffeine cache · Spring Mail · springdoc OpenAPI (Swagger). Frontend: React 19 + Vite 7.

**Root package**: `Timeout.travel_tackle` (note: PascalCase — intentional group convention)

### Module layout

Code is organized **by feature**. Each feature package owns its `controller`/`service`/`repository`/`dto`:

- `auth/` — signup, login, JWT, email verification, social (OAuth2) login. See "Authentication" below.
- `trip/` — travel plans (`Trip` → `TripDay` → `TripItem`), itinerary CRUD/reorder, plus public sharing: feed (`FeedService`/`FeedController`), save-as-copy (`SavedTripService`), and trip records with photos (`TripRecordService`). See "Trip sharing" below.
- `tour/` — read-only proxy over the Korean public TourAPI (`data.go.kr` KorService2), plus `tour/recommendation/` rule-based recommender. See "Recommendations & preferences" below.
- `preference/` — per-user travel preferences (`UserPreference`); feeds the recommender.
- `cart/` — saved tour contents per user.
- `image/` — `ImageStorageService` uploads trip-record photos to S3 and deletes them on replace/delete. Trip records accept `multipart/form-data` on `POST`/`PATCH /api/trips/{tripId}/record` (repeated form fields `title`, `content`, `photos`, optional `captions` aligned by index, bound to `TripRecordUploadRequest`); the JSON variants that take pre-existing `imageUrl`s still work. Only active when `aws.s3.bucket` is non-blank (`config/S3Config` is `@ConditionalOnExpression`; `application.yaml` maps it from `AWS_S3_BUCKET`); otherwise uploads fail with `IMAGE_001` (503). The server generates the object key (`images/{userId}/{yyyy}/{MM}/{uuid}.{ext}`) and deletes only keys under the caller's own `images/{userId}/` prefix; the type is sniffed from file signatures (jpeg/png/webp), 10MB per file (`WebConfig` raises the multipart limits to 10MB/50MB). Profile images reuse the same service with `Kind.PROFILE` (key prefix `profiles/{userId}/`, so record cleanup can never delete a profile object): `PUT/DELETE /api/auth/me/profile-image` (`AuthenticationService`), stored in `User.profileImageUrl`. `deleteQuietly` only touches URLs under our own base URL, so external image URLs (TourAPI, seed data) are left alone.
- `notification/` — user notifications (`Notification` entity, receiver-centric, FK-free snapshots of actor/trip/feedback). `TripFeedbackService.create` calls `NotificationService.notifyFeedback` and `SavedTripService.save` calls `notifyScrap`, each passing a snapshot command so `notification/` never depends on `trip/`. Both types honor the single `User.notifyFeedback` switch (no separate scrap setting by decision). Stored first, then pushed **after commit** over SSE (`GET /api/notifications/stream`, `NotificationSseRegistry` keeps per-user emitters in memory with a 25s heartbeat; single-instance only). List/unread-count/read endpoints under `/api/notifications`. Trip deletion and account deletion remove the related rows.
- `config/` — `SecurityConfig`, `JwtConfig`, `SocialOAuthConfig`, `WebConfig` (CORS), `QueryDslConfig`, `TourCacheConfig`, `SwaggerConfig`.
- `global/exception/` — centralized error handling (see below). `global/util/` — shared helpers (e.g. `UuidConverter.fromSubject` turns a JWT subject into the user `UUID`).
- `entity/` — all JPA `@Entity` classes (shared across features), `entity/Enum/` for enums.

> The top-level `controller/`, `service/`, and `dto/` packages are empty legacy placeholders — put new code in its feature package, not here.

### Authentication

Stateless JWT, sessions disabled (`SessionCreationPolicy.STATELESS`). Two flows converge on the same token issuance:

- **Tokens**: HS256 JWTs signed with `JWT_SECRET` via Nimbus (`JwtConfig`, `JwtService`). Access + refresh tokens are delivered as **httpOnly cookies** by `AuthCookieService` — `access_token` (path `/`) and `refresh_token` (path `/api/auth`). `AuthCookieService implements BearerTokenResolver`: it resolves the bearer token from the `access_token` cookie first, then falls back to the `Authorization` header. `SecurityConfig` wires this as an OAuth2 resource server.
- **Email/password signup**: 6-digit code emailed via SMTP, BCrypt-hashed, 10-min expiry, rate-limited (60s between requests, 5/hour). Documented in `docs/AUTH.md`.
- **Social login (Kakao, Google)**: Only active when `social.login.enabled=true`. `SocialOAuthConfig` is `@ConditionalOnProperty` and builds the `ClientRegistrationRepository` only from providers whose client id/secret are set. `SecurityConfig` conditionally enables `oauth2Login` only if a `ClientRegistrationRepository` bean exists, so the app boots fine without social config. On success, `SocialOAuthSuccessHandler` issues cookies then redirects to the frontend.

Public (permitAll) endpoints: `POST /api/auth/{email-verifications,email-verifications/confirm,signup,login,refresh,logout}`, `GET /api/tour/**`, plus `/oauth2/**`, `/login/oauth2/**`, Swagger, and `/h2-console/**`. Everything else requires authentication.

### Tour API integration

`TourApiClient` calls the external Korean TourAPI via `RestClient`. It requires `tour.service-key`; without it, requests throw `TOUR_API_NOT_CONFIGURED`. Responses are JSON-parsed defensively (single object vs. array `item` nodes both handled). `TourService` results are cached with Caffeine (`TourCacheConfig`).

### Recommendations & preferences

`UserPreference` (managed by `preference/`) stores a user's `InterestTag`s, `PreferredRegion`s, `BudgetLevel`, and `TravelStyle` (enums in `entity/Enum/`). `RecommendationService` (`tour/recommendation/`) is **rule-based, not ML**: `PreferenceMapper` is the single source of truth that maps each `InterestTag` → TourAPI params (`contentTypeId`/`lclsSystm` codes) and each `PreferredRegion` → area/lDong region codes, then it composes sections (a personalized section plus dedicated Food/Cafe/Festival sections) by querying `TourService`. When a user has no saved preference it returns `buildDefaultRecommendations()`. When extending recommendations, edit the `PreferenceMapper` switch rather than scattering TourAPI codes through the service.

### Trip sharing, feed & records

Plans start private and are made public via a publish flag on `Trip` (`isPublished`). Once published:
- **Feed** (`FeedService`): latest-first paginated list of public trips; each trip's thumbnail is the earliest-uploaded `TripPhoto` of its `TripRecord`s.
- **Save-as-copy** (`SavedTripService`): saving someone else's public trip **deep-copies** the whole `Trip`/`TripDay`/`TripItem` graph into a new trip owned by the saver (you cannot save your own trip or save the same one twice).
- **Records** (`TripRecordService`): `TripRecord` + `TripPhoto` are post-trip photo logs attached to a trip.

**Credit billing is deferred (not implemented).** `CreditTransaction` / `CreditTransactionReason` / `User.creditBalance` exist as scaffolding, but no flow charges credits yet — `SavedTripService.save` has an explicit commented hook (`[과금 보류]`) marking where deduction + transaction logging will go once pricing policy is decided. Do not invent a pricing policy; surface the decision to the user.

### Exception handling convention

Do **not** throw ad-hoc exceptions. Throw `CustomException(ErrorCode.X)` where `ErrorCode` (`global/exception/ErrorCode.java`) is the single registry of every error — each entry carries an HTTP status, a stable code (e.g. `TRIP_005`, `AUTH_014`), and a Korean user message. `GlobalExceptionHandler` maps these to `ErrorResponse`. Add a new `ErrorCode` enum constant rather than reusing a loosely-matching one.

### Entity conventions

- UUID primary keys (`@GeneratedValue(strategy = GenerationType.UUID)`).
- `@NoArgsConstructor(access = PROTECTED)` + public domain constructors that **validate invariants and throw `CustomException`** (e.g. `Trip` rejects end-before-start). Keep validation in the entity, not the service.
- Relationships are all `fetch = LAZY`.
- `TripItem` has a unique constraint on `(trip_day_id, order_index)`. Reordering itinerary items must avoid transient duplicate `order_index` collisions within a day — this is the subject of recent reorder bug fixes.

The full entity/column reference lives in `docs/ENTITIES.md`; `scripts/render_entities_md.py` renders it to `docs/ENTITIES.png` (requires Pillow + the macOS AppleSDGothicNeo font). Keep `docs/ENTITIES.md` in sync when you change entities.

### QueryDSL setup

Q-classes are generated into `src/main/generated/` (tracked in `sourceSets`, **not** inside `build/`). This directory is included as a source root and is wiped by `clean`. The annotation processors used are:

- `io.github.openfeign.querydsl:querydsl-apt:6.0`
- `jakarta.annotation:jakarta.annotation-api`
- `jakarta.persistence:jakarta.persistence-api`

When adding a new `@Entity`, run `./gradlew compileJava` to produce its corresponding `Q<EntityName>` class in `src/main/generated/`. Feature `*QueryRepository` classes (e.g. `trip/repository/TripQueryRepository`) hold the QueryDSL queries.

### Data layer

**MariaDB** for local development and deployment (H2 stays only for tests via `src/test/resources/application.properties`). The datasource comes from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (defaults `jdbc:mariadb://localhost:3306/travel_tackle`, `travel`/`travel`); the driver is inferred from the URL. Locally use an existing MariaDB (Homebrew) or `docker compose up -d` (see `docker-compose.yml`, utf8mb4). Create the DB/user once:

```sql
CREATE DATABASE travel_tackle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'travel'@'localhost' IDENTIFIED BY 'travel';
GRANT ALL PRIVILEGES ON travel_tackle.* TO 'travel'@'localhost';
```

JPA DDL is still `update` (schema created/extended on boot; new NOT NULL columns need `@ColumnDefault`, see entity conventions) — Flyway is the planned replacement. Demo data: load `demo_seed_v4.sql` with `mariadb -u travel -ptravel travel_tackle < demo_seed_v4.sql` after the first boot.

## Required environment

- **Database**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (defaults point at local MariaDB `travel_tackle` as `travel`/`travel`).
- **`JWT_SECRET`** (required, must be ≥ 32 bytes) — the app fails to start without it. Tests supply it via `src/test/resources/application.properties`.
- Optional/defaulted: `JWT_ACCESS_TOKEN_SECONDS` (900), `JWT_REFRESH_TOKEN_DAYS` (14), `AUTH_COOKIE_SECURE` (false), `FRONTEND_ORIGIN` (`http://localhost:5173`, used by CORS), `OAUTH_SUCCESS_REDIRECT_URL`, `OAUTH_FAILURE_REDIRECT_URL`.
- Social login: `social.login.enabled=true` plus `KAKAO_CLIENT_ID`/`KAKAO_CLIENT_SECRET` and/or `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`.
- SMTP (`SMTP_HOST`/`PORT`/`USERNAME`/`PASSWORD`/`AUTH`/`STARTTLS`, `MAIL_FROM`) — see `docs/AUTH.md`. Tests use a fake mail sender, not real SMTP.
- `tour.service-key` — TourAPI key.
- Sentry (optional): `application.yaml` maps `sentry.dsn` from `SENTRY_DSN` (blank = disabled, the local default) and `sentry.environment` from `SENTRY_ENVIRONMENT` (`local`/`prod`). `GlobalExceptionHandler` reports through `SentryErrorReporter`: unexpected exceptions at ERROR, `CustomException` with a 5xx status (mail/TourAPI/S3 outages) at WARNING, 4xx never. Each event carries tags `api` (method + route pattern), `screen` (from the frontend's `X-Client-Screen` header, allowed in CORS), `error_code`, a `request_details` context (path, route, masked query params, path variables) and a fingerprint of exception class + route so issues group per API. `sentry-logback` still turns `log.error` into events, but the handler captures the same throwable first (with tags) and the SDK's built-in deduplication drops the second copy; server logs are unchanged. `config/SentryConfig` attaches only the JWT subject as the Sentry user; `send-default-pii` stays false.
- S3 image upload (optional): `application.yaml` maps `aws.s3.bucket`/`region`/`public-base-url` from `AWS_S3_BUCKET` (enables the feature when non-blank), `AWS_REGION` (default `ap-northeast-2`), `AWS_S3_PUBLIC_BASE_URL` (CloudFront domain for read URLs; falls back to the S3 virtual-hosted URL), plus `aws.credentials.access-key`/`secret-key` from `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` (leave blank on AWS to use the IAM role via the SDK default chain). Credentials come from the AWS SDK default chain (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` locally, IAM role on AWS).

## Frontend

`frontend/` is a React 19 + Vite 7 SPA. The Vite dev server (`:5173`) proxies `/api` to the backend at `:8080`, and the backend CORS (`WebConfig`) allows `FRONTEND_ORIGIN` with credentials so the httpOnly auth cookies work cross-origin.
