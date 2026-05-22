package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true)
public record RefPayload(
        @JsonProperty("label")
        String label,

        @JsonProperty("ref")
        String ref,

        @JsonProperty("sha")
        String sha,

        @JsonProperty("repo")
        RepositoryPayload repo
) {


}
