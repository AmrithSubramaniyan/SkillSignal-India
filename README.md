# SkillSignal India (Lite POC — `sprint1-v1`)

> **"Search once. Find relevant jobs. Apply smarter. Track every application. Follow up on time."**

SkillSignal India is an AI job-search, application-preparation, and career-management web application designed for job seekers in **any field** (software, healthcare, finance, sales, teaching, design, logistics, core engineering, legal, HR), **any experience level** (freshers to senior leaders), and **any country** (starting with India: LPA salary period, optional notice period in days, Indian cities).

---

## 1. Architecture (Modular Monolith)

```text
+-----------------------------------------------------------------------+
|                        React 18 + TypeScript + Vite                   |
|                 Ant Design 5 UI  (http://localhost:5173)              |
|  [Login/Register] [Dashboard] [Resumes] [Jobs & Match] [Tracker]      |
|  [Profile & Settings] [Privacy, Consents, Data Export & Erasure]      |
+-----------------------------------+-----------------------------------+
                                    |  /api/* (JWT Bearer Auth)
                                    v
+-----------------------------------------------------------------------+
|                 Spring Boot 3.3.5 Backend (Java 17)                   |
|                     (http://localhost:8080)                           |
|                                                                       |
|  +---------------+  +---------------+  +---------------------------+  |
|  | security/auth |  | profile &     |  | resume/                   |  |
|  | BCrypt, JWT,  |  | consent &     |  | Magic-Byte PDF/DOCX Check |  |
|  | Rate Limiter  |  | privacy/audit |  | PDFBox 3 / Apache POI     |  |
|  +---------------+  +---------------+  +---------------------------+  |
|  +---------------+  +---------------+  +---------------------------+  |
|  | ai/           |  | job & match/  |  | application & seed/       |  |
|  | mock/anthropic|  | Java 60/10/   |  | Tracker CRUD, Events,     |  |
|  | /openai +SHA  |  | 20/10 Scorer  |  | Dashboard & Demo Seeder   |  |
|  +---------------+  +---------------+  +---------------------------+  |
+-----------------------------------+-----------------------------------+
                                    |  Flyway Migrations V1 - V3
                                    v
+-----------------------------------------------------------------------+
|   PostgreSQL 16 (Docker Compose)  OR  H2 (PostgreSQL Mode Fallback)   |
|   + Local File Storage (./uploads/{userId}/{uuid})                    |
+-----------------------------------------------------------------------+
```

---

## 2. One-Command Setup & Run

### Start Backend + Frontend (Windows PowerShell)
```powershell
.\scripts\start-all.ps1
```
- **Frontend UI**: `http://localhost:5173`
- **Backend Health Check**: `http://localhost:8080/api/health`
- **OpenAPI / Swagger UI**: `http://localhost:8080/swagger-ui.html`

### Stop All Services
```powershell
.\scripts\stop-all.ps1
```

### Optional: Start PostgreSQL 16 with Docker
If Docker Desktop is installed and running:
```powershell
docker compose up -d
```
Then set `DB_URL=jdbc:postgresql://localhost:5432/skillsignal` and `DB_DRIVER=org.postgresql.Driver` in `.env`. When Docker is unavailable, the backend automatically uses H2 in `MODE=PostgreSQL` (`./data/skillsignal`).

---

## 3. Environment Variables (`.env.example`)

| Variable | Default | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Spring Boot HTTP port |
| `FRONTEND_URL` | `http://localhost:5173` | Frontend origin used in password-reset links |
| `DB_URL` | `jdbc:h2:file:./data/skillsignal;MODE=PostgreSQL...` | JDBC URL (PostgreSQL 16 or H2 fallback) |
| `DB_DRIVER` | `org.h2.Driver` | `org.postgresql.Driver` or `org.h2.Driver` |
| `DB_USERNAME` | `skill` | Database user |
| `DB_PASSWORD` | `skillpass` | Database password |
| `JWT_SECRET` | *(local dev secret)* | HMAC-SHA256 signing key (`>= 32` bytes) |
| `JWT_EXPIRATION_HOURS` | `24` | JWT token validity in hours |
| `AUTH_RATE_LIMIT_MAX_ATTEMPTS` | `10` | Max login attempts per IP+email window |
| `AUTH_RATE_LIMIT_WINDOW_SECONDS` | `60` | Login rate-limit sliding window |
| `UPLOAD_DIR` | `./uploads` | Root directory for isolated user resume files |
| `AI_PROVIDER` | `mock` | `mock` (offline default), `anthropic`, or `openai` |
| `AI_API_KEY` | *(empty)* | API key when `AI_PROVIDER` is `anthropic` or `openai` |
| `AI_MODEL` | `claude-3-5-sonnet-latest` | Model name for Anthropic or OpenAI |
| `AI_TIMEOUT_SECONDS` | `30` | Timeout per AI HTTP call |
| `APP_SEED_DEMO` | `false` (`true` in `.env.example`) | Seeds `demo@skillsignal.test` on startup |

