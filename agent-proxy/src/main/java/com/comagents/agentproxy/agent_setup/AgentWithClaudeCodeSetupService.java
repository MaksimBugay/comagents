package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry.SessionSlot;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.agentproxy.domain.AgentSessionStatus;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.AiHarnessProvider;
import com.comagents.agentproxy.vcs.VcsService;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// non-sealed, not final: method validation (@NotNull on setup) needs a generated subclass proxy
@Singleton
public non-sealed class AgentWithClaudeCodeSetupService implements AgentSetupService {

    // what Claude Code loads from the folder it runs in
    private static final List<String> CLAUDE_CODE_FILES = List.of("CLAUDE.md", ".claude", ".mcp.json");

    // kept beside the repository, not in it: the agent's README (every agent has one) and agent-proxy's run settings
    // for it (agent.json); copying them also creates <session folder>/<agent name>
    private static final List<String> AGENT_FOLDER_FILES = List.of(AgentFiles.README, ClaudeAgentConfig.FILE_NAME);

    private final VcsService vcsService;

    private final ClaudeAgentSessionRegistry claudeAgentSessionRegistry;

    private final SessionFolders sessionFolders;

    public AgentWithClaudeCodeSetupService(VcsService vcsService, ClaudeAgentSessionRegistry claudeAgentSessionRegistry,
                                           SessionFolders sessionFolders) {
        this.vcsService = vcsService;
        this.claudeAgentSessionRegistry = claudeAgentSessionRegistry;
        this.sessionFolders = sessionFolders;
    }


    /**
     * Reserves a session slot first, so a busy service or a session id already in use (in memory or on disk) is
     * rejected before any download; a failed setup frees the slot and leaves no half-prepared session folder behind.
     */
    @Override
    public void setup(AiAgent agent, AiAgentAttributes attributes) {
        UUID sessionId = attributes.sessionId();
        try (SessionSlot slot = claudeAgentSessionRegistry.reserve(sessionId)) {
            requireNoSessionFolder(sessionId);
            try {
                Path agentFolder = sessionFolders.agentFolder(sessionId, agent);
                Path workingDirectory = prepareWorkingDirectory(attributes, agentFolder);
                vcsService.cloneComAgentsSources(comAgentsSources -> {
                    AgentFiles.copyInto(workingDirectory, comAgentsSources, agent, CLAUDE_CODE_FILES);
                    AgentFiles.copyInto(agentFolder, comAgentsSources, agent, AGENT_FOLDER_FILES);
                });
                slot.start(agent, workingDirectory);
            } catch (RuntimeException e) {
                // the folder did not exist before and the slot holds the id, so it belongs to this failed setup
                FileTrees.deleteIfExists(sessionFolders.of(sessionId));
                throw e;
            }
        }
    }

    // the sessions dir is persistent: an earlier session with this id (finished, archived, or from before a restart)
    // may still be on disk, and it is neither reused nor deleted
    private void requireNoSessionFolder(UUID sessionId) {
        Path sessionFolder = sessionFolders.of(sessionId);
        if (Files.exists(sessionFolder)) {
            throw new ClaudeSessionExistsException(sessionId, "already has data in " + sessionFolder);
        }
    }

    // Claude Code runs in the cloned repository, or in the agent folder when there is nothing to clone
    private Path prepareWorkingDirectory(AiAgentAttributes attributes, Path agentFolder) {
        if (StringUtils.isNotEmpty(attributes.cloneSourceUrl())) {
            return vcsService.clone(attributes.cloneSourceUrl(), sessionFolders.of(attributes.sessionId()));
        }
        return agentFolder;
    }

    @Override
    public Optional<AgentSessionStatus> status(UUID sessionId) {
        return claudeAgentSessionRegistry.findSession(sessionId)
                .map(session -> new AgentSessionStatus(session.getState(), session.getStartedAt(), session.getFinishedAt()));
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
