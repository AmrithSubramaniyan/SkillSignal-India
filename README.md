# SkillSignal India (`v1.0.0-lite` — Sprints 1 to 6 Complete)

> **"Search once. Find relevant jobs. Apply smarter. Track every application. Follow up on time."**

SkillSignal India is an AI job-search, application-preparation, and career-management web application designed for job seekers in **any field** (software, healthcare, finance, sales, teaching, design, logistics, core engineering, legal, HR), **any experience level** (freshers to senior leaders), and **any country** (starting with India: LPA salary period, optional notice period in days, Indian cities).

---

## 1. Architecture (Modular Monolith + Local Browser Apply-Agent)

```text
+-----------------------------------------------------------------------+
|                        React 18 + TypeScript + Vite                   |
|                 Ant Design 5 UI  (http://localhost:5173)              |
|  [Login/Register] [Dashboard] [Resumes] [AI Job Search] [Jobs & Match]|
|  [Sources] [Tracker] [Reminders] [Notifications] [Companies]          |
|  [Assisted Apply] [Approval Queue] [Auto-Apply Centre] [Apply Rules]  |
|  [Analytics] [Career & Prep] [Plans & Feedback] [Profile] [Privacy]   |
+-----------------------------------+-----------------------------------+
                                    |  /api/* (JWT Bearer Auth)
                                    v
+-----------------------------------------------------------------------+
|                 Spring Boot 3.3.5 Backend (Java 17)                   |
|                     (http://localhost:8080)                           |
|                                                                       |
|  +---------------+  +---------------+  +---------------------------+  |
|  | security/auth |  | profile,      |  | resume/                   |  |
|  | BCrypt, JWT,  |  | consent,      |  | Magic-Byte PDF/DOCX Check |  |
|  | Rate Limiter  |  | privacy/audit |  | Tailored Draft + Truth    |  |
|  +---------------+  +---------------+  +---------------------------+  |
|  +---------------+  +---------------+  +---------------------------+  |
|  | ai/ & search/ |  | job & match/  |  | application, reminder,    |  |
|  | NL Chips,     |  | Java 60/10/   |  | company & hiring intel    |  |
|  | Greenhouse API|  | 20/10 Scorer  |  | 13-State Tracker Machine  |  |
|  +---------------+  +---------------+  +---------------------------+  |
|  +---------------+  +---------------+  +---------------------------+  |
|  | answer, pack, |  | rule, email,  |  | analytics, career,        |  |
|  | eligibility,  |  | safety, agent,|  | production/               |  |
|  | submission    |  | learning      |  | Roadmap, Mock Interview   |  |
|  +---------------+  +---------------+  +---------------------------+  |
+-----------------------------------+-----------------------------------+
                                    |  Flyway Migrations V1 - V10
                                    v
+-----------------------------------------------------------------------+
|   PostgreSQL 16 (Docker Compose)  OR  H2 (PostgreSQL Mode Fallback)   |
|   + Local File Storage (./uploads/{userId}/{uuid})                    |
|   + apply-agent/ (Node + TypeScript + Playwright Local Agent + Mock)  |
+-----------------------------------------------------------------------+
```

---

## 2. Exact Commands to Start, Stop, Test, Backup & Restore

### Start Backend + Frontend (Windows PowerShell)
```powershell
.\scripts\start-all.ps1
```
- **Frontend UI**: `http://localhost:5173`
- **Backend Health Check**: `http://localhost:8080/api/health`
- **OpenAPI / Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Local Mock Portal (Stage 28)**: `http://localhost:8080/mock-portal/jobs/easy-apply`

### Stop All Services
```powershell
.\scripts\stop-all.ps1
```

### Run Complete Test Suites
```powershell
# 1. Backend JUnit 5 + MockMvc Suite (Stages 0-33)
cd backend
.\mvnw.cmd test

# 2. Frontend TypeScript Check + Production Vite Build
cd ../frontend
npm run build

# 3. Local Apply-Agent Test Suite against Mock Portal
cd ../apply-agent
npm test
```

### Daily Database Backup & Tested Restore
```powershell
# Create compressed backup archive
.\scripts\backup-db.ps1

# Restore & verify archive integrity
.\scripts\restore-db.ps1 -ArchivePath .\backups\skillsignal-db-backup-<timestamp>.zip
```

### Staging Docker Compose
```powershell
docker compose -f docker-compose.staging.yml up -d --build
```

---

## 3. Environment Variables (`.env.example`)

