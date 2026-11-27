package com.comagents.commons.response;

import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.commons.domain.AiAgent;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Map;

/**
 * The body of agent-proxy's {@code GET /agent-definition/config}: the {@code agent.json} of each agent asked, in the
 * order asked.
 */
public record AgentConfigResponse(List<AgentConfig> agentConfigs) {

    public AgentConfigResponse {
        Validate.notNull(agentConfigs, "agentConfigs must not be null");
        agentConfigs = List.copyOf(agentConfigs);
    }

    /** In the map's iteration order. */
    public static AgentConfigResponse of(Map<AiAgent, ClaudeAgentConfig> configs) {
        return new AgentConfigResponse(configs.entrySet().stream()
                .map(entry -> new AgentConfig(entry.getKey(), entry.getValue()))
                .toList());
    }

    /**
     * @param agent  the agent
     * @param config its {@code agent.json}
     */
    public record AgentConfig(AiAgent agent, ClaudeAgentConfig config) {

        public AgentConfig {
            Validate.notNull(agent, "agent must not be null");
            Validate.notNull(config, "config must not be null");
        }
    }
}
