package com.comagents.agentorchestrator.conf;

import com.comagents.commons.communication.DeviceIdExtractor;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class AgentOrchestratorConf {

    private final String version;
    private final String pushcaApiUrl;
    private final String pushcaApiKey;
    private final String deviceId;

    public AgentOrchestratorConf(@Value("${agent-orchestrator.version:unknown}") String version,
                                 @Value("${pushca.api-url}") String pushcaApiUrl,
                                 @Value("${pushca.api-key}") String pushcaApiKey,
                                 DeviceIdExtractor deviceIdExtractor) {
        this.version = version;
        this.pushcaApiUrl = pushcaApiUrl;
        this.pushcaApiKey = pushcaApiKey;
        this.deviceId = deviceIdExtractor.extract();
    }

    public String getVersion() {
        return version;
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
