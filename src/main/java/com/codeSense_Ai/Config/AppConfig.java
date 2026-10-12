package com.codeSense_Ai.Config;

import com.codeSense_Ai.Dto.GithubAppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(GithubAppProperties githubAppProperties) {

        return RestClient.builder()
                .baseUrl(githubAppProperties.getApiBaseUrl())
                .build();
    }
}
