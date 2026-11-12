package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import com.comagents.agentproxy.vcs.VcsService;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

// non-sealed, not final: method validation (@NotNull on setup) needs a generated subclass proxy
@Singleton
public non-sealed class AgentWithClaudeCodeSetupService implements AgentSetupService {

    // what Claude Code loads from the folder it runs in
    private static final List<String> CLAUDE_CODE_FILES = List.of("CLAUDE.md", ".claude", ".mcp.json");

    // documentation about the agent (every agent has one), kept beside the repository instead of overwriting
    // the repository's own; copying it also creates <session folder>/<agent name>
    private static final List<String> AGENT_DOCS = List.of(AgentFiles.README);

    private final VcsService vcsService;

    private final ClaudeAgentSessionRegistry claudeAgentSessionRegistry;

    public AgentWithClaudeCodeSetupService(VcsService vcsService, ClaudeAgentSessionRegistry claudeAgentSessionRegistry) {
        this.vcsService = vcsService;
        this.claudeAgentSessionRegistry = claudeAgentSessionRegistry;
    }


    @Override
    public void setup(AiAgent agent, AiAgentAttributes attributes) {
        Path agentFolder = SessionFolders.agentFolder(attributes.sessionId(), agent);
        Path workingDirectory = prepareWorkingDirectory(attributes, agentFolder);
        vcsService.cloneComAgentsSources(comAgentsSources -> {
            AgentFiles.copyInto(workingDirectory, comAgentsSources, agent, CLAUDE_CODE_FILES);
            AgentFiles.copyInto(agentFolder, comAgentsSources, agent, AGENT_DOCS);
        });
        claudeAgentSessionRegistry.createSession(attributes.sessionId(), agent, workingDirectory);
    }

    // Claude Code runs in the cloned repository, or in the agent folder when there is nothing to clone
    private Path prepareWorkingDirectory(AiAgentAttributes attributes, Path agentFolder) {
        if (StringUtils.isNotEmpty(attributes.cloneSourceUrl())) {
            return vcsService.clone(attributes.cloneSourceUrl(), attributes.sessionId());
        }
        return agentFolder;
    }

    @Override
    public void cleanUp(UUID sessionId) {
        claudeAgentSessionRegistry.cleanAllSessionData(sessionId);
    }

    @Override
    public AiHarnessProvider getAiHarnessProvider() {
        return AiHarnessProvider.CLAUDE_CODE;
    }
}
