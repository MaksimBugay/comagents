package com.comagents.agentproxy.conf;

import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.domain.AiHarnessProvider;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class AgentProxyConf {

    private final String version;
    private final String sourcesUrl;
    private final String sourcesRepoAccessToken;
    private final String pushcaApiUrl;
    private final String pushcaApiKey;
    private final String deviceId;
    private final AiHarnessProvider aiHarnessProvider;

    public AgentProxyConf(@Value("${agent-proxy.version:unknown}") String version,
                          @Value("${clone.comagents.sources.url}") String sourcesUrl,
                          @Value("${sources.repo.access-token}") String sourcesRepoAccessToken,
                          @Value("${pushca.api-url}") String pushcaApiUrl,
                          @Value("${pushca.api-key}") String pushcaApiKey,
                          @Value("${agent-proxy.ai-harness-provider}") AiHarnessProvider aiHarnessProvider,
                          DeviceIdExtractor deviceIdExtractor) {
        this.version = version;
        this.sourcesUrl = sourcesUrl;
        this.sourcesRepoAccessToken = sourcesRepoAccessToken;
        this.pushcaApiUrl = pushcaApiUrl;
        this.pushcaApiKey = pushcaApiKey;
        this.aiHarnessProvider = aiHarnessProvider;
        this.deviceId = deviceIdExtractor.extract();
    }

    public String getVersion() {
        return version;
    }

    /** The harness installed next to agent-proxy, which runs the agent sessions; set by the Docker image. */
    public AiHarnessProvider getAiHarnessProvider() {
        return aiHarnessProvider;
    }

    public String getComAgentsSourcesUrl() {
        return sourcesUrl;
    }

    public String getSourcesRepoAccessToken() {
        return sourcesRepoAccessToken;
    }

    public String getPushcaApiUrl() {
        return pushcaApiUrl;
    }

    public String getPushcaApiKey() {
        return pushcaApiKey;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
