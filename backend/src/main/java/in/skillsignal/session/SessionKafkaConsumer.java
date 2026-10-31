package in.skillsignal.session;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SessionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(SessionKafkaConsumer.class);

    public record DeadLetterRecordInfo(
            String topic,
            int partition,
            long offset,
            String key,
            Instant receivedAt
    ) {}

    private final SessionCheckoutEmailService sessionCheckoutEmailService;
    private final List<DeadLetterRecordInfo> dltRecords = new CopyOnWriteArrayList<>();

    public SessionKafkaConsumer(SessionCheckoutEmailService sessionCheckoutEmailService) {
        this.sessionCheckoutEmailService = sessionCheckoutEmailService;
    }

    @KafkaListener(
            id = "skillsignal-application-applied-listener",
            topics = KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED,
            groupId = "${spring.kafka.consumer.group-id:skillsignal-session-summary}"
    )
    public void onApplicationApplied(ConsumerRecord<String, String> record) {
        sessionCheckoutEmailService.handleApplicationAppliedMessage(record.value());
    }

    @KafkaListener(
            id = "skillsignal-session-checked-out-listener",
            topics = KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT,
            groupId = "${spring.kafka.consumer.group-id:skillsignal-session-summary}"
    )
    public void onSessionCheckedOut(ConsumerRecord<String, String> record) {
        sessionCheckoutEmailService.handleSessionCheckedOutMessage(record.value());
        sessionCheckoutEmailService.relayPendingEvents();
    }

    @KafkaListener(
            id = "skillsignal-summary-email-requested-listener",
            topics = KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id:skillsignal-session-summary}"
    )
    public void onSummaryEmailRequested(ConsumerRecord<String, String> record) {
        sessionCheckoutEmailService.handleSummaryEmailRequestedMessage(record.value());
    }

    @KafkaListener(
            id = "skillsignal-dlt-listener",
            topics = {
                    KafkaTopicsConfig.TOPIC_APPLICATION_APPLIED_DLT,
                    KafkaTopicsConfig.TOPIC_SESSION_CHECKED_OUT_DLT,
                    KafkaTopicsConfig.TOPIC_SUMMARY_EMAIL_REQUESTED_DLT
            },
            groupId = "${spring.kafka.consumer.group-id:skillsignal-session-summary}-dlt"
    )
    public void onDeadLetterMessage(ConsumerRecord<String, String> record) {
        // Log without personal data
        log.warn("[DLT-CONSUMER] Recorded dead-letter message on topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
        dltRecords.add(new DeadLetterRecordInfo(
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                Instant.now()
        ));
    }

    public List<DeadLetterRecordInfo> getDltRecords() {
        return List.copyOf(dltRecords);
    }

    public void clearDltRecords() {
        dltRecords.clear();
    }
}