---

## 4. Switching AI Provider (`mock` / `anthropic` / `openai`)

By default, `AI_PROVIDER=mock` runs 100% offline using the 305-skill cross-industry dictionary (`seed/skills.json`) and deterministic regex extractors.

To switch to a live LLM provider (GATE G2):
1. Copy `.env.example` to `.env`.
2. Set `AI_PROVIDER=anthropic` (or `openai`), `AI_API_KEY=<your-key>`, and `AI_MODEL=claude-3-5-sonnet-latest` (or `gpt-4o-mini`).
3. All AI calls go through `AiClient` (`DelegatingAiClient`) with:
   - **15,000-character** input limit (400 if exceeded)
   - **30-second** timeout
   - **1 automatic retry** if the provider returns malformed JSON
   - **SHA-256** content cache
   - **HTTP 502 Bad Gateway** graceful error on upstream failure

> **Note**: The **Match Score** is **always computed deterministically in Java** (`MatchScoringService`), never by an LLM.

---

## 5. Running Tests & Production Build

### Backend JUnit 5 + MockMvc Suite
```powershell
cd backend
.\mvnw.cmd test
```

### Frontend TypeScript Check + Vite Build
```powershell
cd frontend
npm run build
```

---

## 6. POC Demo Walkthrough (`APP_SEED_DEMO=true`)

1. Start the application with `APP_SEED_DEMO=true` (or register a new account).
2. Sign in with the pre-seeded demo account:
   - **Email**: `demo@skillsignal.test`
   - **Password**: `DemoPass123!`
3. **Dashboard (`/`)**:
   - View live `/api/health` backend + database timestamp, 4 saved jobs, 3 tracked applications, pipeline breakdown, and overdue/due follow-up reminders.
4. **Resumes (`/resumes`)**:
   - Inspect `Ananya_Rao_Backend_Resume.pdf` (v1) or upload your own PDF/DOCX (`<= 5 MB`, validated by both file extension and binary magic bytes).
   - Edit and save **Verified Skills** — only verified skills count toward job matching.
5. **Jobs & Match (`/jobs`)**:
   - Click **Analyze Match** on any of the 4 demo jobs (spanning High, Good, Partial, and Low cross-industry match scores), or click **Add Job (Paste JD / URL)** to paste any job description.
   - Inspect the 100-point breakdown (Required Skills `/60`, Nice-to-Have `/10`, Experience `/20`, Location/Mode `/10`), green matched skill tags, red missing skill tags, 3 conditional suggestions, and the mandatory disclaimer.
   - Click **Save to Tracker**.
6. **Tracker (`/tracker`)**:
   - Update application statuses (`SAVED`, `PREPARING`, `READY_TO_APPLY`, `APPLIED_CONFIRMED`, `NEEDS_VERIFICATION`, `INTERVIEWING`, `OFFER`, `REJECTED`, `WITHDRAWN`), set follow-up dates, and inspect the chronological **Status History** timeline.
7. **Profile (`/profile`) & Privacy (`/privacy`)**:
   - Update LPA salary expectations, notice period, preferred Indian/global cities, toggle versioned consents (`STORE_RESUME_ANSWERS`, `ENABLE_PORTAL_APPLY`, `ENABLE_EMAIL_SENDING`), inspect the Audit Log, download a complete JSON data export (`GET /api/privacy/export`), or test full account & file erasure (`DELETE /api/privacy/account`).

---

## 7. Known Limits (Sprint 1 Lite POC)

- **No OCR for Scanned Image PDFs**: PDFs must contain a readable text layer; scanned image-only PDFs return HTTP 422 with a clear message.
- **Max 3 Resume Versions per User**: Users can store up to 3 resume versions at a time (5 MB max per file).
- **Assisted Apply Only**: Applications are never automatically submitted or marked submitted without user confirmation; uncertain states use `NEEDS_VERIFICATION`.
- **Local Dev Database Fallback**: Uses H2 in PostgreSQL compatibility mode when Docker/PostgreSQL 16 is not installed on the host machine.
