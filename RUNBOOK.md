# SkillSignal India — Operations Runbook (`RUNBOOK.md`)

This runbook covers day-2 operations for PostgreSQL, Apache Kafka 3.8.0 (KRaft mode), the transactional outbox relay (`outbox_events`), dead-letter topics (`*.DLT`), and session check-out summary emails (`session_summary_emails`).

---

## 1. Starting the Stack (`postgres + kafka + backend + frontend`)

```bash
# Start core services (PostgreSQL 16 + Apache Kafka 3.8.0 KRaft + Backend + Frontend)
docker compose -f docker-compose.staging.yml up -d

# Start local dev services including Mailpit (SMTP capture on :1025, Web UI on :8025)
docker compose --profile dev up -d
```

Check health:
```bash
curl -s http://localhost:8080/api/health
curl -s http://localhost:8080/actuator/health
```

---

## 2. Apache Kafka Topic Inspection

Kafka runs on the internal Docker network (`skillsignal_internal`) as `apache/kafka:3.8.0` in single-node KRaft mode (`process.roles=broker,controller`).

### List all topics
```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:9092 \
  --list
```

Expected topics:
- `application.applied` (3 partitions, 7-day retention)
- `session.checked-out` (3 partitions, 7-day retention)
- `session.summary-email.requested` (3 partitions, 7-day retention)
- `application.applied.DLT`
- `session.checked-out.DLT`
- `session.summary-email.requested.DLT`

### Describe a topic and consumer group lag
```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:9092 \
  --describe --topic application.applied

docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --describe --group skillsignal-session-summary
```

---

## 3. Inspecting & Replaying Dead-Letter Topics (`*.DLT`)

When a consumer fails 3 times with exponential backoff (1s -> 5s -> 25s), `DeadLetterPublishingRecoverer` routes the failed message to `<topic>.DLT` so it never blocks valid messages.

### Inspect messages on a `.DLT` topic
```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic application.applied.DLT \
  --from-beginning \
  --max-messages 10 \
  --property print.key=true \
  --property print.timestamp=true
```

### Replay corrected messages
1. Identify the root cause from the `.DLT` payload and backend logs (note: logs never print personal data, resume text, or tokens).
2. If the event originated from `outbox_events`, reset `published_at = NULL` for that `event_id` in PostgreSQL so the outbox relay republishes it cleanly:
   ```sql
   UPDATE outbox_events
   SET published_at = NULL, attempts = 0, last_error = NULL
   WHERE event_id = '<uuid>';
   ```

---

## 4. What To Do When Kafka Is Down

1. **User Impact**: **None** for job search, resume upload, or applying to jobs. Every qualifying application (`application.applied`) and session check-out (`session.checked-out`) is written to the PostgreSQL `outbox_events` table inside the same database transaction.
2. **Monitoring**: Check pending outbox depth:
   ```sql
   SELECT topic, COUNT(*) AS pending_count, MAX(attempts) AS max_attempts, MAX(last_error) AS latest_error
   FROM outbox_events
   WHERE published_at IS NULL
   GROUP BY topic;
   ```
3. **Recovery**: Restart or recover the `kafka` container (`docker compose restart kafka`). Within 3 seconds (`SESSION_OUTBOX_RELAY_MS=3000`), `SessionCheckoutEmailService.relayPendingEvents()` automatically drains all unpublished `outbox_events` rows in creation order (`FOR UPDATE`), sets `published_at`, and triggers any pending session check-out summary emails once.

---

## 5. Resending a Failed Session Summary Email

If an email provider outage or daily rate limit caused a session summary email to be marked `FAILED` (or if a user re-enabled consent after a session was skipped):

```bash
curl -X POST "http://localhost:8080/api/sessions/<SESSION_UUID>/resend-summary" \
  -H "Authorization: Bearer <USER_JWT_TOKEN>"
```

Because `session_summary_emails` enforces `UNIQUE (session_id)`, calling `resend-summary` is strictly idempotent and will never send a duplicate email for a session that already succeeded (`status = 'SENT'`).

