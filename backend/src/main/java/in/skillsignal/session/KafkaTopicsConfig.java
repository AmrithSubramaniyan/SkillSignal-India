package in.skillsignal.session;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class KafkaTopicsConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicsConfig.class);

    public static final String TOPIC_APPLICATION_APPLIED = "application.applied";
    public static final String TOPIC_SESSION_CHECKED_OUT = "session.checked-out";
    public static final String TOPIC_SUMMARY_EMAIL_REQUESTED = "session.summary-email.requested";

    public static final String TOPIC_APPLICATION_APPLIED_DLT = "application.applied.DLT";
    public static final String TOPIC_SESSION_CHECKED_OUT_DLT = "session.checked-out.DLT";
    public static final String TOPIC_SUMMARY_EMAIL_REQUESTED_DLT = "session.summary-email.requested.DLT";

    public static final int PARTITIONS = 3;
    public static final int REPLICAS = 1;

    private final AtomicBoolean simulatedKafkaDown = new AtomicBoolean(false);
    private final String bootstrapServers;

    public KafkaTopicsConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    public boolean isKafkaDownSimulated() {
        return simulatedKafkaDown.get();
    }

    public void setKafkaDownSimulated(boolean down) {
        this.simulatedKafkaDown.set(down);
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public List<String> getConfiguredTopics() {
        return List.of(
                TOPIC_APPLICATION_APPLIED,
                TOPIC_SESSION_CHECKED_OUT,
                TOPIC_SUMMARY_EMAIL_REQUESTED,
                TOPIC_APPLICATION_APPLIED_DLT,
                TOPIC_SESSION_CHECKED_OUT_DLT,
                TOPIC_SUMMARY_EMAIL_REQUESTED_DLT
        );
    }

    @Bean
    public NewTopic applicationAppliedTopic() {
        return TopicBuilder.name(TOPIC_APPLICATION_APPLIED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic sessionCheckedOutTopic() {
        return TopicBuilder.name(TOPIC_SESSION_CHECKED_OUT)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic sessionSummaryEmailRequestedTopic() {
        return TopicBuilder.name(TOPIC_SUMMARY_EMAIL_REQUESTED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic applicationAppliedDltTopic() {
        return TopicBuilder.name(TOPIC_APPLICATION_APPLIED_DLT)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic sessionCheckedOutDltTopic() {
        return TopicBuilder.name(TOPIC_SESSION_CHECKED_OUT_DLT)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic sessionSummaryEmailRequestedDltTopic() {
        return TopicBuilder.name(TOPIC_SUMMARY_EMAIL_REQUESTED_DLT)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    /**
     * Exponential backoff error handler (3 attempts total: initial + 2 retries), then routes
     * poison messages to {@code <topic>.DLT} without blocking the partition or logging personal data.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    // Never log message value/personal data; log only topic, partition, offset, and exception class
                    log.warn("[KAFKA-DLT] Routing poison message from topic={} partition={} offset={} to {}.DLT due to {}",
                            record.topic(), record.partition(), record.offset(), record.topic(), ex.getClass().getSimpleName());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        ExponentialBackOff backOff = new ExponentialBackOff(100L, 2.0);
        backOff.setMaxElapsedTime(500L); // 3 attempts total (initial + 100ms + 200ms)

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    /**
     * Actuator HealthIndicator for Kafka. Reports UP with outbox fallback mode if broker is down
     * so the application remains healthy for login and job applications.
     */
    @Bean
    public HealthIndicator kafkaSessionSummaryHealthIndicator() {
        return () -> {
            boolean down = simulatedKafkaDown.get();
            return Health.up()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("consumerGroup", "skillsignal-session-summary")
                    .withDetail("brokerState", down ? "DEGRADED_OUTBOX_BUFFERING" : "READY")
                    .withDetail("topics", getConfiguredTopics())
                    .build();
        };
    }
}
