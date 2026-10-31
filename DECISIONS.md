# Architectural & Technical Decisions (DECISIONS.md)

## Stage 0: Environment & Tooling
1. **Java 17 Installation (User-Space Eclipse Temurin 17)**:
   - *Context*: The machine initially had Java 8 (`1.8.0_202`) on the system PATH and current shell lacked Administrator privileges.
   - *Decision*: Downloaded and extracted Eclipse Temurin JDK 17 (`17.0.20.1+1`) into `C:\Users\God\.jdks` and configured User-level `JAVA_HOME` and `Path` environment variables. This avoids requiring Administrator elevation (GATE G1) or a machine reboot.
2. **Database Fallback (H2 in PostgreSQL Compatibility Mode for Dev/Test)**:
   - *Context*: Neither Docker Desktop nor a local PostgreSQL 16 service is installed on this Windows host, and installing system services requires admin rights.
   - *Decision*: Per Stage 0 instructions (*"If Docker is unavailable, use local PostgreSQL 16, else H2 (dev only) and tell me"*), we provide `docker-compose.yml` for PostgreSQL 16 and configure the backend to default to file-backed/in-memory H2 in `MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=USER,VALUE` when `DB_URL` is not pointed at a live PostgreSQL instance, while keeping standard PostgreSQL 16 driver support ready via environment variables. Flyway migrations are written in standard SQL compatible with both PostgreSQL 16 and H2's PostgreSQL mode, with `ddl-auto: validate`.

## Stage 1: Project Skeleton & Health Endpoint
1. **Modular Monolith Structure**:
   - *Backend (`backend/`)*: Organized by domain package (`common`, `health`, `security`, `user`, `profile`, `consent`, `audit`, `privacy`, `skill`, `resume`, `ai`, `job`, `match`, `application`, `seed`) under `in.skillsignal`.
   - *Frontend (`frontend/`)*: Vite + React 18 + TypeScript + Ant Design 5 + Axios + React Router v6, with Vite dev server proxying `/api` to `http://localhost:8080`.
   - *Consistent Error Contract*: `GlobalExceptionHandler` returns `{"error":"...","details":[]}` across validation, payload size, and business exceptions.

## Stage 2: Database Migrations V1–V3 & Skills Catalog
1. **Three Immutable Flyway Migrations**:
   - `V1__users_profiles_consents_audit.sql`: `users`, `career_profiles`, `consent_records`, `audit_logs` with `ON DELETE CASCADE` and `(user_id)` indexes.
   - `V2__resumes_jobs_matches.sql`: `resumes`, `resume_versions`, `jobs`, `job_matches` with `(user_id)` and `(job_id)` / `(resume_id)` indexes.
   - `V3__applications_events_skills.sql`: `applications` (with `UNIQUE (user_id, job_id)` and indexes on `(user_id)`, `(user_id, status)`, `(user_id, follow_up_date)`), `application_events`, and `skills`.
2. **Cross-Industry Skills Seed (`seed/skills.json`)**:
   - Seeded 305 skills across Software, Data & AI, Healthcare, Finance, Sales & Marketing, Teaching & Education, Design & Creative, Logistics & Operations, Core Engineering, and Legal & HR, including canonical names and common aliases.

## Stage 3: Authentication, Rate Limiting & Password Reset
1. **Stateless JWT + Revocation Timestamp**:
   - Tokens are signed with HMAC-SHA256 (`jjwt` 0.12.6) and expire after 24 hours.
   - `users.token_revoked_before` enables immediate server-side token invalidation on `/api/auth/logout` and `/api/auth/reset-password` without storing raw tokens.
2. **Sliding-Window Login Rate Limiter**:
   - `LoginRateLimiter` tracks login attempts per IP+email window (`AUTH_RATE_LIMIT_MAX_ATTEMPTS`, `AUTH_RATE_LIMIT_WINDOW_SECONDS`) and rejects brute-force bursts with HTTP 429.
3. **Mail Abstraction (`MailSenderService`)**:
   - `ConsoleMailSenderService` implements `MailSenderService` for local/dev without logging raw reset tokens in plain text.

## Stage 4: Profile, Consent & Privacy Controls
1. **Global & India-Ready Career Profile**:
   - Supports LPA / Annual / Monthly salary periods, INR/USD/EUR/GBP currencies, optional notice period in days, Indian and global cities, and cross-industry skill tags.
2. **Versioned, Revocable Consents & Audit Trail**:
   - Every consent grant/revocation (`STORE_RESUME_ANSWERS`, `ENABLE_PORTAL_APPLY`, `ENABLE_EMAIL_SENDING`) records `consent_version`, `granted_at` / `revoked_at`, and writes an immutable `AuditLog` entry.
3. **Complete Data Export & Erasure**:
   - `GET /api/privacy/export` exports all user-owned entities as a single JSON document.
   - `DELETE /api/privacy/account` recursively removes `./uploads/{userId}` on disk (verified to stay inside `uploadRootDir`) and deletes all user database records.

## Stage 5: Resume Upload, Magic-Byte Validation & Verified Skills
1. **Strict File Validation & Path Isolation**:
   - Verifies both file extension (`.pdf`, `.docx`) and binary magic bytes (`%PDF` for PDF, `PK\x03\x04` for DOCX), rejects files `> 5 MB` with 413, and rejects scanned/blank files (`< 10` readable chars) with 422 and a clear OCR-unsupported message.
   - Files are stored under `./uploads/{userId}/{uuid}` using a server-generated UUID so user-supplied filenames can never cause path traversal.
2. **Verified Skills Gate**:
   - AI extraction populates `parsed_json`, and initializes `verified_skills_json`. Users can edit and save `verifiedSkills` and `yearsOfExperience` per version (up to 3 versions per user, 1 default). Only verified skills drive job match scoring.
