package com.codeSense_Ai.Queue;

import com.codeSense_Ai.Config.RabbitMQConfig;
import com.codeSense_Ai.model.ReviewJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(ReviewJob job) {

        log.info("Publishing review job: {}", job);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.REVIEW_QUEUE,
                job
        );
    }

    public void publishWithDelay(ReviewJob job, Duration delay) {

        Instant processAfter = Instant.now().plus(delay);

        ReviewJob retryJob = job.asRetry(processAfter);

        log.info(
                "Publishing delayed review job: {} — processAfter={}",
                retryJob,
                processAfter
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.REVIEW_QUEUE,
                retryJob
        );
    }

    public void publishWithRetry(ReviewJob job, Duration delay) {

        if (job.hasExceededRetries(3)) {

            log.error(
                    "Job {} exceeded max retries — dead letter",
                    job.getJobId()
            );

            handleDeadLetter(job);

            return;
        }

        publishWithDelay(job, delay);
    }

    public void handleDeadLetter(ReviewJob job) {

        log.error(
                "DEAD LETTER: Review job permanently failed: {}",
                job
        );
    }
}
