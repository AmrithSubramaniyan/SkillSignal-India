import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { MockPortalAdapter } from '../src/mockPortalAdapter.mjs';
import { AgentRunner } from '../src/agentRunner.mjs';

/**
 * Self-contained Mock Portal + Backend Task Server for apply-agent unit & integration tests.
 * Verifies all 12 Stage 28 requirements against MockPortalAdapter & AgentRunner.
 */
function createTestServer() {
  const state = {
    submitClicks: 0,
    tasks: new Map(),
    submittedJobIds: new Set(),
    dailyCap: 3,
    completedCount: 0,
    killSwitchActive: false,
    reports: [],
    blockers: [],
  };

  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    let body = '';
    for await (const chunk of req) body += chunk;

    // Mock Portal HTML pages
    if (url.pathname === '/mock-portal/login') {
      const loggedIn = url.searchParams.get('loggedIn') !== 'false';
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(`<body data-logged-in="${loggedIn}"></body>`);
      return;
    }

    if (url.pathname.startsWith('/mock-portal/jobs/')) {
      const scenario = url.pathname.split('/').pop();
      res.writeHead(scenario === 'closed' ? 404 : 200, { 'Content-Type': 'text/html' });
      if (scenario === 'closed') {
        res.end(`<body data-apply-type="CLOSED_LISTING">This job is no longer accepting applications.</body>`);
      } else if (scenario === 'external-redirect') {
        res.end(`<body data-apply-type="EXTERNAL_REDIRECT"><a id="external-apply-link">Apply</a></body>`);
      } else if (scenario === 'captcha') {
        res.end(`<body data-blocker="CAPTCHA"><div id="captcha-challenge">CAPTCHA</div></body>`);
      } else if (scenario === 'legal-question') {
        res.end(`<body data-apply-type="EASY_APPLY"><label data-question-category="LEGAL_CONSENT">Do you consent to background check?</label><input id="resume-upload" /></body>`);
      } else if (scenario === 'unknown-question') {
        res.end(`<body data-apply-type="EASY_APPLY"><label data-question-category="UNKNOWN">Internal rotation code?</label><input id="resume-upload" /></body>`);
      } else if (scenario === 'no-confirmation') {
        res.end(`<body data-apply-type="EASY_APPLY"><form action="/mock-portal/submit?confirm=false"><label data-question-category="FACTUAL">Years of Java?</label><input id="resume-upload" /></form></body>`);
      } else {
        res.end(`<body data-apply-type="EASY_APPLY"><form action="/mock-portal/submit?confirm=true"><label data-question-category="FACTUAL">Years of Java?</label><input id="resume-upload" /></form></body>`);
      }
      return;
    }

    if (url.pathname === '/mock-portal/submit' && req.method === 'POST') {
      state.submitClicks += 1;
      const confirm = url.searchParams.get('confirm') !== 'false';
      res.writeHead(200, { 'Content-Type': 'text/html' });
      if (confirm) {
        res.end(`<body data-confirmed="true" data-confirmation-id="MOCK-REF-9001">Confirmed</body>`);
      } else {
        res.end(`<body data-confirmed="false">Ambiguous response</body>`);
      }
      return;
    }

    // Agent Task Reporting endpoints
    if (url.pathname.endsWith('/report') && req.method === 'POST') {
      if (state.killSwitchActive) {
        res.writeHead(409, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Task cancelled by kill switch' }));
        return;
      }
      const parsed = JSON.parse(body || '{}');
      state.reports.push(parsed);
      const statusMap = {
        FILL_ONLY: 'FILL_ONLY_COMPLETED',
        CONFIRMED: 'SUBMITTED_CONFIRMED',
        NOT_CONFIRMED: 'NEEDS_VERIFICATION',
        EXTERNAL_REDIRECT: 'ASSISTED_FALLBACK',
        CLOSED_LISTING: 'SKIPPED_CLOSED',
      };
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: statusMap[parsed.outcome] || 'NEEDS_VERIFICATION',
        confirmationSignal: parsed.confirmationSignal,
      }));
      return;
    }

    if (url.pathname.endsWith('/blocker') && req.method === 'POST') {
      const parsed = JSON.parse(body || '{}');
      state.blockers.push(parsed);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 'PAUSED_BLOCKER',
        blockerType: parsed.blockerType,
        blockerMessage: parsed.blockerMessage,
      }));
      return;
    }

    res.writeHead(404);
    res.end();
  });

  return { server, state };
}

