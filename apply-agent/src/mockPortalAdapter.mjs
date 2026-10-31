/**
 * Local Mock Portal Adapter (Stage 28 — F14)
 * Implements the PortalAdapter contract:
 * isLoggedIn, openJob, detectApplyType, readQuestions, fillAnswers,
 * uploadResume, submit, detectConfirmation, detectBlocker.
 *
 * Security rule: Never stores or sends portal passwords, cookies, or session tokens.
 */
export class MockPortalAdapter {
  constructor(portalBaseUrl, fetchImpl = globalThis.fetch) {
    this.portalName = 'mock_portal';
    this.portalBaseUrl = portalBaseUrl.replace(/\/$/, '');
    this.fetchImpl = fetchImpl;
    this.currentHtml = '';
    this.currentUrl = '';
    this.loggedInState = true;
    this.submitClickedCount = 0;
  }

  setLoggedInState(isLoggedIn) {
    this.loggedInState = Boolean(isLoggedIn);
  }

  async isLoggedIn() {
    const res = await this.fetchImpl(`${this.portalBaseUrl}/mock-portal/login?loggedIn=${this.loggedInState}`);
    const html = await res.text();
    return html.includes('data-logged-in="true"');
  }

  async openJob(jobUrl) {
    this.currentUrl = jobUrl.startsWith('http') ? jobUrl : `${this.portalBaseUrl}${jobUrl}`;
    const res = await this.fetchImpl(this.currentUrl);
    this.currentHtml = await res.text();
  }

  async detectApplyType() {
    if (this.currentHtml.includes('data-apply-type="CLOSED_LISTING"') || this.currentHtml.includes('no longer accepting applications')) {
      return 'CLOSED_LISTING';
    }
    if (this.currentHtml.includes('data-apply-type="EXTERNAL_REDIRECT"') || this.currentHtml.includes('id="external-apply-link"')) {
      return 'EXTERNAL_REDIRECT';
    }
    return 'EASY_APPLY';
  }

  async detectBlocker() {
    if (this.currentHtml.includes('data-blocker="CAPTCHA"') || this.currentHtml.includes('id="captcha-challenge"')) {
      return {
        blocked: true,
        blockerType: 'CAPTCHA',
        message: 'CAPTCHA / unusual activity challenge detected on portal page',
      };
    }
    if (this.currentHtml.includes('data-blocker="MFA"')) {
      return {
        blocked: true,
        blockerType: 'MFA',
        message: 'MFA verification required in user browser',
      };
    }
    return { blocked: false };
  }

  async readQuestions() {
    const questions = [];
    const regex = /<label\s+data-question-category="([^"]+)">([^<]+)<\/label>/g;
    let match;
    let idx = 1;
    while ((match = regex.exec(this.currentHtml)) !== null) {
      questions.push({
        id: `q_${idx++}`,
        category: match[1],
        label: match[2].trim(),
      });
    }
    return questions;
  }

  async fillAnswers(approvedAnswersByCategoryOrLabel) {
    const questions = await this.readQuestions();
    const filled = [];
    for (const q of questions) {
      if (q.category === 'LEGAL_CONSENT' || q.category === 'UNKNOWN') {
        throw new Error(`BLOCKER_${q.category}: Question "${q.label}" requires manual user input`);
      }
      const val = approvedAnswersByCategoryOrLabel[q.label] ?? approvedAnswersByCategoryOrLabel[q.category] ?? 'Approved Answer';
      filled.push(`${q.label}=${val}`);
    }
    return filled;
  }

  async uploadResume(resumePath) {
    if (!resumePath) return false;
    return this.currentHtml.includes('id="resume-upload"');
  }

  async submit() {
    this.submitClickedCount += 1;
    const confirmParam = this.currentHtml.includes('action="/mock-portal/submit?confirm=false"') ? 'false' : 'true';
    const res = await this.fetchImpl(`${this.portalBaseUrl}/mock-portal/submit?confirm=${confirmParam}`, {
      method: 'POST',
    });
    this.currentHtml = await res.text();
  }

  async detectConfirmation() {
    if (this.currentHtml.includes('data-confirmed="true"')) {
      const idMatch = /data-confirmation-id="([^"]+)"/.exec(this.currentHtml);
      return {
        confirmed: true,
        confirmationSignal: idMatch ? idMatch[1] : 'CONFIRMATION_BANNER_DETECTED',
      };
    }
    return {
      confirmed: false,
    };
  }
}
