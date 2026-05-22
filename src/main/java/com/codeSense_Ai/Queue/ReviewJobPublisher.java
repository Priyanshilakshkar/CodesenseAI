package com.codeSense_Ai.Queue;

import com.codeSense_Ai.model.ReviewJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewJobPublisher {

    private final ReviewJobConsumer consumer;

    public void publish(ReviewJob job){
        log.info("Publishing review job: {}", job);
        consumer.consume(job);
    }

    public void publishWithDelay(ReviewJob job , Duration dalay){
        Instant processAfter = Instant.now().plus(dalay);
        ReviewJob job1 = job.asRetry(processAfter);


        log.info("Publishing delayed review job: {} — processAfter={}",
                job1, processAfter);

        consumer.consume(job);
    }

    public void publishWithRetry(ReviewJob job , Duration delay){
        if(job.hasExceededRetries(3)){
            log.error("Job {} exceeded max retries — sending to dead letter: {}",
                    job.getJobId(), job);
            handleDeadLetter(job);
            return;
        }
        publishWithDelay(job, delay);
    }

    public void handleDeadLetter(ReviewJob job){
        log.error("DEAD LETTER: Review job permanently failed after 3 attempts: {}", job);
    }
}
