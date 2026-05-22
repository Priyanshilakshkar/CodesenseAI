package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.RepoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepoConfigRepository extends JpaRepository<RepoConfig , Long> {
    Optional<RepoConfig> findByRepositoryId(Long repositoryId);
}
