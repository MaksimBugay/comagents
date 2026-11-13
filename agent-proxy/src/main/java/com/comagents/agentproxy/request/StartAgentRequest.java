package com.comagents.agentproxy.request;

import com.comagents.agentproxy.agent_setup.AgentSessionLoggingLevel;
import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Serdeable
public record StartAgentRequest(UUID sessionId,
                                @NotNull AiAgent agent,
                                String cloneSourceUrl,
                                String actorAccountId,
                                AgentSessionLoggingLevel loggingLevel) {

    public StartAgentRequest(UUID sessionId, AiAgent agent, String cloneSourceUrl, String actorAccountId) {
        this(sessionId, agent, cloneSourceUrl, actorAccountId, AgentSessionLoggingLevel.INFO);
    }
}
