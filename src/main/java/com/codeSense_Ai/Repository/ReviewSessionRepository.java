package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.ReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession , Long> {
    @Query("""
    SELECT s.status, s.filesReviewed, s.filesCached, s.commentsPosted,
           s.tokensUsed,
           TIMESTAMPDIFF(MILLISECOND, s.createdAt, s.completedAt) as latencyMs,
           s.pullRequest.repository.id
    FROM ReviewSession s
    WHERE s.createdAt >= :start AND s.createdAt < :end
    """)
    List<Object[]> getStatsForDateRange(
            @Param("start") Instant start,
            @Param("end")   Instant end
    );
}
