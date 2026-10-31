# SkillSignal India — Security & Production Readiness Report (`v1.0.0-lite`)

## 1. Authentication, Authorization & Cross-User Isolation
- **Password Hashing**: `BCryptPasswordEncoder` (`Spring Security 6`). Raw passwords are never stored or logged.
- **Session Tokens**: Stateless 24-hour HMAC-SHA256 JWT (`APP_JWT_SECRET`), with server-side token revocation blocklist on `/api/auth/logout`.
- **Cross-User Data Isolation**: Every JPA repository query and controller endpoint scopes lookups by `(id, userId)` from the authenticated principal (`@AuthenticationPrincipal AuthenticatedUser`), returning HTTP `404 Not Found` on cross-user access attempts (verified across Sprints 1–6).

## 2. Credential, Token & Cookie Protection (Email-Apply & Apply-Agent)
- **Email-Apply OAuth (`F13`)**:
  - Rejects any request containing a `password` field (`HTTP 400`).
  - Encrypts OAuth token references at rest in `connected_mailboxes.encrypted_token_ref` using **AES-256-GCM** (`app.mailbox.encryption-key` / `APP_MAILBOX_ENCRYPTION_KEY`) with a random 96-bit IV per encryption.
  - Excludes token references from DTOs, `toString()`, and `audit_logs`.
- **Local Browser Apply-Agent (`F14`)**:
  - Uses a persistent Chromium profile on the user's own machine.
  - Authenticates via a revocable SHA-256 hashed device token (`apply_agents.device_token_hash`).
  - **Zero-Credential Rule**: Both `AgentRunner.sanitizePayload` and `AgentAndPortalService.rejectIfContainsCredentialsOrCookies` reject any payload containing `password`, `cookie`, `cookies`, or `sessionToken` (`HTTP 400`).

## 3. File Upload, Rate Limiting & HTTP Security Headers
- **Resume Upload Validation (`B1`)**: Enforces 5 MB size cap, `.pdf`/`.docx` extension allowlist, and magic-byte header verification (`%PDF-` for PDF, `PK\x03\x04` for DOCX). Files are stored outside the web root under `./uploads/{userId}/{uuid}`.
- **Rate Limiting & Safety Governor (`F16`)**:
  - Auth endpoints (`/api/auth/login`, `/api/auth/register`, `/api/auth/forgot-password`) are protected by an IP/email sliding-window rate limiter (`HTTP 429`).
  - Auto-apply sources are protected by `SafetyGovernorService` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` row-level locks, per-source circuit breakers, automatic pause after 3 consecutive failures or any `CAPTCHA`/`UNUSUAL_ACTIVITY`/`REJECTION_SPIKE`/`TERMS_CHANGE` signal, and user/admin emergency kill switches.
- **Security Headers (`SecurityConfig`)**:
  - `X-Frame-Options: DENY`
  - `X-Content-Type-Options: nosniff`
  - Stateless session management (`SessionCreationPolicy.STATELESS`).

## 4. Dependency Vulnerability Scan & Secrets Management
- **Backend Dependencies (`pom.xml`)**: Spring Boot `3.3.5`, Java `17`, JJWT `0.12.6`, Apache PDFBox `2.0.32`, Apache POI `5.3.0`, Flyway `10`.
- **Frontend Dependencies (`package.json`)**: React `18.3`, Vite `5.4`, Ant Design `5.21`, TypeScript `5.6`.
- **Secrets from Environment**: All production secrets (`POSTGRES_PASSWORD`, `APP_JWT_SECRET`, `APP_MAILBOX_ENCRYPTION_KEY`, `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) are injected exclusively via environment variables (see `.env.example` and `docker-compose.staging.yml`).

## 5. Daily Database Backup & Tested Restore (`H2/H3`)
- Automated backup script: `scripts/backup-db.ps1` (supports both PostgreSQL `pg_dump` and local H2/snapshot archive compression).
- Automated restore & verification script: `scripts/restore-db.ps1` (extracts and verifies restored database files).

## 6. GATE G7 Reminder (Legal & Portal Terms Sign-Off Before Public Launch)
> **IMPORTANT — GATE G7 REMINDER**:
> Before deploying SkillSignal India to public users:
> 1. Have qualified Indian legal counsel review the **Digital Personal Data Protection (DPDP) Act, 2023** consent notices, data export (`GET /api/privacy/export`), and account erasure (`DELETE /api/privacy/account`) workflows.
> 2. Verify third-party job portal Terms of Service. Keep Naukri, LinkedIn, Indeed, Foundit, Instahyre, and Wellfound in **Assisted-Only** mode unless written partner API permission is obtained.

