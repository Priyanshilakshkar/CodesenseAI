package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestPayload(

        @JsonProperty("id")
        Long id,

        @JsonProperty("number")
        Integer number,

        @JsonProperty("title")
        String title,

        @JsonProperty("body")
        String body,

        @JsonProperty("state")
        String state,

        @JsonProperty("draft")
        boolean draft,

        @JsonProperty("head")
        RefPayload head,

        @JsonProperty("base")
        RefPayload base,

        @JsonProperty("user")
        UserPayload user,

        @JsonProperty("diff_url")
        String diffUrl,

        @JsonProperty("additions")
        Integer additions,

        @JsonProperty("deletions")
        Integer deletions,

        @JsonProperty("changed_files")
        Integer changedFiles
) {
}
