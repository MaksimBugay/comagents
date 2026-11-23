package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.response.AgentInfoResponse;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Singleton
public class StartAgentSessionHelper {

    private final ComagentsGateway gateway;

    public StartAgentSessionHelper(ComagentsGateway gateway) {
        this.gateway = gateway;
    }

    public AgentInfoResponse startAgentSession(@NotNull AiAgent agent,
                                               @Nullable AgentContext agentContext,
                                               @NotBlank String originatorDeviceId,
                                               List<String> agentCallSequence,
                                               String actorAccountId,
                                               AgentSessionLoggingLevel loggingLevel) {
        return null;
    }
}
