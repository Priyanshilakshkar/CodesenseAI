package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog , Long> {
}
