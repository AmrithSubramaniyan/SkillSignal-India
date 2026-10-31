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
