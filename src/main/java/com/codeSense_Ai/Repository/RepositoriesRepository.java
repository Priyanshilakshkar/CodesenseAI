package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.Repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositoriesRepository extends JpaRepository<Repositories , Long> {
    Optional<Repositories> findByFullName(String fullName);

    Optional<Repositories> findByOwnerAndName(
            String owner,
            String name
    );

    Optional<Repositories> findByGithubRepoId(Long githubRepoId);

    @Query("SELECT r FROM Repositories r WHERE r.installation_id = :installationId")
    List<Repositories> findAllByInstallationId(
            @Param("installationId") Long installationId
    );

}
