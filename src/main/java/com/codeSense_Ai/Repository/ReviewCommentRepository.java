package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment , Long> {
    @Query("""
    SELECT c.category, COUNT(c)
    FROM ReviewComment c
    WHERE c.reviewSession.createdAt >= :start
      AND c.reviewSession.createdAt < :end
    GROUP BY c.category
    """)
    List<Object[]> getCategoryBreakdownForDateRange(
            @Param("start") Instant start,
            @Param("end")   Instant end
    );
}
