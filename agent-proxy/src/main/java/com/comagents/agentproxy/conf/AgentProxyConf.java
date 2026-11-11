package com.comagents.agentproxy.conf;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class AgentProxyConf {

    private final String sourcesUrl;
    private final String sourcesRepoAccessToken;

    public AgentProxyConf(@Value("${clone.comagents.sources.url}") String sourcesUrl,
                          @Value("${sources.repo.access-token}") String sourcesRepoAccessToken) {
        this.sourcesUrl = sourcesUrl;
        this.sourcesRepoAccessToken = sourcesRepoAccessToken;
    }

    public String getComAgentsSourcesUrl() {
        return sourcesUrl;
    }

    public String getSourcesRepoAccessToken() {
        return sourcesRepoAccessToken;
    }
}
