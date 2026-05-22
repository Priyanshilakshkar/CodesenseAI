package com.codeSense_Ai.Security;

import com.codeSense_Ai.Enties.AuditLog;
import com.codeSense_Ai.Repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    @Async("reviewProcessingExecutor")
    public void log(Long actorId, String action,
                    String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorId(actorId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details);
            entry.setIpAddress(extractClientIp());
            entry.setCreatedAt(Instant.now());

            auditLogRepository.save(entry);
            log.debug("Audit: actor={} action={} entity={}/{} details={}",
                    actorId, action, entityType, entityId, details);

        } catch (Exception e) {
            // Never let audit logging failure affect the user
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    /**
     * Logs a security incident — failed auth, blocked SSRF, etc.
     */
    @Async("reviewProcessingExecutor")
    public void logSecurityIncident(String type, String description,
                                    String ipAddress) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorId(null); // unknown actor
            entry.setAction("SECURITY_INCIDENT");
            entry.setEntityType("System");
            entry.setEntityId(null);
            entry.setDetails(type + ": " + description);
            entry.setIpAddress(ipAddress);
            entry.setCreatedAt(Instant.now());

            auditLogRepository.save(entry);
            log.warn("Security incident: type={} ip={} description={}",
                    type, ipAddress, description);

        } catch (Exception e) {
            log.error("Failed to log security incident: {}", e.getMessage());
        }
    }

    // ── AOP-based automatic audit logging ────────────────────────────────────

    private String extractClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";

            String ip = attrs.getRequest().getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                return ip.split(",")[0].trim(); // first IP in chain
            }
            return attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
