package com.codeSense_Ai.Rule;

import com.codeSense_Ai.Enties.RepoConfig;
import com.codeSense_Ai.Enties.Repositories;
import com.codeSense_Ai.Repository.RepoConfigRepository;
import com.codeSense_Ai.Repository.RepositoriesRepository;
import com.codeSense_Ai.model.RepoReviewConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoConfigService {
    private final RepoConfigRepository configRepository;
    private final RepositoriesRepository repoRepository;
    private final RuleValidator        ruleValidator;
    private final RuleLoader           ruleLoader;
    private final ObjectMapper objectMapper;

    /**
     * Gets the current effective config for a repo.
     * Returns defaults if nothing has been configured.
     */
    @PreAuthorize("isAuthenticated()")
    public RepoReviewConfig getConfig(Long repositoryId) {
        return configRepository.findByRepositoryId(repositoryId)
                .map(this::mapToConfig)
                .orElse(RepoReviewConfig.defaults());
    }

    /**
     * Saves or updates the DB config for a repo.
     * Validates before saving — throws if config is invalid.
     * Evicts Redis cache so next review uses the new config.
     *
     * Called by DashboardController when a user submits the
     * "Repository Settings" form.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('REPO_OWNER')")
    @Transactional
    public RepoConfig saveConfig(Long repositoryId, RepoReviewConfig config) {
        // Validate first
        RuleValidator.ValidationResult validation = ruleValidator.validate(config);
        if (!validation.isValid()) {
            throw new InvalidConfigException(
                    "Invalid config: " + validation.errorSummary()
            );
        }

        Repositories repo = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Repository not found: " + repositoryId
                ));

        // Upsert — update if exists, create if not
        RepoConfig entity = configRepository
                .findByRepositoryId(repositoryId)
                .orElseGet(() -> {
                    RepoConfig newEntity = new RepoConfig();
                    newEntity.setRepository(repo);
                    newEntity.setCreatedAt(Instant.now());
                    return newEntity;
                });

        // Map config fields to entity
        entity.setMaxComments(config.getMaxComments());
        entity.setModel(config.getModel());
        entity.setConfidenceThreshold(config.getConfidenceThreshold());
        entity.setMinimumSeverity(config.getMinimumSeverity());
        entity.setIgnoreFilesJson(toJson(config.getIgnoreFiles()));
        entity.setOnlyFilesJson(toJson(config.getOnlyFiles()));
        entity.setAlwaysFlagJson(toJson(config.getAlwaysFlag()));
        entity.setRequirePatternsJson(toJson(config.getRequirePatterns()));
        entity.setIgnoreCategoriesJson(toJson(config.getIgnoreCategories()));
        entity.setUpdatedAt(Instant.now());

        RepoConfig saved = configRepository.save(entity);
        log.info("Saved config for repo {}: maxComments={} model={}",
                repositoryId, config.getMaxComments(), config.getModel());

        // Evict cache — next review will use the new config
        ruleLoader.evictCache(repositoryId);

        return saved;
    }

    /**
     * Resets a repo's config to system defaults.
     * Deletes the DB entry — ruleLoader will fall back to defaults.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void resetToDefaults(Long repositoryId) {
        configRepository.findByRepositoryId(repositoryId)
                .ifPresent(config -> {
                    configRepository.delete(config);
                    log.info("Reset config to defaults for repo {}", repositoryId);
                });
        ruleLoader.evictCache(repositoryId);
    }

    /**
     * Returns all repos with custom configs — for the admin dashboard.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<RepoConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RepoReviewConfig mapToConfig(RepoConfig entity) {
        return RepoReviewConfig.builder()
                .maxComments(entity.getMaxComments())
                .model(entity.getModel())
                .confidenceThreshold(entity.getConfidenceThreshold())
                .minimumSeverity(entity.getMinimumSeverity())
                .ignoreFiles(fromJson(entity.getIgnoreFilesJson()))
                .onlyFiles(fromJson(entity.getOnlyFilesJson()))
                .alwaysFlag(fromJson(entity.getAlwaysFlagJson()))
                .requirePatterns(fromJson(entity.getRequirePatternsJson()))
                .loadedFromRepo(false)
                .configVersion("db-v" + entity.getId())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // Custom exception for invalid config submissions
    public static class InvalidConfigException extends RuntimeException {
        public InvalidConfigException(String message) { super(message); }
    }
}
