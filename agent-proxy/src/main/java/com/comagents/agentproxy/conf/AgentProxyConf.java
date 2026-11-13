package com.comagents.agentproxy.conf;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class AgentProxyConf {

    private final String sourcesUrl;
    private final String sourcesRepoAccessToken;
    private final String anthropicApiKey;
    private final String pushcaApiUrl;
    private final String pushcaApiKey;

    public AgentProxyConf(@Value("${clone.comagents.sources.url}") String sourcesUrl,
                          @Value("${sources.repo.access-token}") String sourcesRepoAccessToken,
                          @Value("${anthropic.api-key}") String anthropicApiKey,
                          @Value("${pushca.api-url}") String pushcaApiUrl,
                          @Value("${pushca.api-key}") String pushcaApiKey) {
        this.sourcesUrl = sourcesUrl;
        this.sourcesRepoAccessToken = sourcesRepoAccessToken;
        this.anthropicApiKey = anthropicApiKey;
        this.pushcaApiUrl = pushcaApiUrl;
        this.pushcaApiKey = pushcaApiKey;
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

    public String getPushcaApiUrl() {
        return pushcaApiUrl;
    }

    public String getPushcaApiKey() {
        return pushcaApiKey;
    }
}
