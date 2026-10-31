# SkillSignal India: ONE Master Prompt (everything, all features, Lite size)

## HOW TO USE TOMORROW (read first, 2 minutes)
Use an AI coding agent that can run terminal commands and edit files (Claude Code, Cursor Agent, Windsurf, Copilot agent mode). A normal chat window cannot do this.
Create an empty folder skillsignal-india, open it in the agent, paste EVERYTHING below the line "BEGIN PROMPT" as your first message.
The agent installs tools, builds, tests and commits by itself, stage by stage, and keeps a file PROGRESS.md so it can continue after a break.
It will keep going on its own, but it WILL stop and ask you at "GATES" (API keys, OAuth setup, legal and portal-terms checks, automatic submission). That is on purpose.
Be realistic: this whole prompt is about 16-20 weeks of work (rough estimate). Tomorrow you will not finish everything. A good first day = Stages 0 and 1 done and committed. Each new day, open the agent in the same folder and type: "Continue from PROGRESS.md."
If the agent's memory gets full or it starts making mistakes, start a new session and type the same sentence. Everything it needs is in PROGRESS.md, DECISIONS.md and docs/MASTER_PROMPT.md (the agent saves this prompt there in Stage 0).
Tell your mentor honestly: Sprint 1 (POC) is the demo. Later sprints are the roadmap.

---

## BEGIN PROMPT

### ROLE
You are a senior full-stack engineer, architect, database designer and QA engineer. Build SkillSignal India: an AI job-search, application-preparation and career-management web app, in the current folder. Work autonomously: create files, run commands, install dependencies, write tests, run them, fix failures, commit. Ask me a question only if truly blocked or at a GATE. Explain decisions briefly in simple English.

### PRODUCT
For job seekers in ANY field, ANY experience level (freshers to senior), ANY country (start with India: LPA, notice period, Indian cities as optional fields). Promise: "Search once. Find relevant jobs. Apply smarter. Track every application. Follow up on time." Core flow: upload resume, paste or find a job, see an explainable match score, prepare an application pack, approve, apply in a permitted way, track, get reminders, learn from results.

### SIZE RULE ("LITE")
Build EVERY feature below, each in its SMALLEST working form: one screen, 1-2 tables, one API set, one happy path, the important error paths, tests for safety-critical logic. No extra options, no custom branding, no big-scale work. If a feature runs past its size (S = 1-2 days, M = 3-5 days, L = 1-2 weeks), stop, tell me what you cut and why.

### STACK
Backend: Java 17+, Spring Boot 3.x (Web, Security, Data JPA, Validation, Flyway, Actuator), jjwt, springdoc-openapi, Apache PDFBox 3.x, Apache POI, Testcontainers. Database: PostgreSQL 16 (Docker). Frontend: React + TypeScript + Vite + Ant Design + react-router + axios. Local apply agent: Node.js + TypeScript + Playwright. Modular monolith (one package per module). Migrations only through new Flyway files (never edit old ones).

