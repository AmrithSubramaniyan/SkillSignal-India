# SkillSignal India — Apache Kafka Event Schemas (`docs/events.md`)

## 1. Overview & Architecture

Every login starts a **Session Check-in** (`user_sessions`) and every explicit logout, browser tab close (`navigator.sendBeacon`), or 15-minute idle timeout triggers a **Session Check-out**.

To guarantee **zero lost events** even when Apache Kafka is temporarily unreachable:
1. Application status transitions (`APPLIED`, `SENT`, `SUBMITTED_CONFIRMED`) and session check-outs write an event row to `outbox_events` **in the same PostgreSQL database transaction**.
2. The `OutboxRelayScheduler` locks unpublished rows (`FOR UPDATE SKIP LOCKED`) and publishes them to Apache Kafka (`apache/kafka:3.8.0` in KRaft mode), keyed by `userId.toString()` so all events for a user stay strictly ordered within the same partition.
3. Consumers in group `skillsignal-session-summary` process events idempotently (`event_id UNIQUE`, `application_id UNIQUE`, `session_id UNIQUE`), with 3-attempt exponential backoff and automatic dead-letter routing (`*.DLT`) for poison messages.

---

## 2. Topics & Partitioning

All topics are created on startup via `NewTopic` beans in [`KafkaTopicsConfig.java`](file:///e:/Project/backend/src/main/java/in/skillsignal/session/KafkaTopicsConfig.java) with **3 partitions** and **replication factor 1** (local/single-broker KRaft):

| Topic Name | Dead-Letter Topic (`.DLT`) | Key | Purpose |
| :--- | :--- | :--- | :--- |
| `application.applied` | `application.applied.DLT` | `userId` (`UUID` string) | Emitted whenever an application transitions to a qualifying applied/sent state (`APPLIED`, `SENT`, `SUBMITTED_CONFIRMED`). |
| `session.checked-out` | `session.checked-out.DLT` | `userId` (`UUID` string) | Emitted when a user session closes (`LOGOUT`, `BROWSER_CLOSE`, `IDLE_TIMEOUT`, `TOKEN_EXPIRED`). |
| `session.summary-email.requested` | `session.summary-email.requested.DLT` | `userId` (`UUID` string) | Emitted after the checkout grace period when `>= 1` qualifying applications were recorded and the user has not opted out. |

---

## 3. Common Event Envelope (`schemaVersion: 1`)

Every Kafka message value is a UTF-8 JSON object conforming to [`SkillSignalEventEnvelope`](file:///e:/Project/backend/src/main/java/in/skillsignal/session/SkillSignalEventEnvelope.java):

```json
{
  "schemaVersion": 1,
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "eventType": "application.applied",
  "occurredAt": "2026-10-31T11:45:00Z",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionId": "11223344-5566-7788-99aa-bbccddeeff00",
  "payload": {}
}
```

### Privacy & Security Invariant
- **Never** include resume text, screening answers, salary data, recruiter contact details, passwords, or OAuth/JWT/beacon tokens in `payload`.

---

## 4. Event Payload Schemas

### 4.1 `application.applied`
Emitted in the same transaction when an application transitions to:
- `APPLIED` (`USER_CONFIRMED`, method `"Applied on company page"`)
- `SENT` (`UNCONFIRMED`, method `"Sent by email"`)
- `SUBMITTED_CONFIRMED` (`EXTERNALLY_CONFIRMED`, method `"Submitted by agent"`)

Non-qualifying statuses (`NEEDS_VERIFICATION`, `FAILED`, `SUBMISSION_FAILED`, `INTERESTED`, `SAVED`, `READY_TO_APPLY`, `AWAITING_APPROVAL`, drafts) **never** emit `application.applied`.

```json
{
  "schemaVersion": 1,
  "eventId": "6d7e8f90-1234-4567-89ab-cdef01234567",
  "eventType": "application.applied",
  "occurredAt": "2026-10-31T11:45:12Z",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionId": "11223344-5566-7788-99aa-bbccddeeff00",
  "payload": {
    "applicationId": "99887766-5544-3322-1100-aabbccddeeff",
    "company": "Razorpay",
    "jobTitle": "Senior Backend Engineer",
    "appliedAt": "2026-10-31T11:45:12Z",
    "method": "Applied on company page",
    "confirmationType": "USER_CONFIRMED",
    "sessionId": "11223344-5566-7788-99aa-bbccddeeff00"
  }
}
```
*(Note: If the application was completed by the background apply-agent or a scheduled rule while no user session was open, `sessionId` is `null` and the item is included in the user's next session check-out summary labelled `"while you were away"`.)*

### 4.2 `session.checked-out`
Emitted in the same transaction when `user_sessions.checked_out_at` is set:

```json
{
  "schemaVersion": 1,
  "eventId": "7a8b9c0d-1122-3344-5566-778899aabbcc",
  "eventType": "session.checked-out",
  "occurredAt": "2026-10-31T12:15:00Z",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionId": "11223344-5566-7788-99aa-bbccddeeff00",
  "payload": {
    "sessionId": "11223344-5566-7788-99aa-bbccddeeff00",
    "checkedInAt": "2026-10-31T11:30:00Z",
    "checkedOutAt": "2026-10-31T12:15:00Z",
    "checkoutReason": "LOGOUT",
    "timeZone": "Asia/Kolkata"
  }
}
```

### 4.3 `session.summary-email.requested`
Emitted by the `session.checked-out` consumer after the configurable grace period (`app.session.checkout-grace-ms`, default `30000` ms) when `>= 1` qualifying applications exist for the session and the user has email consent and `session_summary_email_enabled = true`:

```json
{
  "schemaVersion": 1,
  "eventId": "3c4d5e6f-7788-99aa-bbcc-ddeeff001122",
  "eventType": "session.summary-email.requested",
  "occurredAt": "2026-10-31T12:15:30Z",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionId": "11223344-5566-7788-99aa-bbccddeeff00",
  "payload": {
    "sessionId": "11223344-5566-7788-99aa-bbccddeeff00",
    "checkedInAt": "2026-10-31T11:30:00Z",
    "checkedOutAt": "2026-10-31T12:15:00Z",
    "checkoutReason": "LOGOUT",
    "timeZone": "Asia/Kolkata",
    "totalCompaniesApplied": 1
  }
}
```

