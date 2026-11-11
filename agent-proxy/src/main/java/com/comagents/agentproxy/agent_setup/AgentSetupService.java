package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import jakarta.validation.constraints.NotNull;

public sealed interface AgentSetupService permits AgentWithClaudeCodeSetupService {

    void setup(@NotNull AiAgent agent, @NotNull AiAgentAttributes attributes);

    AiHarnessProvider getAiHarnessProvider();
}
