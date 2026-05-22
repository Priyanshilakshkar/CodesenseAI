package com.codeSense_Ai.Auth;

import com.codeSense_Ai.Dto.GitHubInstallationTokenResponse;
import com.codeSense_Ai.Dto.GithubAppProperties;
import com.codeSense_Ai.Exceptions.GithubAuthException;
import com.codeSense_Ai.Utility.GithubJwtGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
@Slf4j
public class GithubAppAuthenticator {

    private final GithubJwtGenerator githubJwtGenerator;
    private final GithubAppProperties githubAppProperties;
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient ;
    private final ObjectMapper objectMapper;

    public GithubAppAuthenticator(GithubJwtGenerator githubJwtGenerator, GithubAppProperties githubAppProperties, StringRedisTemplate redisTemplate, RestClient restClient, ObjectMapper objectMapper) {
        this.githubJwtGenerator = githubJwtGenerator;
        this.githubAppProperties = githubAppProperties;
        this.redisTemplate = redisTemplate;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private final String CACHE_PREFIX = "github:token:";

    public String generateIntallationToken(Long installationId){
        String cacheKey = CACHE_PREFIX + installationId ;

        String redis = redisTemplate.opsForValue().get(cacheKey);
        if(redis != null){
            log.debug("Using cached GitHub installation token for installation {}", installationId);
            return redis;
        }
        log.info("Fetching new GitHub installation token for installation {}", installationId);
        String token = fetchInstallationToken(installationId);
        redisTemplate.opsForValue().set(cacheKey , token , Duration.ofMinutes(55));

        return token;
    }

    public String fetchInstallationToken(Long installationId){
        String jwt  = githubJwtGenerator.generateJwt();

        try{
            GitHubInstallationTokenResponse response = restClient.post()
                    .uri("/app/installations/{id}/access_tokens", installationId)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(GitHubInstallationTokenResponse.class);

            if (response == null || response.token() == null) {
                throw new GithubAuthException("GitHub returned empty token response");
            }

            log.info("Got installation token, expires at {}", response.expiresAt());
            return response.token();
        }catch (HttpClientErrorException e){
            log.error("GitHub auth failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GithubAuthException(
                    "Failed to get installation token: " + e.getStatusCode()
            );
        }
    }
    public void evictToken(Long installationId) {
        redisTemplate.delete(CACHE_PREFIX+ installationId);
        log.info("Evicted cached token for installation {}", installationId);
    }
}
