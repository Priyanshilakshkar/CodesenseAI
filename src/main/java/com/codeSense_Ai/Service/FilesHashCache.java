package com.codeSense_Ai.Service;

import com.codeSense_Ai.Dto.CachedReviewResult;
import com.codeSense_Ai.Enties.FileReviewCache;
import com.codeSense_Ai.Repository.CacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilesHashCache {
    private final ObjectMapper objectMapper ;
    private final StringRedisTemplate stringRedisTemplate ;
    private final CacheRepository cacheRepository;

    private static final String KEY_PREFIX =  "filecache:";

    @Value("${codesense.review.file-hash-cache-ttl-hours:24}")
    private int cacheTtlHours;

    public Optional<CachedReviewResult> get(String blobSha){
        Optional<CachedReviewResult> redisResult = getFromRedis(blobSha);
        if (redisResult.isPresent()) {
            log.debug("Redis cache hit for blob sha {}", abbreviated(blobSha));
            return redisResult;
        }

        Optional<CachedReviewResult> dbResult = getFromDatabase(blobSha);
        if (dbResult.isPresent()) {
            log.debug("DB cache hit for blob sha {} — backfilling Redis", abbreviated(blobSha));


            putInRedis(blobSha, dbResult.get());
            return dbResult;
        }

        log.debug("Cache miss for blob sha {}", abbreviated(blobSha));
        return Optional.empty();
    }

    public boolean contains(String blobSha) {

        Boolean existsInRedis = stringRedisTemplate.hasKey(KEY_PREFIX + blobSha);
        if (Boolean.TRUE.equals(existsInRedis)) {
            return true;
        }
        // Fall back to DB check
        return cacheRepository.findByFileHash(blobSha).isPresent();
    }
    public void evict(String blobSha) {
        stringRedisTemplate.delete(KEY_PREFIX + blobSha);
        cacheRepository.findByFileHash(blobSha)
                .ifPresent(cacheRepository::delete);
        log.info("Evicted cache entry for sha {}", abbreviated(blobSha));
    }

    /**
     * Scheduled cleanup — remove DB entries older than TTL.
     * Called by CacheCleanupScheduler every night at 2am.
     * Redis handles its own TTL expiry automatically.
     */
    public void deleteExpiredEntries() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(cacheTtlHours));
        cacheRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up file review cache entries older than {}h", cacheTtlHours);

    }

    public Optional<CachedReviewResult> getFromRedis(String blobSha){
        String key = KEY_PREFIX + blobSha ;
        try{
            String json = stringRedisTemplate.opsForValue().get(key);
            if(json == null){
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(json , CachedReviewResult.class));
        }catch (Exception e){
            log.warn("Redis read failed for sha {} — treating as cache miss: {}" ,
                    abbreviated(blobSha) , e.getMessage());
            return Optional.empty();
        }
    }
    public  void putInRedis(String blobSha , CachedReviewResult cachedReviewResult){
        try {
            String json = objectMapper.writeValueAsString(cachedReviewResult);
            stringRedisTemplate.opsForValue().set(
                    KEY_PREFIX + blobSha,
                    json,
                    Duration.ofHours(cacheTtlHours)
            );
        } catch (Exception e) {
            // Redis write failure is non-fatal — DB is the durable layer
            log.warn("Redis write failed for sha {} — DB cache still intact: {}",
                    abbreviated(blobSha), e.getMessage());
        }


    }

    private void putInDatabase(String blobSha, String filename, CachedReviewResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);


            FileReviewCache entity = cacheRepository.findByFileHash(blobSha)
                    .orElse(FileReviewCache.builder()
                            .fileHash(blobSha)
                            .filename(filename)
                            .build());

            entity.setReviewResultJson(json);
            entity.setModelUsed(result.modelUsed());
            entity.setTokensUsed(result.tokensUsed());

            cacheRepository.save(entity);

        } catch (Exception e) {
            log.error("Failed to persist cache entry for sha {}: {}",
                    abbreviated(blobSha), e.getMessage());
        }
    }
    public Optional<CachedReviewResult> getFromDatabase(String blobSha){
        return cacheRepository.findByFileHash(blobSha)
                .map(entity -> {
                    entity.setLastHitAt(Instant.now());
                    entity.setHitCount(entity.getHitCount()+1);
                    cacheRepository.save(entity);

                    return deserializeResult(entity.getReviewResultJson());
                })
                .flatMap(result -> result);

    }
    private Optional<CachedReviewResult> deserializeResult(String json){
        try{
            return Optional.of(objectMapper.readValue(json , CachedReviewResult.class));
        }catch (Exception e){
            log.warn("Failed to deserialize cached result — treating as cache miss: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }
    private String abbreviated(String sha) {
        return sha != null && sha.length() > 8 ? sha.substring(0, 8) + "..." : sha;
    }
}
