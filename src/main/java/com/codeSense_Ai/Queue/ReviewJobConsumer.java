package com.codeSense_Ai.Queue;

import com.codeSense_Ai.Config.RabbitMQConfig;
import com.codeSense_Ai.Exceptions.RateLimitRetryException;
import com.codeSense_Ai.Exceptions.RetryableException;
import com.codeSense_Ai.Service.ReviewOrchestrationService;
import com.codeSense_Ai.model.ReviewJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewJobConsumer {
    private final ReviewJobPublisher publisher;

    private final ReviewOrchestrationService orchestrationService;

    @RabbitListener(queues = RabbitMQConfig.REVIEW_QUEUE)
    public void consume(ReviewJob job) {

        MDC.put("jobId", job.getJobId());

        MDC.put(
                "repo",
                job.getOwner() + "/" + job.getRepoName()
        );

        MDC.put(
                "pr",
                "#" + job.getPrNumber()
        );

        MDC.put(
                "attempt",
                String.valueOf(job.getAttemptNumber())
        );

        try {

            if (job.isDelayed()) {

                long sleepMs = Duration
                        .between(Instant.now(), job.getProcessAfter())
                        .toMillis();

                if (sleepMs > 0) {

                    log.info(
                            "Job delayed — sleeping {}ms",
                            sleepMs
                    );

                    Thread.sleep(sleepMs);
                }
            }

            Instant startTime = Instant.now();

            orchestrationService.processReview(job);

            long elapsedMs = Duration
                    .between(startTime, Instant.now())
                    .toMillis();

            log.info(
                    "Review completed in {}ms: {}",
                    elapsedMs,
                    job
            );

        } catch (RateLimitRetryException e) {

            log.warn(
                    "Rate limited — retrying in {}s",
                    e.getRetryAfterSeconds()
            );

            publisher.publishWithRetry(
                    job,
                    Duration.ofSeconds(
                            e.getRetryAfterSeconds()
                    )
            );

        } catch (RetryableException e) {

            long backoffSeconds =
                    (long) Math.pow(2, job.getAttemptNumber()) * 30L;

            log.warn(
                    "Transient error — retrying in {}s",
                    backoffSeconds
            );

            publisher.publishWithRetry(
                    job,
                    Duration.ofSeconds(backoffSeconds)
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.error(
                    "Job interrupted: {}",
                    job.getJobId()
            );

        } catch (Exception e) {

            log.error(
                    "Unexpected error processing {}",
                    job.getJobId(),
                    e
            );

            publisher.publishWithRetry(
                    job,
                    Duration.ofMinutes(5)
            );

        } finally {

            MDC.clear();
        }
    }
}
