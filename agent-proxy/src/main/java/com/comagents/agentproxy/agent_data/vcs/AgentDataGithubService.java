package com.comagents.agentproxy.agent_data.vcs;

import com.comagents.agentproxy.agent_data.AgentDataService;
import com.comagents.agentproxy.agent_setup.AgentFiles;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.vcs.VcsService;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.serialization.JsonUtility;
import com.comagents.commons.serialization.SerializationException;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent data from the comagents sources on GitHub: each agent's {@code agent.json} is downloaded alone, from
 * {@code agent/<harness>/<agent name>/}.
 */
@Singleton
@Requires(property = "comagents.agent-data.source.type", value = "GIT_HUB", defaultValue = "GIT_HUB")
public class AgentDataGithubService implements AgentDataService {

    private final VcsService vcsService;

    public AgentDataGithubService(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    /**
     * @throws IllegalStateException when an agent has no readable {@code agent.json}
     */
    @Override
    public Map<AiAgent, ClaudeAgentConfig> getAgentConfig(List<AiAgent> agents) {
        Map<AiAgent, ClaudeAgentConfig> configs = new LinkedHashMap<>();
        agents.forEach(agent -> configs.computeIfAbsent(agent, this::agentConfig));
        return configs;
    }

    private ClaudeAgentConfig agentConfig(AiAgent agent) {
        String file = AgentFiles.sourcesFolder(agent) + "/" + ClaudeAgentConfig.FILE_NAME;
        try {
            return JsonUtility.fromJson(vcsService.downloadComAgentsFile(file), ClaudeAgentConfig.class);
        } catch (SerializationException e) {
            throw new IllegalStateException("Agent " + agent.getAgentName() + " has no readable " + file, e);
        }
    }
}
