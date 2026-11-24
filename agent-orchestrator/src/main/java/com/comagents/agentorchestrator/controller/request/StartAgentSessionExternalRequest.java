package com.comagents.agentorchestrator.controller.request;

import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import io.micronaut.core.annotation.Introspected;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// introspected for the @Valid of the HTTP API
@Introspected
public record StartAgentSessionExternalRequest(@NotNull AiAgent agent,
                                               @Nullable AgentContext agentContext,
                                               List<String> agentCallSequence,
                                               String actorAccountId,
                                               String agentResponseWaiterDeviceId,
                                               AgentSessionLoggingLevel loggingLevel) {
    @SuppressWarnings("unused")
    public StartAgentSessionExternalRequest(AiAgent agent, @Nullable AgentContext agentContext, String actorAccountId) {
        this(agent, agentContext, List.of(), actorAccountId, null, AgentSessionLoggingLevel.INFO);
    }
}
