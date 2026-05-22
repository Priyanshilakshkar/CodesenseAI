package com.codeSense_Ai.Dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SenderPayload(

        Long id,
        String login,
        String avatarUrl,
        String htmlUrl,
        String type
) {
}
