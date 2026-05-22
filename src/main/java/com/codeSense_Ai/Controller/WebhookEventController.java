package com.codeSense_Ai.Controller;

import com.codeSense_Ai.Exceptions.WebhookValidationException;
import com.codeSense_Ai.Github.webhook.WebhookPayloadProtector;
import com.codeSense_Ai.Github.webhook.WebhookSignatureValidator;
import com.codeSense_Ai.Service.WebhookEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/api/v1/webhooks")
public class WebhookEventController {
    private final WebhookEventService webhookEventService;

    private final WebhookSignatureValidator webhookSignatureValidator ;

    private final WebhookPayloadProtector webhookPayloadProtector ;

    public WebhookEventController(WebhookEventService webhookEventService, WebhookSignatureValidator webhookSignatureValidator, WebhookPayloadProtector webhookPayloadProtector) {
        this.webhookEventService = webhookEventService;
        this.webhookSignatureValidator = webhookSignatureValidator;
        this.webhookPayloadProtector = webhookPayloadProtector;
    }

    @GetMapping("/github")
    public ResponseEntity<Void> handleGihubEvent(
            @RequestHeader("X-Hub-Signature-256")  String signature,
            @RequestHeader("X_Github_Event")  String eventType,
            @RequestHeader("X_Github_Delivery") String deliveryId,
            @RequestBody String payload
    ){
        if(webhookPayloadProtector.payloadProtector(deliveryId)){
            log.warn("Replay detected for delivery: {}", deliveryId);
            return ResponseEntity.ok().build();
        }

        String repoName = extractRepoName(payload);
        String webhookSecret = webhookEventService.getWebhookSecret(repoName);

        if(!webhookSignatureValidator.validate(payload , signature , webhookSecret)){
            log.warn("HMAC validation failed for delivery : {} , repo: {}" , deliveryId , repoName);
            throw new WebhookValidationException("Validation failed");
        }

        webhookEventService.saveWebhook( deliveryId , payload , payload);

        return ResponseEntity.accepted().build();




    }
    private String extractRepoName(String rawPayload) {

        try {

            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(rawPayload);

            return root
                    .get("repository")
                    .get("full_name")
                    .asText();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to extract repository name"
            );
        }
    }
}
