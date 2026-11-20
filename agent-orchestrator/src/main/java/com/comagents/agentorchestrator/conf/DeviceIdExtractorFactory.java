package com.comagents.agentorchestrator.conf;

import com.comagents.commons.communication.DeviceIdExtractor;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/** The {@link DeviceIdExtractor} for this agent-orchestrator: {@code agent-orchestrator.device-id} when set. */
@Factory
public class DeviceIdExtractorFactory {

    private static final String NO_HOSTNAME = "local";

    @Singleton
    public DeviceIdExtractor deviceIdExtractor(@Value("${agent-orchestrator.device-id:}") String configuredDeviceId,
                                               @Value("${HOSTNAME:" + NO_HOSTNAME + "}") String hostname) {
        return new DeviceIdExtractor(configuredDeviceId, hostname);
    }
}
