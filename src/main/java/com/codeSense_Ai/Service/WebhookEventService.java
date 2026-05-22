package com.codeSense_Ai.Service;

import com.codeSense_Ai.Enties.WebhookEvent;
import com.codeSense_Ai.Repository.RepositoriesRepository;
import com.codeSense_Ai.Repository.WebhookEventRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;

    private final RepositoriesRepository repositoriesRepository;


    public WebhookEventService(WebhookEventRepository webhookEventRepository, RepositoriesRepository repositoriesRepository) {
        this.webhookEventRepository = webhookEventRepository;
        this.repositoriesRepository = repositoriesRepository;
    }



    public boolean saveWebhook(
            String gihubDeliveryId,
            String eventType,
            String payload

    ){
      try {
          WebhookEvent event = WebhookEvent.builder()
                  .githubDeliveryId(gihubDeliveryId)
                  .eventType(eventType)
                  .payloadJson(payload)
                  .build();

          webhookEventRepository.save(event);
          return true;
      } catch (DataIntegrityViolationException e){
          return false;
      }
    }

    @Transactional
    public void markAsProcessed(String githubDeliveryId) {

        WebhookEvent webhookEvent = webhookEventRepository
                .findByGithubDeliveryId(githubDeliveryId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Webhook event not found: " + githubDeliveryId
                        )
                );

        webhookEvent.setProcessed(true);
        webhookEvent.setProcessedAt(LocalDateTime.now());

        webhookEventRepository.save(webhookEvent);
    }

    public void incrementProcessingAttempts(Long webhookEventId) {

        WebhookEvent webhookEvent = webhookEventRepository
                .findById(webhookEventId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Webhook event not found: " + webhookEventId
                        )
                );

        webhookEvent.setProcessingAttempts(
                webhookEvent.getProcessingAttempts() + 1
        );

        webhookEventRepository.save(webhookEvent);
    }

    public boolean existsByDeliveryId(String deliveryId) {

        return webhookEventRepository
                .existsByGithubDeliveryId(deliveryId);
    }

    public String getWebhookSecret(String repoName){
        return repositoriesRepository.findByFullName(repoName)
                .orElseThrow(()->new RuntimeException("No repository found")).getWebhookSecret();
    }

}
