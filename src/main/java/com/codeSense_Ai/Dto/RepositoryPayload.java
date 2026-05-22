package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepositoryPayload(
        @JsonProperty("id")
        Long id,

        @JsonProperty("name")
        String name,

        @JsonProperty("full_name")
        String fullName,

        @JsonProperty("private")
        boolean isPrivate,

        @JsonProperty("html_url")
        String htmlUrl,

        @JsonProperty("default_branch")
        String defaultBranch,

        @JsonProperty("owner")
        UserPayload owner
) {
}
