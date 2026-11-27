package com.comagents.commons.agent_definition;

import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.commons.domain.AiAgent;

import java.util.List;
import java.util.Map;

public interface AgentDefinitionService {

    Map<AiAgent, ClaudeAgentConfig> getAgentConfig(List<AiAgent> agents);
}
