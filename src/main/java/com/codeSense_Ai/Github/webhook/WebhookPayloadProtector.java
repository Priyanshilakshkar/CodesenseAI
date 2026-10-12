package com.codeSense_Ai.Github.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class WebhookPayloadProtector {

    private final RedisTemplate<String , Object> redisTemplate ;
    private static final String REDIS_PREFIX = "webhook:delivery";
    private static final Duration REPLAY_WINDOW = Duration.ofHours(1);

    public boolean payloadProtector(String deliveryId){
        String key = REDIS_PREFIX + deliveryId ;
        boolean set = redisTemplate.opsForValue().setIfAbsent(key , "1" ,REPLAY_WINDOW);

        return Boolean.FALSE.equals(set);
    }
}
