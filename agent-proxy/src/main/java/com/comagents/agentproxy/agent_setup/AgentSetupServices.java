package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Finds the setup service for an agent's harness. */
@Singleton
public class AgentSetupServices {

    private final Map<AiHarnessProvider, AgentSetupService> byProvider;

    public AgentSetupServices(List<AgentSetupService> agentSetupServices) {
        // toMap fails fast at startup if two setup services claim the same provider
        this.byProvider = agentSetupServices.stream()
                .collect(Collectors.toMap(AgentSetupService::getAiHarnessProvider, Function.identity()));
    }

    /** Empty when the agent is known but no setup service exists for its harness yet. */
    public Optional<AgentSetupService> forAgent(AiAgent agent) {
        return Optional.ofNullable(byProvider.get(agent.getAiHarnessProvider()));
    }
}
