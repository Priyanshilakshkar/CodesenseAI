package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.AIRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiRequestLogRepository extends JpaRepository<AIRequestLog , Long> {
    @Query("""
    SELECT SUM(a.promptTokens), SUM(a.completionTokens)
    FROM AIRequestLog a
    WHERE a.createdAt >= :start AND a.createdAt < :end
    """)
    List<Object[]> getTokenBreakdownForDateRange(
            @Param("start") Instant start,
            @Param("end")   Instant end
    );
}
