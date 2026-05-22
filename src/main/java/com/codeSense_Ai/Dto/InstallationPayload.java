package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationPayload(
        @JsonProperty("id")
        Long id,

        @JsonProperty("node_id")
        String nodeId

) {
}
