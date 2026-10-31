/**
 * SkillSignal Local Apply-Agent Runner (Stage 28 — F14)
 * - Runs one task at a time with human-paced delays
 * - Checks login state first; if not logged in, waits for user login in browser
 * - Never sends passwords, cookies, or session tokens to backend
 * - In FILL_ONLY mode, fills fields and uploads resume, then stops BEFORE final Submit
 * - Pauses immediately on CAPTCHA, MFA, legal/visa/unknown questions
 */
export class AgentRunner {
  constructor({ apiBaseUrl, deviceToken, adapter, fetchImpl = globalThis.fetch }) {
    this.apiBaseUrl = apiBaseUrl.replace(/\/$/, '');
    this.deviceToken = deviceToken;
    this.adapter = adapter;
    this.fetchImpl = fetchImpl;
    this.auditLog = [];
  }

  sanitizePayload(payload) {
    const raw = JSON.stringify(payload || {});
    if (/("password"|"cookie"|"cookies"|"sessionToken")/i.test(raw)) {
      throw new Error('SECURITY_VIOLATION: Agent must never log or send passwords, cookies, or session tokens');
    }
    return payload;
  }

  async executeTask(task, jobScenarioUrl, approvedAnswers = {}) {
    // 1. Check login state in persistent profile
    const loggedIn = await this.adapter.isLoggedIn();
    if (!loggedIn) {
      return this.reportBlocker(task.id, 'LOGIN_REQUIRED', 'User is not logged in; waiting for manual login in persistent browser profile');
    }

    // 2. Open job page
    await this.adapter.openJob(jobScenarioUrl);

    // 3. Check for page-level blocker (CAPTCHA / MFA)
    const blocker = await this.adapter.detectBlocker();
    if (blocker.blocked) {
      return this.reportBlocker(task.id, blocker.blockerType, blocker.message);
    }

    // 4. Detect apply type (Closed, External Redirect, or Easy Apply)
    const applyType = await this.adapter.detectApplyType();
    if (applyType === 'CLOSED_LISTING') {
      return this.reportOutcome(task.id, 'CLOSED_LISTING', 'LISTING_CLOSED_OR_404', { applyType });
    }
    if (applyType === 'EXTERNAL_REDIRECT') {
      return this.reportOutcome(task.id, 'EXTERNAL_REDIRECT', 'EXTERNAL_ATS_REDIRECT', { applyType });
    }

    // 5. Inspect questions for Legal / Unknown blockers before filling
    const questions = await this.adapter.readQuestions();
    for (const q of questions) {
      if (q.category === 'LEGAL_CONSENT') {
        return this.reportBlocker(task.id, 'LEGAL_QUESTION', `Paused on legal/consent question: ${q.label}`);
      }
      if (q.category === 'UNKNOWN') {
        return this.reportBlocker(task.id, 'UNKNOWN_QUESTION', `Paused on unrecognised question: ${q.label}`);
      }
    }

    // 6. Fill answers and upload resume
    const filledFields = await this.adapter.fillAnswers(approvedAnswers);
    const resumeUploaded = await this.adapter.uploadResume('./uploads/default_resume.pdf');

    // 7. Enforce FILL_ONLY mode: NEVER click submit!
    if (task.applyMode === 'FILL_ONLY') {
      return this.reportOutcome(task.id, 'FILL_ONLY', 'STOPPED_AT_FINAL_REVIEW_FOR_USER_SUBMIT', {
        filledCount: filledFields.length,
        resumeUploaded,
        submitClicked: false,
      });
    }

    // 8. Mode 3 (AUTO_SUBMIT): click submit and verify confirmation signal
    await this.adapter.submit();
    const conf = await this.adapter.detectConfirmation();
    if (conf.confirmed && conf.confirmationSignal) {
      return this.reportOutcome(task.id, 'CONFIRMED', conf.confirmationSignal, {
        filledCount: filledFields.length,
        resumeUploaded,
        submitClicked: true,
      });
    }

    // No confirmation detected -> Needs Verification (never assume Applied, never auto-retry!)
    return this.reportOutcome(task.id, 'NOT_CONFIRMED', 'MISSING_CONFIRMATION_BANNER', {
      filledCount: filledFields.length,
      resumeUploaded,
      submitClicked: true,
    });
  }

  async reportOutcome(taskId, outcome, confirmationSignal, resultDetails) {
    const cleanDetails = this.sanitizePayload(resultDetails);
    this.auditLog.push({ type: 'REPORT', taskId, outcome, confirmationSignal, resultDetails: cleanDetails });
    const res = await this.fetchImpl(`${this.apiBaseUrl}/api/agents/tasks/${taskId}/report`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Agent-Token': this.deviceToken,
      },
      body: JSON.stringify({
        outcome,
        confirmationSignal,
        resultDetails: cleanDetails,
      }),
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Report failed (${res.status}): ${errText}`);
    }
    return res.json();
  }

  async reportBlocker(taskId, blockerType, blockerMessage) {
    this.sanitizePayload({ blockerType, blockerMessage });
    this.auditLog.push({ type: 'BLOCKER', taskId, blockerType, blockerMessage });
    const res = await this.fetchImpl(`${this.apiBaseUrl}/api/agents/tasks/${taskId}/blocker`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Agent-Token': this.deviceToken,
      },
      body: JSON.stringify({
        blockerType,
        blockerMessage,
      }),
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Blocker report failed (${res.status}): ${errText}`);
    }
    return res.json();
  }
}

