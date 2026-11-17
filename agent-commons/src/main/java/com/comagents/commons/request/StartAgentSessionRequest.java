package com.comagents.commons.request;

import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * @param agentContext         what the session works with: its primary repository is cloned as the working directory,
 *                             and all its items are added to the kick-off prompt; without one nothing is cloned
 * @param orchestratorDeviceId the orchestrator device that gets the session's response
 */
public record StartAgentSessionRequest(UUID sessionId,
                                       @NotNull AiAgent agent,
                                       @Nullable AgentContext agentContext,
                                       @NotBlank String orchestratorDeviceId,
                                       String actorAccountId,
                                       AgentSessionLoggingLevel loggingLevel,
                                       long createdAt) {

    public StartAgentSessionRequest(UUID sessionId, AiAgent agent, @Nullable AgentContext agentContext, String orchestratorDeviceId, String actorAccountId) {
        this(sessionId, agent, agentContext, orchestratorDeviceId, actorAccountId, AgentSessionLoggingLevel.INFO, Instant.now().toEpochMilli());
    }
}
