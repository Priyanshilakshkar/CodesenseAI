package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.PullRequests;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PullRequestRepository extends JpaRepository<PullRequests , Long> {
    Optional<PullRequests> findByRepositoryIdAndGithubPrNumber(Long repositoryId , Integer prNumber);

}
