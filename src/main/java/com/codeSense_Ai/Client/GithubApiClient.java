package com.codeSense_Ai.Client;

import com.codeSense_Ai.Auth.GithubAppAuthenticator;
import com.codeSense_Ai.Dto.GithubAppProperties;
import com.codeSense_Ai.Dto.GithubFile;
import com.codeSense_Ai.Service.RateLimitTracker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GithubApiClient {
    private final GithubAppAuthenticator githubAppAuthenticator ;
    private  final GithubAppProperties githubAppProperties ;
    private final RestClient restClient;
    private final RateLimitTracker rateLimitTracker;

    @Retry(name = "GithubApiRetry" ,
              fallbackMethod = "")
    public List<GithubFile>  getPullRequestFiles(String owner, String repo,
                                                 int prNumber, Long installationId){
        String token  = githubAppAuthenticator.generateIntallationToken(installationId);
        RestClient.ResponseSpec response = restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pr}/files", owner, repo, prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve();

        return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pr}/files", owner, repo, prNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .exchange((request, resp) -> {
                    // ← Update tracker with every response's headers
                    rateLimitTracker.updateFromReponses(owner, repo, resp.getHeaders());

                    return resp.bodyTo(new ParameterizedTypeReference<List<GithubFile>>() {});
                });


    }
    public List<GithubFile> getPullRequestFilesFallback(String owner , String repo , int pnrNumber , Long installationid , Exception ex){
        log.error( "Github api failed after retries for owner {} , repository {} , pnrNumber {} , exception = {} " ,
                owner , repo , pnrNumber ,ex.getMessage());

        throw new RuntimeException("Github Api temporily unavilable after retries0 " ,ex);


    }
    public String getFileContent(
            String owner,
            String repo,
            String path,
            String token
    ) throws Exception {

        try {

            return restClient.get()
                    .uri(
                            "/repos/{owner}/{repo}/contents/{path}",
                            owner,
                            repo,
                            path
                    )
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.raw")
                    .retrieve()
                    .body(String.class);

        } catch (HttpClientErrorException.NotFound e) {

            throw new java.io.FileNotFoundException(
                    path + " not found in repository"
            );
        }
    }

}
