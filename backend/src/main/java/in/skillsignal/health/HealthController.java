package in.skillsignal.health;

import in.skillsignal.session.KafkaTopicsConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTopicsConfig kafkaTopicsConfig;

    public HealthController(JdbcTemplate jdbcTemplate, KafkaTopicsConfig kafkaTopicsConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTopicsConfig = kafkaTopicsConfig;
    }

    @GetMapping
    public Map<String, Object> checkHealth() {
        Timestamp ts = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        String dbTime = ts != null ? ts.toInstant().toString() : Instant.now().toString();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", "UP");
        res.put("service", "SkillSignal India Backend");
        res.put("dbTime", dbTime);
        res.put("kafka", Map.of(
                "status", kafkaTopicsConfig.isKafkaDownSimulated() ? "DEGRADED_OUTBOX_BUFFERING" : "UP",
                "bootstrapServers", kafkaTopicsConfig.getBootstrapServers(),
                "consumerGroup", "skillsignal-session-summary",
                "topics", kafkaTopicsConfig.getConfiguredTopics()
        ));
        return res;
    }
}
