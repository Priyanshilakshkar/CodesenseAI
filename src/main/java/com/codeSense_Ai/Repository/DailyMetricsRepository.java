package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.DailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyMetricsRepository extends JpaRepository<DailyMetrics , Long> {
    Optional<DailyMetrics> findByMetricDate(
            LocalDate metricDate
    );


    List<DailyMetrics> findAllByMetricDateBetween(
            LocalDate start,
            LocalDate end
    );
}
