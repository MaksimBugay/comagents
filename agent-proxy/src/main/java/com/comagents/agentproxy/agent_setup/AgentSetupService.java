package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AgentSessionStatus;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;
import java.util.UUID;

public sealed interface AgentSetupService permits AgentWithClaudeCodeSetupService {

    void setup(@NotNull AiAgent agent, @NotNull AiAgentAttributes attributes);

    /** Empty when the session is unknown: never started, or already cleaned up. */
    Optional<AgentSessionStatus> status(@NotNull UUID sessionId);

    /** Stops the session if it is still running and deletes everything it left behind. */
    void cleanUp(@NotNull UUID sessionId);

    AiHarnessProvider getAiHarnessProvider();
}
