package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public sealed interface AgentSetupService permits AgentWithClaudeCodeSetupService {

    void setup(@NotNull AiAgent agent, @NotNull AiAgentAttributes attributes);

    /** Stops the session if it is still running and deletes everything it left behind. */
    void cleanUp(@NotNull UUID sessionId);

    AiHarnessProvider getAiHarnessProvider();
}
