package com.codeSense_Ai.Repository;

import com.codeSense_Ai.Enties.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent , Long> {

    boolean existsByGithubDeliveryId(String githubDeliveryId);

    Optional<WebhookEvent> findByGithubDeliveryId(String githubDeliveryId);

}