### GOLDEN RULES (never break)
- Never invent degrees, skills, experience, salary history, eligibility, or job listings. AI extracts only what is written.
- Never state an application was submitted unless submission is confirmed. If uncertain: "Needs Verification", no auto-retry, user resolves.
- Never bypass or solve CAPTCHA or MFA, evade bot detection, rate limits or access controls, or use stealth plugins. Never store portal passwords, cookies or session tokens. Never scrape sites whose terms forbid it. Do not assume any provider API exists. Automatic submission is allowed only for a source whose capability record permits it AND the user gave per-source consent, completed a dry run and typed a confirmation. Default for every source = Assisted Apply.
- Every private record is scoped by user_id; every API checks ownership (other user's data returns 404).
- Secrets only in environment variables (.env, never committed, .env.example provided). Never log resumes, answers, tokens, keys.
- Validate all input; consistent errors `{"error":"...","details":[]}`; OpenAPI always current.
- Scores and metrics are labelled estimates, computed only from stored data. Never fabricate numbers.
- Never claim something works unless you ran it. After each stage run build + tests and show the real output; fix failures yourself.
- Do not ask me to edit code manually.

### WORKING METHOD (follow exactly)
- Stage 0: save this entire prompt to docs/MASTER_PROMPT.md. Create PROGRESS.md (checklist of all stages with status, last commit, next step, open GATES) and DECISIONS.md (what you chose and why). Update both after every stage.
- After each stage: run tests/build, commit with a clear message, update PROGRESS.md, then CONTINUE to the next stage automatically. STOP only at a GATE or when blocked.
- Tag the end of each sprint: sprint1-v1 ... sprint6-v1, final v1.0.0-lite.
- If you ever resume in a new session: read PROGRESS.md, DECISIONS.md, README.md and docs/MASTER_PROMPT.md first, then continue at the next unfinished stage. Do not redo finished stages.
- Keep AI_PROVIDER=mock by default (rule/dictionary based, works offline). Support anthropic and openai through env (AI_API_KEY, AI_MODEL). All AI calls go through one AiClient interface, with 30s timeout, one retry on invalid JSON, 15,000-character input limit, SHA-256 cache, graceful 502 on failure.

### GATES (stop and ask me; do not proceed until I answer)
- **G1.** Tool installs that need admin rights or a restart.
- **G2.** Real AI provider key (only if I want real AI; otherwise stay in mock).
- **G3.** Before building ANY connector for a real job source: read that provider's current documentation and terms, tell me what they say about API access and automation, and build only what is clearly permitted.
- **G4.** Email-apply OAuth app registration (Google Cloud or Microsoft Entra): give me exact steps; I create the credentials.
- **G5.** Before any real-portal adapter for the browser agent: report that portal's terms on automation, ask which portal, build FILL_ONLY mode only.
- **G6.** Before enabling automatic submission anywhere except the local mock portal: remind me of account-ban and legal risk and ask me to confirm in writing in the chat.
- **G7.** Before any public deployment or real users: remind me to get legal advice (privacy policy, terms, India DPDP Act and other laws that apply, portal terms).

### SPRINT 1: FOUNDATION + POC CORE
- **STAGE 0: ENVIRONMENT.** Detect OS and tools (java -version, node -v, git, docker, docker compose). Install what is missing via winget/choco, brew or apt (GATE G1 if admin needed). Use mvnw. If Docker is unavailable, use local PostgreSQL 16, else H2 (dev only) and tell me. git init, .gitignore (node_modules, target, uploads, .env, IDE files). Save docs as above. Print a table of tool versions.
- **STAGE 1: SKELETON.** Structure: backend/, frontend/, apply-agent/ (empty for now), docker-compose.yml (postgres:16, db skillsignal, user skill), .env.example, README.md, scripts/ (start-all and stop-all for my OS). Backend config via env with safe local defaults, ddl-auto: validate, multipart 5 MB. Vite proxy /api to http://localhost:8080. GET /api/health (status + DB time) shown on the React home page. Checkpoint: React page shows the backend/DB message.
- **STAGE 2: DATABASE V1-V3.** Tables with FKs, indexes, timestamps: users, career_profiles, resumes, resume_versions, jobs, job_matches, applications (UNIQUE user_id+job_id), application_events, consent_records, audit_logs, skills (canonical name + aliases + category). Indexes (user_id), (user_id,status), (user_id,follow_up_date). Seed ~300 cross-industry skills from a JSON file (software, healthcare, finance, sales, teaching, design, logistics, engineering, legal, etc.).
- **STAGE 3: AUTH (A1, A6).** Register, login, GET /api/me, BCrypt, JWT 24h (secret from env), logout, password reset by emailed link (mail abstraction with console implementation in dev), login rate limit, roles USER/ADMIN. Frontend login/register, auth context, axios interceptor, protected routes. Tests: duplicate email, wrong password, 401 without token.
- **STAGE 4: PROFILE, CONSENT, PRIVACY (A2-A5).** Profile form (target titles, years of experience, cities, work modes, expected salary + currency + period, notice period optional, skills, employment types, country, languages, time zone). Consent APIs and toggles (store resume/answers, enable portal, enable email sending), versioned and revocable. Audit log for sensitive actions. GET /api/privacy/export (JSON) and DELETE /api/privacy/account (erases records AND files). Screens: Profile and Settings, Privacy and Data Controls.
- **STAGE 5: RESUME (B1-B3).** Upload PDF/DOCX up to 5 MB (check extension AND magic bytes), save under ./uploads/{userId}/{uuid}, extract text (PDFBox/POI), clear errors for empty/scanned files (OCR not supported). AI parser fills parsed_json (skills, years, education, projects). User edits skills (corrected list drives matching, only verified skills count). Up to 3 versions per user, one default. Tests: bad type, empty, oversize, other user's file returns 404.
- **STAGE 6: AI ADAPTER + JOBS + MATCH (C1, C2, D1).** AiClient with mock (dictionary + regex; works offline), anthropic, openai. Parsers for resume and job. Add job by pasted JD/URL. Match score computed IN JAVA: required skills 60, nice-to-have 10 (full 10 if none listed), experience 20 (full if resume years >= required; scaled below; 10 if unstated), location/work mode 10. Normalise skills (case, punctuation, aliases, synonyms). Result JSON: score, matched/missing required and nice-to-have, experienceFit, locationFit, 3 suggestions (conditional wording, never tell the user to claim skills they lack), disclaimer "This score estimates relevance. It does not guarantee an interview or selection." Screens: Add Job, Job list, Job Details, Match Result (progress circle, green matched tags, red missing tags, "Save to tracker"). Unit tests for scoring edge cases (no division by zero, synonyms, missing experience).
- **STAGE 7: TRACKER BASIC + DASHBOARD BASIC (E1, E7 basic).** Create/update applications, status history events, duplicate returns 409, dashboard counts and follow-ups due. Never mark anything submitted automatically. Seed demo data when APP_SEED_DEMO=true (demo@skillsignal.test, 1 resume, 4 jobs of different match levels, 3 applications).
- **STAGE 8: SPRINT 1 CLOSE.** Polish (spinners, empty states, errors), security review (no secrets in Git, upload path traversal impossible, rate limits, security headers), OpenAPI at /swagger-ui.html, README (what it is, ASCII architecture, one-command setup, env table, switching AI provider, running tests, demo steps, known limits), CI workflow (build + tests). Run full backend tests and npm run build. Tag sprint1-v1. THIS IS THE POC DEMO.
