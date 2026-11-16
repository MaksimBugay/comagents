package com.comagents.agentproxy.request;

import com.comagents.agentproxy.agent_setup.AgentSessionLoggingLevel;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.agent_context.AgentContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param agentContext what the session works with: its primary repository is cloned as the working directory, and
 *                     all its items are added to the kick-off prompt; without one nothing is cloned
 */
@Serdeable
public record StartAgentRequest(UUID sessionId,
                                @NotNull AiAgent agent,
                                @Nullable AgentContext agentContext,
                                String actorAccountId,
                                AgentSessionLoggingLevel loggingLevel) {

    public StartAgentRequest(UUID sessionId, AiAgent agent, @Nullable AgentContext agentContext, String actorAccountId) {
        this(sessionId, agent, agentContext, actorAccountId, AgentSessionLoggingLevel.INFO);
    }
}
