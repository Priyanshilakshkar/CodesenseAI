package com.codeSense_Ai.Service;

import com.codeSense_Ai.Auth.GithubAppAuthenticator;
import com.codeSense_Ai.Client.GithubApiClient;
import com.codeSense_Ai.Dto.DiffFetchResult;
import com.codeSense_Ai.Dto.GithubFile;
import com.codeSense_Ai.Enties.Repositories;
import com.codeSense_Ai.Exceptions.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubFetchDiffService {
    private final GithubAppAuthenticator githubAppAuthenticator;
    private final RateLimitTracker rateLimitTracker;
    private final GithubApiClient apiClient;
    private final FilesHashCache filesHashCache;

    public DiffFetchResult fetchDiff(Repositories repositories , int pnrNumber ){
        String owner = repositories.getOwner();
        String repo = repositories.getName();
        Long installationId  = repositories.getInstallation_id();

        checkRateLimit(owner , repo);

        String token  = githubAppAuthenticator.generateIntallationToken(installationId);

        List<GithubFile> allFiles = apiClient.getPullRequestFiles(owner , repo ,pnrNumber , installationId);

        return classifyFiles(allFiles , installationId);



    }
    public void checkRateLimit(String owner , String repoName){
        int remaining  = rateLimitTracker.getRemainingRequests(owner , repoName);
        Long resetSeconds = rateLimitTracker.getSecondsUntilReset(owner , repoName);
        if(rateLimitTracker.isBelowThreshold(remaining)){
            log.warn("GitHub rate limit too low ({} remaining). Resets in {}s. " +
                    "Delaying job for {}/{}" ,remaining , resetSeconds ,owner , repoName);
            throw new RateLimitExceededException(
                    "GitHub rate limit too low: " + remaining + " remaining. " +
                            "Resets in " + resetSeconds + " seconds.",
                    resetSeconds
            );
        }

    }
    public DiffFetchResult classifyFiles(List<GithubFile> allFiles , Long installationId){
        List<GithubFile> skipped = new ArrayList<>();
        List<GithubFile> reviewable = new ArrayList<>();
        List<GithubFile> cached = new ArrayList<>();

        for(GithubFile file : allFiles){
            if(!file.hasPatch()){
                skipped.add(file);
            }

            if(file.isLikelyNotReviewable()){
                skipped.add(file);
            }

            if(filesHashCache.contains(file.blobSha())){
                cached.add(file);
            }

            reviewable.add(file);
        }

        return  new DiffFetchResult( reviewable , cached ,skipped, allFiles.size(), installationId);
    }
}
