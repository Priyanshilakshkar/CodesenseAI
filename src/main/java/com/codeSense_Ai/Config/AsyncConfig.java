package com.codeSense_Ai.Config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Executable;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Bean(name = "reviewProcessingExecutor")
    public Executor reviewProcessingExecutor(){

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);

        executor.setMaxPoolSize(16);

        executor.setQueueCapacity(500);

        executor.setKeepAliveSeconds(60);

        executor.setThreadNamePrefix("review-");

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.setAwaitTerminationSeconds(30);

        executor.setTaskDecorator(runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }

                    runnable.run();

                } finally {
                    MDC.clear();
                }
            };
        });


        executor.initialize();
        log.info("Review processing executor initialized: core={} max={} queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;



    }
}
