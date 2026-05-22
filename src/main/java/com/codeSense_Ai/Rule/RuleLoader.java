package com.codeSense_Ai.Rule;

import com.codeSense_Ai.Client.GithubApiClient;
import com.codeSense_Ai.Enties.RepoConfig;
import com.codeSense_Ai.Enties.Repositories;
import com.codeSense_Ai.Repository.RepoConfigRepository;
import com.codeSense_Ai.model.RepoReviewConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleLoader {
    private final GithubApiClient gitHubApiClient;
    private final RepoConfigRepository configRepository;
    private final RuleValidator        ruleValidator;
    private final ObjectMapper yamlMapper;   // configured with YAMLFactory
    private final StringRedisTemplate redisTemplate;

    // Cache config for 5 minutes — teams want changes to take effect quickly
    private static final Duration CACHE_TTL    = Duration.ofMinutes(5);
    private static final String   KEY_PREFIX   = "repoconfig:";
    private static final String   CONFIG_FILE  = ".codesense.yml";

    /**
     * Loads the effective config for a repository.
     *
     * Priority order (highest wins):
     *   1. .codesense.yml in the GitHub repo root
     *   2. DB-stored config (set via the dashboard UI)
     *   3. System defaults
     *
     * Result is cached in Redis for 5 minutes to avoid
     * hitting the GitHub API on every chunk review.
     */
    public RepoReviewConfig load(Repositories repo, String token) {
        String cacheKey = KEY_PREFIX + repo.getId();

        // ── Check Redis cache first ───────────────────────────────
        RepoReviewConfig cached = loadFromCache(cacheKey);
        if (cached != null) {
            log.debug("Config cache hit for repo {}/{}", repo.getOwner(), repo.getName());
            return cached;
        }

        // ── Load from sources ─────────────────────────────────────
        RepoReviewConfig config = loadFresh(repo, token);

        // ── Cache the result ──────────────────────────────────────
        saveToCache(cacheKey, config);

        return config;
    }

    /**
     * Force-reload config — bypasses Redis cache.
     * Called by RepoConfigService when a user updates settings via dashboard.
     */
    public RepoReviewConfig reload(Repositories repo, String token) {
        evictCache(repo.getId());
        return load(repo, token);
    }

    /**
     * Evicts the cached config for a repo.
     * Called when .codesense.yml is updated or DB config changes.
     */
    public void evictCache(Long repositoryId) {
        redisTemplate.delete(KEY_PREFIX + repositoryId);
        log.info("Evicted config cache for repository id={}", repositoryId);
    }

    // ── Private: load logic ───────────────────────────────────────────────────

    private RepoReviewConfig loadFresh(Repositories repo, String token) {
        // Start with system defaults
        RepoReviewConfig base = RepoReviewConfig.defaults();

        // Layer 2: DB config (if exists)
        Optional<RepoConfig> dbConfig =
                configRepository.findByRepositoryId(repo.getId());

        RepoReviewConfig dbLayer = dbConfig
                .map(this::mapEntityToConfig)
                .orElse(base);

        // Layer 1: .codesense.yml from repo root (highest priority)
        Optional<RepoReviewConfig> repoYaml =
                loadFromGitHub(repo, token);

        if (repoYaml.isPresent()) {
            RepoReviewConfig merged = RepoReviewConfig.merge(dbLayer, repoYaml.get());
            log.info("Loaded config for {}/{}: source=yaml+db merged",
                    repo.getOwner(), repo.getName());
            return merged;
        }

        log.info("Loaded config for {}/{}: source={}",
                repo.getOwner(), repo.getName(),
                dbConfig.isPresent() ? "db" : "defaults");
        return dbLayer;
    }

    private Optional<RepoReviewConfig> loadFromGitHub(Repositories repo, String token) {
        try {
            // Fetch .codesense.yml content from the default branch
            String yamlContent = gitHubApiClient.getFileContent(
                    repo.getOwner(), repo.getName(), CONFIG_FILE, token
            );

            if (yamlContent == null || yamlContent.isBlank()) {
                return Optional.empty();
            }

            // Parse YAML
            RepoReviewConfig parsed = parseYaml(yamlContent);

            // Validate — reject invalid configs with a helpful error
            RuleValidator.ValidationResult validation =
                    ruleValidator.validate(parsed);

            if (!validation.isValid()) {
                log.error(
                        "Invalid .codesense.yml for {}/{}: {} — using defaults instead",
                        repo.getOwner(), repo.getName(), validation.errorSummary()
                );
                // In Week 7 style: post a comment on the PR explaining the error
                // For now, just fall back to defaults
                return Optional.empty();
            }

            if (validation.hasWarnings()) {
                log.warn(".codesense.yml warnings for {}/{}: {}",
                        repo.getOwner(), repo.getName(), validation.warnings());
            }

            log.info("Loaded .codesense.yml for {}/{}: maxComments={} model={}",
                    repo.getOwner(), repo.getName(),
                    parsed.getMaxComments(), parsed.getModel());

            return Optional.of(parsed);

        } catch (FileNotFoundException e) {
            // .codesense.yml doesn't exist — perfectly normal
            log.debug("No .codesense.yml found for {}/{}", repo.getOwner(), repo.getName());
            return Optional.empty();

        } catch (Exception e) {
            // Parse error or API error — fall back gracefully
            log.warn("Failed to load .codesense.yml for {}/{}: {}",
                    repo.getOwner(), repo.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private RepoReviewConfig parseYaml(String yaml) throws Exception {
        // The YAML has a top-level "review:" key — unwrap it
        com.fasterxml.jackson.databind.JsonNode root = yamlMapper.readTree(yaml);
        com.fasterxml.jackson.databind.JsonNode reviewNode =
                root.has("review") ? root.get("review") : root;

        RepoReviewConfig config = yamlMapper.treeToValue(
                reviewNode, RepoReviewConfig.class
        );

        // Mark as loaded from repo
        return config.toBuilder()
                .loadedFromRepo(true)
                .configVersion(yaml.hashCode() + "") // version = hash of YAML content
                .build();
    }

    private RepoReviewConfig mapEntityToConfig(RepoConfig entity) {
        return RepoReviewConfig.builder()
                .maxComments(entity.getMaxComments())
                .model(entity.getModel())
                .confidenceThreshold(entity.getConfidenceThreshold())
                .minimumSeverity(entity.getMinimumSeverity())
                .ignoreFiles(parseJsonArray(entity.getIgnoreFilesJson()))
                .alwaysFlag(parseJsonArray(entity.getAlwaysFlagJson()))
                .requirePatterns(parseJsonArray(entity.getRequirePatternsJson()))
                .loadedFromRepo(false)
                .configVersion("db-v" + entity.getId())
                .build();
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private RepoReviewConfig loadFromCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) return null;
            // Use JSON mapper (not YAML) for cache serialization
            return new ObjectMapper().readValue(json, RepoReviewConfig.class);
        } catch (Exception e) {
            log.debug("Config cache miss or error: {}", e.getMessage());
            return null;
        }
    }

    private void saveToCache(String cacheKey, RepoReviewConfig config) {
        try {
            String json = new ObjectMapper().writeValueAsString(config);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache config: {}", e.getMessage());
            // Non-fatal — just won't be cached
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
