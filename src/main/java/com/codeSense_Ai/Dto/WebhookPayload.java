package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        @JsonProperty("action")
        String action,

        @JsonProperty("number")
        Long number,

        @JsonProperty("pull_request")
        PullRequestPayload pullRequest,

        @JsonProperty("repository")
        RepositoryPayload repository,

        @JsonProperty("sender")
        SenderPayload sender,

        @JsonProperty("installation")
        InstallationPayload installation
) {
}
