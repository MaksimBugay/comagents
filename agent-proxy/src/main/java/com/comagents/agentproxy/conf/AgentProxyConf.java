package com.comagents.agentproxy.conf;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class AgentProxyConf {

    private final String sourcesUrl;
    private final String sourcesRepoAccessToken;
    private final String anthropicApiKey;

    public AgentProxyConf(@Value("${clone.comagents.sources.url}") String sourcesUrl,
                          @Value("${sources.repo.access-token}") String sourcesRepoAccessToken,
                          @Value("${anthropic.api-key}") String anthropicApiKey) {
        this.sourcesUrl = sourcesUrl;
        this.sourcesRepoAccessToken = sourcesRepoAccessToken;
        this.anthropicApiKey = anthropicApiKey;
    }

    public String getComAgentsSourcesUrl() {
        return sourcesUrl;
    }

    public String getSourcesRepoAccessToken() {
        return sourcesRepoAccessToken;
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }
}
