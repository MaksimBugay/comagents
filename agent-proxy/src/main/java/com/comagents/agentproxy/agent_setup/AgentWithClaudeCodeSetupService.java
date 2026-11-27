package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_call.AgentCallToolDefinition;
import com.comagents.commons.agent_definition.AgentDefinitionService;
import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.commons.agent_definition.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry.SessionSlot;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionOptions;
import com.comagents.agentproxy.domain.AgentSessionStatus;
import com.comagents.agentproxy.domain.agent_context.AgentContextValidator;
import com.comagents.agentproxy.mcp.AgentCallTools;
import com.comagents.agentproxy.vcs.VcsService;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.AgentContextItem;
import com.comagents.commons.domain.agent_context.InvalidAgentContextException;
import com.comagents.commons.exception.ClaudeSessionExistsException;
import com.comagents.commons.serialization.JsonUtility;
import com.comagents.commons.serialization.SerializationException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    private final AgentSessionWsClientRegistry agentSessionWsClientRegistry;

    private final AgentContextValidator agentContextValidator;

    private final AgentCallTools agentCallTools;

    private final AgentDefinitionService agentDefinitionService;

    public AgentWithClaudeCodeSetupService(VcsService vcsService, ClaudeAgentSessionRegistry claudeAgentSessionRegistry,
                                           SessionFolders sessionFolders,
                                           AgentSessionWsClientRegistry agentSessionWsClientRegistry,
                                           AgentContextValidator agentContextValidator,
                                           AgentCallTools agentCallTools, AgentDefinitionService agentDefinitionService) {
        this.vcsService = vcsService;
        this.claudeAgentSessionRegistry = claudeAgentSessionRegistry;
        this.sessionFolders = sessionFolders;
        this.agentSessionWsClientRegistry = agentSessionWsClientRegistry;
        this.agentContextValidator = agentContextValidator;
        this.agentCallTools = agentCallTools;
        this.agentDefinitionService = agentDefinitionService;
    }


    /**
     * Reserves a session slot first, so a busy service or a session id already in use (in memory or on disk) is
     * rejected before any download; a failed setup frees the slot and leaves no half-prepared session folder or
     * chat behind. The request's agent context is checked against the agent's {@code agent.json} before anything is
     * cloned for the session ({@link InvalidAgentContextException}). The session's Pushca chat is opened last, right
     * before the run starts, so it gets all output; without Pushca the session runs without a chat.
     */
    @Override
    public void setup(AiAgent agent, AgentSessionAttributes attributes) {
        UUID sessionId = attributes.sessionId();
        try (SessionSlot slot = claudeAgentSessionRegistry.reserve(sessionId)) {
            requireNoSessionFolder(sessionId);
            try {
                Path workingDirectory = prepareSessionFiles(agent, attributes);
                agentSessionWsClientRegistry.openConnection(sessionId, agent, attributes.actorAccountId());
                slot.start(agent, workingDirectory, new ClaudeSessionOptions(
                        attributes.loggingLevel() == AgentSessionLoggingLevel.DEBUG, attributes.actorAccountId(), attributes),
                        AgentContextPrompt.render(attributes.agentContext()).orElse(null));
            } catch (RuntimeException e) {
                // the folder did not exist before and the slot holds the id, so it belongs to this failed setup
                FileTrees.deleteIfExists(sessionFolders.of(sessionId));
                agentSessionWsClientRegistry.removeConnection(sessionId);
                throw e;
            }
        }
    }

    // the agent's files are copied into the session from the comagents sources readConfig downloads, into a temporary
    // folder per setup, so concurrent setups never share or delete each other's
    private Path prepareSessionFiles(AiAgent agent, AgentSessionAttributes attributes) {
        Path agentFolder = sessionFolders.agentFolder(attributes.sessionId(), agent);
        Path comAgentsSources = createTemporaryFolder();
        try {
            ClaudeAgentConfig config = readConfig(comAgentsSources, agent);
            agentContextValidator.validate(agent, attributes.agentContext(), config.requiredItems());
            List<AgentCallToolDefinition> agentTools = agentTools(config);
            Path workingDirectory = prepareWorkingDirectory(attributes, agentFolder);
            AgentFiles.copyInto(workingDirectory, comAgentsSources, agent, CLAUDE_CODE_FILES);
            AgentFiles.copyInto(agentFolder, comAgentsSources, agent, AGENT_FOLDER_FILES);
            agentCallTools.add(agentFolder, agentTools);
            return workingDirectory;
        } finally {
            FileTrees.deleteIfExists(comAgentsSources);
        }
    }

    private static Path createTemporaryFolder() {
        try {
            return Files.createTempDirectory("comagents-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create a temporary folder", e);
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

    // the agents the agent calls as tools of agent-proxy's MCP server, as their agent.json exposes them
    private List<AgentCallToolDefinition> agentTools(ClaudeAgentConfig config) {
        List<AiAgent> calledAgents = AgentProxyMcpServer.calledAgents(config.allowedTools());
        Map<AiAgent, ClaudeAgentConfig> configs = agentDefinitionService.getAgentConfig(calledAgents);
        return calledAgents.stream()
                .map(called -> AgentCallToolDefinition.of(called, configs.get(called)))
                .toList();
    }

    // downloads the agent's folder of the comagents sources first
    private ClaudeAgentConfig readConfig(Path comAgentsSources, AiAgent agent) {
        vcsService.downloadComAgentsFolder(comAgentsSources, AgentFiles.sourcesFolder(agent));
        Path configFile = AgentFiles.agentConfigFile(comAgentsSources, agent);
        try {
            return JsonUtility.fromJson(Files.readAllBytes(configFile), ClaudeAgentConfig.class);
        } catch (IOException | SerializationException e) {
            throw new IllegalStateException("Agent " + agent.getAgentName() + " has no readable " + configFile, e);
        }
    }

    // Claude Code runs in the context's repository to clone, or in the agent folder when there is none
    private Path prepareWorkingDirectory(AgentSessionAttributes attributes, Path agentFolder) {
        Optional<String> cloneSourceUrl = Optional.ofNullable(attributes.agentContext())
                .flatMap(AgentContext::repositoryToClone)
                .map(AgentContextItem::getValue);
        if (cloneSourceUrl.isPresent()) {
            return vcsService.clone(cloneSourceUrl.get(), sessionFolders.of(attributes.sessionId()));
        }
        return agentFolder;
    }

    @Override
    public Optional<AgentSessionStatus> status(UUID sessionId) {
        return claudeAgentSessionRegistry.findSession(sessionId)
                .map(session -> new AgentSessionStatus(session.getState(), session.getStartedAt(), session.getFinishedAt()));
    }

    @Override
    public Optional<AgentSessionAttributes> attributes(UUID sessionId) {
        return claudeAgentSessionRegistry.findSession(sessionId)
                .map(session -> session.getOptions().attributes());
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
