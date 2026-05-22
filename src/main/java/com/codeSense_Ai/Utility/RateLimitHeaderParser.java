package com.codeSense_Ai.Utility;

import com.codeSense_Ai.Dto.RatelLimitStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
@Slf4j
public class RateLimitHeaderParser {
    private static final String HEADER_LIMIT     = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RESET     = "X-RateLimit-Reset";

    public RatelLimitStatus parse(HttpHeaders headers){
        String limit = headers.getFirst(HEADER_LIMIT);
        String remaining = headers.getFirst(HEADER_REMAINING);
        String resetAt =headers.getFirst(HEADER_RESET);

        if(limit == null || remaining == null || resetAt == null){
            log.debug("Rate limit headers are not present in the url");
            return null;
        }

        try {
            int limits = Integer.parseInt(limit);
            int remain = Integer.parseInt(remaining);
            Instant reset = Instant.ofEpochSecond(Long.parseLong(resetAt));

            return new RatelLimitStatus(limits , remain , reset);
        }catch (NumberFormatException e){
            log.warn("Malformede headers of limit {} , remaining {} , resetAt {}" , limit , remaining , resetAt);
            return null;
        }
    }
}
