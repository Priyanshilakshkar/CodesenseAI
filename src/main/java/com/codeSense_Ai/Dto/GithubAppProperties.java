package com.codeSense_Ai.Dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "codesense.github")
@Component
public class GithubAppProperties {
    private String appId;
    private String privateKeyPath;
    private String apiBaseUrl;
    private int rateLimitThreshold;
    private Long installationId;
    private String webhookSecret;
}
