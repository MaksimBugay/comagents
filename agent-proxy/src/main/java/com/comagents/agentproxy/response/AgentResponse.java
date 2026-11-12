package com.comagents.agentproxy.response;

import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record AgentResponse(AiAgent aiAgent, UUID sessionId, byte[] body, long createdAt) {
}
