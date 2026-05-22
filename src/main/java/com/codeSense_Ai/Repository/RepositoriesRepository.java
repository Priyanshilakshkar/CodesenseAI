package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.Repositories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoriesRepository extends JpaRepository<Repositories , Long> {
    Optional<Repositories> findByFullName(String fullName);

    Optional<Repositories> findByOwnerAndName(
            String owner,
            String name
    );
}
