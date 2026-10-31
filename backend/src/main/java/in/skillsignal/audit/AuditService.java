package in.skillsignal.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID userId, String action, String entityType, String entityId, Map<String, Object> metadata) {
        if (userId == null) return;
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        try {
            entry.setMetadataJson(metadata != null ? objectMapper.writeValueAsString(metadata) : "{}");
        } catch (Exception e) {
            entry.setMetadataJson("{}");
        }
        auditLogRepository.save(entry);
    }
}
