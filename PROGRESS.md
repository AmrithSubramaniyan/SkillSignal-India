# SkillSignal India — Progress Tracker

## Sprint 1: Foundation + POC Core

- [x] **STAGE 0: ENVIRONMENT** — Detect OS & tools, install JDK 17 (user-space Eclipse Temurin 17), configure H2 (PostgreSQL mode) fallback since Docker/PostgreSQL 16 are unavailable locally, initialize Git, create `.gitignore`, `docs/MASTER_PROMPT.md`, `PROGRESS.md`, `DECISIONS.md`.
- [x] **STAGE 1: SKELETON** — `backend/`, `frontend/`, `apply-agent/`, `docker-compose.yml`, `.env.example`, `README.md`, `scripts/`, `GET /api/health` + React home page status display.
- [x] **STAGE 2: DATABASE V1-V3** — Flyway migrations V1-V3 (`users`, `career_profiles`, `resumes`, `resume_versions`, `jobs`, `job_matches`, `applications`, `application_events`, `consent_records`, `audit_logs`, `skills`) + ~300 cross-industry skills seed JSON.
- [ ] **STAGE 3: AUTH (A1, A6)** — Register, login, `GET /api/me`, BCrypt, 24h JWT, logout, password reset (console mail in dev), rate limiting, roles `USER`/`ADMIN`, frontend auth flow & protected routes, integration tests.
- [ ] **STAGE 4: PROFILE, CONSENT, PRIVACY (A2-A5)** — Career profile API & form, versioned/revocable consents, audit logs, `GET /api/privacy/export`, `DELETE /api/privacy/account` (records + files), frontend screens.
- [ ] **STAGE 5: RESUME (B1-B3)** — PDF/DOCX upload up to 5 MB with extension + magic byte validation, `./uploads/{userId}/{uuid}`, PDFBox/POI text extraction, AI parser, verified skills editor, max 3 versions & default flag, security/isolation tests.
- [ ] **STAGE 6: AI ADAPTER + JOBS + MATCH (C1, C2, D1)** — `AiClient` (`mock`, `anthropic`, `openai`) with 30s timeout, retry, 15k char limit, SHA-256 cache, 502 error handling; job creation & parsing; deterministic Java match scoring (60/10/20/10) + skill normalization + conditional suggestions + disclaimer; frontend screens & unit tests.
- [ ] **STAGE 7: TRACKER BASIC + DASHBOARD BASIC (E1, E7 basic)** — Application tracker CRUD, status history events, 409 on duplicate, dashboard summary counts & due follow-ups, `APP_SEED_DEMO=true` seeder (`demo@skillsignal.test`).
- [ ] **STAGE 8: SPRINT 1 CLOSE** — UI polish, security review & headers, OpenAPI (`/swagger-ui.html`), comprehensive `README.md`, GitHub Actions CI workflow, full test suite + frontend production build, tag `sprint1-v1`.

## Current Status
- **Current Stage**: Stage 2 completed, moving to Stage 3
- **Last Commit**: `a49977e` (Stage 1); committing Stage 2 next
- **Next Step**: Stage 3 — Auth (Register, Login, `/api/me`, BCrypt, JWT 24h, Logout, Password Reset, Login Rate Limit, Roles, Frontend Auth)
- **Open GATES**: None blocking Sprint 1
