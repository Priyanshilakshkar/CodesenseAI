package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.FileReviewCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface CacheRepository extends JpaRepository<FileReviewCache , Long> {
    Optional<FileReviewCache> findByFileHash(String blobSha);

    void deleteByCreatedAtBefore(Instant cutoff);
}
