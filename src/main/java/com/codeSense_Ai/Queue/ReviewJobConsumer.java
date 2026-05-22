package com.codeSense_Ai.Queue;

import com.codeSense_Ai.Exceptions.RateLimitRetryException;
import com.codeSense_Ai.Exceptions.RetryableException;
import com.codeSense_Ai.model.ReviewJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewJobConsumer {
    private final ReviewJobPublisher publisher ;
    private final ReviewOrchestrationService orchestrationService ;

    @Async("reviewProcessingExecutor")
    public void consume(ReviewJob job){
        MDC.put("jobId" , job.getJobId());
        MDC.put("repo" , job.getOwner() + "/" + job.getRepoName());
        MDC.put("pr" , "#" + job.getOwner());
        MDC.put("attempt" , String.valueOf(job.getAttemptNumber()));

        try {
            if(job.isDelayed()){
                long sleepMs = Duration.between(Instant.now() , job.getProcessAfter()).toMillis();
                if(sleepMs>0){
                    log.info("Job is delayed — sleeping {}ms before processing", sleepMs);
                    Thread.sleep(sleepMs);
                }

                Instant startTime = Instant.now();

                orchestrationService.processReview(job);
                long elapsedMs = Duration.between(startTime, Instant.now()).toMillis();
                log.info("Review job completed in {}ms: {}", elapsedMs, job);

            }
        }catch (RateLimitRetryException e) {
            // GitHub rate limited — re-queue with delay
            log.warn("Rate limited — retrying job {} in {}s",
                    job.getJobId(), e.getRetryAfterSeconds());
            publisher.publishWithRetry(job,
                    Duration.ofSeconds(e.getRetryAfterSeconds()));

        } catch (RetryableException e) {
            // Transient error — retry with exponential backoff
            long backoffSeconds = (long) Math.pow(2, job.getAttemptNumber()) * 30L;
            log.warn("Transient error on job {} (attempt {}) — retrying in {}s: {}",
                    job.getJobId(), job.getAttemptNumber(),
                    backoffSeconds, e.getMessage());
            publisher.publishWithRetry(job, Duration.ofSeconds(backoffSeconds));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Job interrupted: {}", job.getJobId());

        } catch (Exception e) {
            // Unexpected error — log and dead-letter
            log.error("Unexpected error processing job {}: {}",
                    job.getJobId(), e.getMessage(), e);
            publisher.publishWithRetry(job, Duration.ofMinutes(5));

        } finally {
            MDC.clear();
        }

    }
}
