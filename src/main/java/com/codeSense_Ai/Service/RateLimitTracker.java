package com.codeSense_Ai.Service;

import com.codeSense_Ai.Dto.RatelLimitStatus;
import com.codeSense_Ai.Utility.RateLimitHeaderParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitTracker {

    private final StringRedisTemplate redisTemplate ;

    private  final RateLimitHeaderParser parser ;

    @Value("${codesense.github.rate-limit-threshold}")
    private int threshold;

    private static final String KEY_PREFIX = "rate:limit:";


    public void updateFromReponses(String owner , String repo , HttpHeaders headers){
        RatelLimitStatus status = parser.parse(headers);
        if(status == null){
            return;
        }

        String key = KEY_PREFIX + owner + ":" + repo;

        Duration ttl = Duration.ofSeconds(status.SecondsUntilResese() + 60);
        redisTemplate.opsForHash().put(key , "remaining" , String.valueOf(status.remaining()));
        redisTemplate.opsForHash().put(key , "limit" , String.valueOf(status.limit()));
        redisTemplate.opsForHash().put(key  , "resetAt" , String.valueOf(status.resetAt()));
        redisTemplate.expire(key , ttl);

        if(status.remaining() < 100){
            log.warn("Github rate limit low for {}/{} , {} remaining ({:.1f}%) , resets in {}"  ,
                    owner ,
                    repo,
                    status.remaining(),
                    status.percentageRemaining(),
                    status.SecondsUntilResese()

                    );
        }
        else {
            log.debug("Rate limit updated for {}/{}: {}/{} remaining",
                    owner, repo, status.remaining(), status.limit());
        }


    }
    public int getRemainingRequests(String owner  , String repo ){
           String key  = KEY_PREFIX + owner + ":" + repo ;
           String value = (String) redisTemplate.opsForHash().get(key , "remaining");

           if(value == null){
               return 999;
           }

           try {
               return Integer.parseInt(value);
           }catch (NumberFormatException e){
               return 999;
           }
    }

    public boolean isBelowThreshold(int remaining) {
        return remaining < threshold; // threshold = 10 from application.yml
    }

    /**
     * How many seconds until the rate limit resets.
     * Used by the job scheduler to know how long to delay a retry.
     */
    public long getSecondsUntilReset(String owner, String repo) {
        String key = KEY_PREFIX + owner + ":" + repo ;
        String value = (String) redisTemplate.opsForHash()
                .get(key, "reset");

        if (value == null) return 3600; // default: wait 1 hour if unknown

        try {
            Instant resetAt = Instant.ofEpochSecond(Long.parseLong(value));
            return new RatelLimitStatus(0, 0, resetAt).SecondsUntilResese();
        } catch (NumberFormatException e) {
            return 3600;
        }
    }
    public Optional<RatelLimitStatus> getCurrentStatus(String owner, String repo) {
        String key = KEY_PREFIX + owner + ":" + repo ;
        String remaining = (String) redisTemplate.opsForHash().get(key, "remaining");
        String reset     = (String) redisTemplate.opsForHash().get(key, "reset");
        String limit     = (String) redisTemplate.opsForHash().get(key, "limit");

        if (remaining == null || reset == null || limit == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new RatelLimitStatus(
                    Integer.parseInt(limit),
                    Integer.parseInt(remaining),
                    Instant.ofEpochSecond(Long.parseLong(reset))
            ));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }


}
