package com.codeSense_Ai.Dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "codesense.github")
public class GithubAppProperties {
    private String appId;
    private String privateKeyPath;
    private String apiBaseUrl;
    private int rateLimitThreshold;
    private Long installationId;
}
