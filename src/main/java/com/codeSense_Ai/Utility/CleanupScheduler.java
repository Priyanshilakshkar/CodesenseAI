package com.codeSense_Ai.Utility;

import com.codeSense_Ai.Service.FilesHashCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CleanupScheduler {
    private final FilesHashCache fileHashCache;

    // Every night at 2am — clean up expired DB entries
    // Redis handles its own TTL; only MySQL needs manual cleanup
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanExpiredCacheEntries() {
        log.info("Starting nightly file review cache cleanup");
        fileHashCache.deleteExpiredEntries();
    }
}
