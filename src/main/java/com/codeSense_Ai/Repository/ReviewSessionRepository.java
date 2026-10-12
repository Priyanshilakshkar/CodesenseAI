package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.ReviewSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession , Long> {
    @Query("""
    SELECT 
        s.status,
        s.filesReviewed,
        s.filesCached,
        s.commentsPosted,
        s.aiTokensUsed,
        s.processingTimeMs,
        s.repository.id
    FROM ReviewSession s
    WHERE s.createdAt >= :start
    AND s.createdAt < :end
""")
    List<Object[]> getStatsForDateRange(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
    SELECT 
        r.owner,
        r.name,
        COUNT(s.id),
        COALESCE(SUM(s.totalIssuesFound), 0)
    FROM ReviewSession s
    JOIN s.repository r
    GROUP BY r.id, r.owner, r.name
    ORDER BY COALESCE(SUM(s.totalIssuesFound), 0) DESC
""")
    List<Object[]> getTopReposByIssueCount(Pageable pageable);

    List<ReviewSession> findTop50ByOrderByCreatedAtDesc();
}