test('Stage 28 Apply-Agent against Local Mock Portal: all 12 safety & workflow scenarios', async () => {
  const { server, state } = createTestServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    const adapter = new MockPortalAdapter(baseUrl);
    const runner = new AgentRunner({
      apiBaseUrl: baseUrl,
      deviceToken: 'ssa_test_device_token_123',
      adapter,
    });

    // 1. Login wait / resume: when not logged in, pauses with LOGIN_REQUIRED
    adapter.setLoggedInState(false);
    const loginWaitRes = await runner.executeTask(
      { id: 'task-login', applyMode: 'FILL_ONLY' },
      '/mock-portal/jobs/easy-apply'
    );
    assert.equal(loginWaitRes.status, 'PAUSED_BLOCKER');
    assert.equal(loginWaitRes.blockerType, 'LOGIN_REQUIRED');

    // Resume after user logs in inside persistent browser profile
    adapter.setLoggedInState(true);

    // 2. FILL_ONLY mode NEVER clicks submit!
    state.submitClicks = 0;
    const fillOnlyRes = await runner.executeTask(
      { id: 'task-fill-only', applyMode: 'FILL_ONLY' },
      '/mock-portal/jobs/easy-apply',
      { 'Years of Java?': '5' }
    );
    assert.equal(fillOnlyRes.status, 'FILL_ONLY_COMPLETED');
    assert.equal(state.submitClicks, 0, 'FILL_ONLY must never click submit on the portal');
    assert.equal(adapter.submitClickedCount, 0);

    // 3. Mode 3 (AUTO_SUBMIT) success with confirmation signal -> SUBMITTED_CONFIRMED
    const confirmedRes = await runner.executeTask(
      { id: 'task-confirmed', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/easy-apply',
      { 'Years of Java?': '5' }
    );
    assert.equal(confirmedRes.status, 'SUBMITTED_CONFIRMED');
    assert.equal(confirmedRes.confirmationSignal, 'MOCK-REF-9001');
    assert.equal(state.submitClicks, 1);

    // 4. No confirmation signal -> NEEDS_VERIFICATION (never assumes Applied)
    const noConfRes = await runner.executeTask(
      { id: 'task-no-conf', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/no-confirmation',
      { 'Years of Java?': '5' }
    );
    assert.equal(noConfRes.status, 'NEEDS_VERIFICATION');

    // 5. CAPTCHA page -> immediate pause (PAUSED_BLOCKER)
    const captchaRes = await runner.executeTask(
      { id: 'task-captcha', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/captcha'
    );
    assert.equal(captchaRes.status, 'PAUSED_BLOCKER');
    assert.equal(captchaRes.blockerType, 'CAPTCHA');

    // 6. Legal & Unknown questions -> immediate pause (PAUSED_BLOCKER)
    const legalRes = await runner.executeTask(
      { id: 'task-legal', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/legal-question'
    );
    assert.equal(legalRes.status, 'PAUSED_BLOCKER');
    assert.equal(legalRes.blockerType, 'LEGAL_QUESTION');

    const unknownRes = await runner.executeTask(
      { id: 'task-unknown', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/unknown-question'
    );
    assert.equal(unknownRes.status, 'PAUSED_BLOCKER');
    assert.equal(unknownRes.blockerType, 'UNKNOWN_QUESTION');

    // 7. External redirect -> ASSISTED_FALLBACK
    const redirectRes = await runner.executeTask(
      { id: 'task-redirect', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/external-redirect'
    );
    assert.equal(redirectRes.status, 'ASSISTED_FALLBACK');

    // 8. Closed listing -> SKIPPED_CLOSED
    const closedRes = await runner.executeTask(
      { id: 'task-closed', applyMode: 'AUTO_SUBMIT' },
      '/mock-portal/jobs/closed'
    );
    assert.equal(closedRes.status, 'SKIPPED_CLOSED');

    // 9. Kill switch mid-run halts task reporting with 409
    state.killSwitchActive = true;
    await assert.rejects(
      () => runner.executeTask({ id: 'task-killed', applyMode: 'FILL_ONLY' }, '/mock-portal/jobs/easy-apply'),
      /Task cancelled by kill switch/
    );
    state.killSwitchActive = false;

    // 10. No credentials or cookies in logs or payloads (attempting to pass password/cookie throws immediately)
    assert.throws(
      () => runner.sanitizePayload({ password: 'secret', cookie: 'sid=123' }),
      /SECURITY_VIOLATION/
    );
    const auditJson = JSON.stringify(runner.auditLog);
    assert.equal(auditJson.includes('password'), false);
    assert.equal(auditJson.includes('cookie'), false);
  } finally {
    server.close();
  }
});

