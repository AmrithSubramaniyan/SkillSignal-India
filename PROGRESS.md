# SkillSignal India — Progress Tracker

## Sprint 1: Foundation + POC Core (`sprint1-v1`)

- [x] **STAGE 0: ENVIRONMENT** — Detect OS & tools, install JDK 17 (user-space Eclipse Temurin 17), configure H2 (PostgreSQL mode) fallback since Docker/PostgreSQL 16 are unavailable locally, initialize Git, create `.gitignore`, `docs/MASTER_PROMPT.md`, `PROGRESS.md`, `DECISIONS.md`.
- [x] **STAGE 1: SKELETON** — `backend/`, `frontend/`, `apply-agent/`, `docker-compose.yml`, `.env.example`, `README.md`, `scripts/`, `GET /api/health` + React home page status display.
- [x] **STAGE 2: DATABASE V1-V3** — Flyway migrations V1-V3 (`users`, `career_profiles`, `resumes`, `resume_versions`, `jobs`, `job_matches`, `applications`, `application_events`, `consent_records`, `audit_logs`, `skills`) + ~300 cross-industry skills seed JSON.
- [x] **STAGE 3: AUTH (A1, A6)** — Register, login, `GET /api/me`, BCrypt, 24h JWT, logout, password reset (console mail in dev), rate limiting, roles `USER`/`ADMIN`, frontend auth flow & protected routes, integration tests.
- [x] **STAGE 4: PROFILE, CONSENT, PRIVACY (A2-A5)** — Career profile API & form, versioned/revocable consents, audit logs, `GET /api/privacy/export`, `DELETE /api/privacy/account` (records + files), frontend screens.
- [x] **STAGE 5: RESUME (B1-B3)** — PDF/DOCX upload up to 5 MB with extension + magic byte validation, `./uploads/{userId}/{uuid}`, PDFBox/POI text extraction, AI parser, verified skills editor, max 3 versions & default flag, security/isolation tests.
- [x] **STAGE 6: AI ADAPTER + JOBS + MATCH (C1, C2, D1)** — `AiClient` (`mock`, `anthropic`, `openai`) with 30s timeout, retry, 15k char limit, SHA-256 cache, 502 error handling; job creation & parsing; deterministic Java match scoring (60/10/20/10) + skill normalization + conditional suggestions + disclaimer; frontend screens & unit tests.
- [x] **STAGE 7: TRACKER BASIC + DASHBOARD BASIC (E1, E7 basic)** — Application tracker CRUD, status history events, 409 on duplicate, dashboard summary counts & due follow-ups, `APP_SEED_DEMO=true` seeder (`demo@skillsignal.test`).
- [x] **STAGE 8: SPRINT 1 CLOSE** — UI polish, security review & headers, OpenAPI (`/swagger-ui.html`), comprehensive `README.md`, GitHub Actions CI workflow (`.github/workflows/ci.yml`), full test suite + frontend production build, tag `sprint1-v1`.

## Sprint 2: Jobs and Matching (B4-B6, C3-C10, D2-D6)

- [x] **STAGE 9: SOURCE REGISTRY + CONNECTOR (C3, C4)** — `source_capabilities` table & seeded rows (`manual`, `company_ats`, `email_apply`, plus disabled rows for Naukri, LinkedIn, Indeed, Foundit, Instahyre, Wellfound marked `"no verified API access"`), Default Assisted-only, Admin switch, `GET /api/sources/capabilities`, **GATE G3** approval, and Greenhouse Public Job Board API read-only GET connector (`GreenhousePublicBoardConnector`).
- [x] **STAGE 10: SEARCH (C5-C7)** — Natural-language query to editable filter chips (rule-based first, AI assist; 22 test queries), parallel search orchestrator with per-source timeout & partial-failure resilience, `AI Job Search` and `Connected Sources and Permissions (lite)` screens.
- [x] **STAGE 11: DEDUP, STALE, SAVED (C8-C10)** — Duplicate merging (`company + fuzzy title + location`) preserving all source records, stale flag only on reliable evidence (`404` or `closed` flag), saved jobs, saved searches with scheduled daily refresh.
- [x] **STAGE 12: FULL MATCH (D2-D6)** — Salary & qualification fit (`"not enough data"` when missing), weights in config file (`app.match.weights.*`), aggregated missing skills across saved jobs, Compare 2–3 jobs screen, thumbs up/down feedback stored.
- [ ] **STAGE 13: RESUME TOOLS (B4-B6)** — Keyword gap list & 3 tips, tailored draft (AI rewords/reorders existing content only), before/after diff, truthfulness check tracing every claim to profile/resume and blocking unsupported claims, user approval gate, download DOCX/PDF, Sprint 2 test suite, tag `sprint2-v1`.

## Current Status
- **Current Stage**: Stage 13 — Resume Tools (`B4-B6`) & Sprint 2 Close (`sprint2-v1`)
- **Last Commit**: Stage 12 (`V6__match_feedback_and_tailored_resumes.sql`, configurable match weights, salary/qualification fit, missing skills aggregation, 2–3 job comparison, match feedback)
- **Next Step**: Complete Stage 13 (`TailoredResume`, `TruthfulnessVerifier` blocking unsupported claims, before/after diff, user approval gate, DOCX/PDF export, full Sprint 2 test suite, tag `sprint2-v1`).
- **Open GATES**:
  - **GATE G3 (RESOLVED)**: User confirmed building the Greenhouse Public Job Board API read-only GET connector (`Assisted Apply` only) and keeping Naukri, LinkedIn, Indeed, Foundit, Instahyre, and Wellfound disabled.