| Variable | Default | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Spring Boot HTTP port |
| `FRONTEND_URL` | `http://localhost:5173` | Frontend origin used in password-reset and approval links |
| `DB_URL` | `jdbc:h2:file:./data/skillsignal;MODE=PostgreSQL...` | JDBC URL (PostgreSQL 16 or H2 fallback) |
| `DB_DRIVER` | `org.h2.Driver` | `org.postgresql.Driver` or `org.h2.Driver` |
| `DB_USERNAME` | `skill` | Database user |
| `DB_PASSWORD` | `skillpass` | Database password |
| `JWT_SECRET` / `APP_JWT_SECRET` | *(local dev secret)* | HMAC-SHA256 signing key (`>= 32` bytes) |
| `JWT_EXPIRATION_HOURS` | `24` | JWT token validity in hours |
| `APP_MAILBOX_ENCRYPTION_KEY` | *(32-byte key)* | AES-256-GCM key for encrypting OAuth mailbox token references |
| `AUTH_RATE_LIMIT_MAX_ATTEMPTS` | `10` | Max login attempts per IP+email window |
| `AUTH_RATE_LIMIT_WINDOW_SECONDS` | `60` | Login rate-limit sliding window |
| `UPLOAD_DIR` | `./uploads` | Root directory for isolated user resume & evidence files |
| `AI_PROVIDER` / `APP_AI_PROVIDER` | `mock` | `mock` (offline default), `anthropic`, or `openai` |
| `AI_API_KEY` | *(empty)* | API key when `AI_PROVIDER` is `anthropic` or `openai` |
| `AI_MODEL` | `claude-3-5-sonnet-latest` | Model name for Anthropic or OpenAI |
| `AI_TIMEOUT_SECONDS` | `30` | Timeout per AI HTTP call |
| `APP_SEED_DEMO` | `false` (`true` in `.env.example`) | Seeds `demo@skillsignal.test` on startup |

---

## 4. What Was Built Across Sprints 1–6

- **Sprint 1 (`sprint1-v1`)**: Foundation, Auth (`BCrypt` + 24h JWT + rate limiter), Career Profile, Versioned Consents, DPDP Data Export & Account Erasure, PDF/DOCX Resume Upload with magic-byte validation & Verified Skills Editor, AI Adapter (`mock`/`anthropic`/`openai`), Deterministic 60/10/20/10 Match Scorer, Basic Tracker & Dashboard.
- **Sprint 2 (`sprint2-v1`)**: Source Capability Registry (`manual`, `company_ats`, `email_apply` enabled; Naukri, LinkedIn, Indeed, Foundit, Instahyre, Wellfound disabled as `"no verified API access"`), Greenhouse Public Job Board read-only connector, Natural-Language Search to Filter Chips (22+ test queries), Parallel Search Orchestrator, Deduplication & Stale Detection, Job Comparison (2–3 jobs), Tailored Resume Draft & Truthfulness Check blocking unsupported claims.
- **Sprint 3 (`sprint3-v1`)**: 13-State Application Tracker State Machine (`INTERESTED`, `READY_TO_APPLY`, `AWAITING_APPROVAL`, `APPLIED`, `SUBMISSION_FAILED`, `ASSESSMENT`, `INTERVIEW`, `ON_HOLD`, `OFFER`, `REJECTED`, `WITHDRAWN`, `CLOSED`, `NEEDS_VERIFICATION`), Reminders & Per-User Time Zone (`Asia/Kolkata`), Quiet Hours, Sent-Log Dedup, T1/T2/T3 Companies & Hiring Intelligence (`"listings are not hires"`), Full Dashboard.
- **Sprint 4 (`sprint4-v1`)**: Question Classifier & Answer Library (`FACTUAL`/`PREFERENCE` auto-fill; `LEGAL_CONSENT`/visa/background-check/`UNKNOWN` always pause), Application Pack + Readiness Score (`0–100`), Strict Match Rule (`MIN_SKILLS=3`, 50% required-skill guard, experience range policy, 9-check eligibility function tested across 44 fixtures), 14-State Submission State Machine with Idempotency Keys & Immutable Attempt Audit, Mode 1 Assisted Apply & Mode 2 Approval Queue (batch approve with exceptions, single-use expiring approval token), Auto-Created +7d Follow-Up Reminder after `Applied`.
- **Sprint 5 (`sprint5-v1`)**: Ordered Auto-Apply Rules (max 5 rules, max 3 conditions, `T1=REVIEW`, 30-day company cooldown) & Zero-Submission Dry Run (`dry_run=true`), OAuth Email-Apply (`AES-256-GCM` encrypted token ref, zero passwords, approval gate, `"Sent"` not confirmed until ack upgrade), Full Safety Governor (User & Admin Kill Switch, 3-failure auto-pause, CAPTCHA/unusual-activity/rejection-spike/terms-change triggers, row-level locks, circuit breakers), Mode 3 Guided Enablement, Local Mock Portal & Playwright Apply-Agent (`FILL_ONLY` never clicks submit), Personal Learning Loop (consent-gated rule suggestions) & Auto-Apply Centre.
- **Sprint 6 (`sprint6-v1`, `v1.0.0-lite`)**: Job-Search Analytics (6 core metrics with sample sizes, explicit response rate formula, disclosed salary ranges only, source health table), Career Growth (Weekly Skill Roadmap from missing skills with checkbox progress & config learning links, JD Interview Prep, Topic Sets for `Java`, `Spring Boot`, `SQL`, `Microservices`, `Kafka`, `DSA` + any custom field, Text Mock Interview with Rubric Feedback & Weak-Topic List, mandatory non-outcome disclaimer), Free vs Paid Feature-Flag Table (no payments), Staging Docker Compose, Tested DB Backup & Restore Scripts, Security Report (`docs/SECURITY_REPORT.md`), Product Feedback, Usage Events, Known Issues & **GATE G7** legal reminder.
