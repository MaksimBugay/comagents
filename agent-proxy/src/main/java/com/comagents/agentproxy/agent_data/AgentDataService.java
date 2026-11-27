package com.comagents.agentproxy.agent_data;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.commons.domain.AiAgent;

import java.util.List;
import java.util.Map;

public interface AgentDataService {

    Map<AiAgent, ClaudeAgentConfig> getAgentConfig(List<AiAgent> agents);
}
