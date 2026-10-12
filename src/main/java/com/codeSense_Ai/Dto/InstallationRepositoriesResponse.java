package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationRepositoriesResponse(
        @JsonProperty("total_count")
        int totalCount,

        @JsonProperty("repositories")
        List<RepositoryPayload> repositories

) {
}
