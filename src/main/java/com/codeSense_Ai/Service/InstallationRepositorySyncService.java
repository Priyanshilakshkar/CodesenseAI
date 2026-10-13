package com.codeSense_Ai.Service;

import com.codeSense_Ai.Auth.GithubAppAuthenticator;
import com.codeSense_Ai.Dto.GithubAppProperties;
import com.codeSense_Ai.Dto.InstallationRepositoriesResponse;
import com.codeSense_Ai.Dto.RepositoryPayload;
import com.codeSense_Ai.Enties.Repositories;
import com.codeSense_Ai.Repository.RepositoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
@RequiredArgsConstructor
public class InstallationRepositorySyncService {
    private final GithubAppAuthenticator authenticator;
    private final GithubAppProperties githubAppProperties;
    private final RestClient restClient;
    private final RepositoriesRepository repositoriesRepository;

    @Transactional
    public void syncInstallationRepositories(Long installationId) {
        if (installationId == null) {
            throw new IllegalArgumentException(
                    "Installation ID cannot be null"
            );
        }

        String token =
                authenticator.generateIntallationToken(installationId);

        int page = 1;
        int perPage = 100;
        int savedCount = 0;

        while (true) {
            final int currentPage = page;

            InstallationRepositoriesResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/installation/repositories")
                            .queryParam("per_page", perPage)
                            .queryParam("page", currentPage)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(InstallationRepositoriesResponse.class);

            log.info("Syncing repositories for installation {}", installationId);

            log.info("GitHub API returned {} repositories on page {}",
                    response == null || response.repositories() == null
                            ? 0 : response.repositories().size(),
                    page);
            if (response == null || response.repositories() == null
                    || response.repositories().isEmpty()) {
                break;
            }

            List<RepositoryPayload> repositories =
                    response.repositories();

            for (RepositoryPayload repository : repositories) {
                registerOrUpdate(repository, installationId);
                savedCount++;
            }

            if (repositories.size() < perPage) {
                break;
            }

            page++;
        }

        log.info(
                "Synchronized {} repositories for installation {}",
                savedCount,
                installationId
        );
    }

    private void registerOrUpdate(
            RepositoryPayload payload,
            Long installationId
    ) {
        if (payload.id() == null
                || payload.owner() == null
                || payload.owner().login() == null
                || payload.name() == null
                || payload.fullName() == null) {
            log.warn("Skipping repository with incomplete GitHub data");
            return;
        }

        Repositories repository = repositoriesRepository
                .findByGithubRepoId(payload.id())
                .orElseGet(Repositories::new);

        repository.setGithubRepoId(payload.id());
        repository.setOwner(payload.owner().login());
        repository.setName(payload.name());
        repository.setFullName(payload.fullName());
        repository.setInstallation_id(installationId);
        repository.setActive(true);

        if (payload.defaultBranch() != null
                && !payload.defaultBranch().isBlank()) {
            repository.setDefaultBranch(payload.defaultBranch());
        } else if (repository.getDefaultBranch() == null) {
            repository.setDefaultBranch("main");
        }

        repositoriesRepository.save(repository);
    }
    @Transactional
    public void deactivateInstallationRepositories(Long installationId) {
        List<Repositories> repositories =
                repositoriesRepository.findAllByInstallationId(installationId);

        for (Repositories repository : repositories) {
            repository.setActive(false);
        }

        repositoriesRepository.saveAll(repositories);
    }
    @Transactional
    public void syncInstallationRepositoryChanges(
            Long installationId,
            List<RepositoryPayload> added,
            List<RepositoryPayload> removed
    ) {
        if (installationId == null) {
            throw new IllegalArgumentException(
                    "Installation ID cannot be null"
            );
        }

        // Register newly added repositories.
        if (added != null) {
            for (RepositoryPayload repository : added) {
                registerOrUpdate(repository, installationId);
            }
        }

        // Deactivate only the repositories removed from this installation.
        if (removed != null) {
            for (RepositoryPayload payload : removed) {
                if (payload.id() == null) {
                    continue;
                }

                repositoriesRepository
                        .findByGithubRepoId(payload.id())
                        .filter(repository ->
                                installationId.equals(
                                        repository.getInstallation_id()
                                )
                        )
                        .ifPresent(repository -> {
                            repository.setActive(false);
                            repositoriesRepository.save(repository);
                        });
            }
        }

        log.info(
                "Processed repository changes for installation {}",
                installationId
        );
    }
}
