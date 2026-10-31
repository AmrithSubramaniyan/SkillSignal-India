package in.skillsignal.session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical Kafka & Outbox event envelope (schemaVersion = 1).
 * Message key in Kafka is always {@code userId.toString()} so events of one user stay ordered.
 * Golden Rule: Never include resume text, screening answers, salary data, recruiter contacts, or tokens.
 */
public record SkillSignalEventEnvelope(
        int schemaVersion,
        UUID eventId,
        String eventType,
        String occurredAt,
        UUID userId,
        UUID sessionId,
        Map<String, Object> payload
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static SkillSignalEventEnvelope create(
            UUID eventId,
            String eventType,
            Instant occurredAt,
            UUID userId,
            UUID sessionId,
            Map<String, Object> payload
    ) {
        return new SkillSignalEventEnvelope(
                CURRENT_SCHEMA_VERSION,
                eventId != null ? eventId : UUID.randomUUID(),
                eventType,
                (occurredAt != null ? occurredAt : Instant.now()).toString(),
                userId,
                sessionId,
                payload != null ? payload : Map.of()
        );
    }
}
