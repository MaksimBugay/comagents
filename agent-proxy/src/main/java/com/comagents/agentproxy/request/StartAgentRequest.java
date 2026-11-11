package com.comagents.agentproxy.request;

import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Serdeable
public record StartAgentRequest(UUID sessionId, @NotNull AiAgent agent, String cloneSourceUrl) {
}
