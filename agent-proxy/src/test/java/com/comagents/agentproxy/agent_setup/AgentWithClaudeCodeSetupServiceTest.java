package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionLimitException;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.AgentContextItem;
import com.comagents.agentproxy.domain.agent_context.AgentContextItemAttributes;
import com.comagents.agentproxy.domain.agent_context.AgentContextItemType;
import com.comagents.agentproxy.domain.agent_context.AgentContextValidator;
import com.comagents.agentproxy.domain.agent_context.InvalidAgentContextException;
import com.comagents.agentproxy.vcs.VcsGithubService;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The slot reservation around setup; nothing here reaches GitHub or Claude Code. */
class AgentWithClaudeCodeSetupServiceTest {

    private final UUID sessionId = UUID.randomUUID();

    @TempDir
    Path sessionsRoot;

    private SessionFolders sessionFolders;
    private ClaudeAgentSessionRegistry registry;
    private AgentWithClaudeCodeSetupService setupService;

    @BeforeEach
    void createServices() {
        sessionFolders = new SessionFolders(sessionsRoot.toString());
        // one slot, so a freed or held slot is observable with a second reservation
        AgentSessionWsClientRegistry wsClientRegistry = new AgentSessionWsClientRegistry(new FakePushcaConnector(),
                new RecordingChatListener(),
                "https://chat.example/chat-room.html");
        registry = new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), "false", 1, Duration.ofMinutes(1),
                Duration.ofMinutes(1), sessionFolders, wsClientRegistry,
                new AgentProxyMcpServer(ObjectMapper.getDefault(), "http://127.0.0.1:8080/mcp", Duration.ofMinutes(4)));
        setupService = new AgentWithClaudeCodeSetupService(
                // not a GitHub URL: downloading the agent's files, the first setup step, fails without network access
                new VcsGithubService(new AgentProxyConf("https://gitlab.com/example/comagents.git", "token", "key",
                        "https://pushca.example", "pushca-key"), null),
                registry, sessionFolders, wsClientRegistry, new AgentContextValidator(), ObjectMapper.getDefault());
    }

    @Test
    void failedSetupFreesSlotAndDeletesSessionFolder() {
        // the download of the agent's files fails after the slot was reserved
        assertThrows(IllegalArgumentException.class,
                () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, repository("https://gitlab.com/a/b"), "Maks", null)));

        assertFalse(Files.exists(sessionFolders.of(sessionId)));
        assertSlotFreed();
    }

    @Test
    void sessionIdWithDataOnDiskIsRejectedAndItsDataKept() throws IOException {
        // e.g. an archived session, or any session from before a restart: on disk, but not in the registry
        Path earlierSessionsFile = Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW))
                .resolve("session-state.json");
        Files.writeString(earlierSessionsFile, "{}");

        ClaudeSessionExistsException e = assertThrows(ClaudeSessionExistsException.class,
                () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, repository("https://gitlab.com/a/b"), "Maks", null)));

        assertTrue(e.getMessage().contains(sessionId.toString()), e.getMessage());
        assertTrue(Files.exists(earlierSessionsFile), "the earlier session's data is not deleted");
        assertSlotFreed();
    }

    @Test
    void busyServiceRejectsBeforeAnySetupWork() {
        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(UUID.randomUUID())) {
            assertThrows(ClaudeSessionLimitException.class,
                    () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, repository("https://github.com/example/repo.git"), "Maks", null)));
        }

        assertFalse(Files.exists(sessionFolders.of(sessionId)), "nothing was downloaded or created");
    }

    @Test
    void sessionIdInUseIsRejectedWithoutTouchingItsFolder() throws IOException {
        Path otherSetupsFile = Files.createDirectories(sessionFolders.of(sessionId)).resolve("in-progress.txt");
        Files.writeString(otherSetupsFile, "another setup owns this session id");

        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(sessionId)) {
            assertThrows(ClaudeSessionExistsException.class,
                    () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, null, "Maks", null)));
        }

        assertTrue(Files.exists(otherSetupsFile), "the folder belongs to the setup that holds the id");
    }

    @Test
    void primaryRepositoryNameMustMatchItsCloneUrl() {
        assertDoesNotThrow(() -> AgentWithClaudeCodeSetupService.requireNameMatchesCloneUrl(
                primaryKnChallenge("https://github.com/acme/kn-challenge.git")));

        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class,
                () -> AgentWithClaudeCodeSetupService.requireNameMatchesCloneUrl(
                        primaryKnChallenge("https://github.com/acme/other-repo.git")));
        assertEquals("Repository item kn-challenge has the clone URL of repository other-repo", e.getMessage());
    }

    private static AgentContextItem primaryKnChallenge(String cloneUrl) {
        return new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, "kn-challenge", true), cloneUrl);
    }

    private void assertSlotFreed() {
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(UUID.randomUUID())) {
            assertNotNull(slot, "the rejected or failed setup freed its slot");
        }
    }

    private static AgentContext repository(String cloneSourceUrl) {
        AgentContextItem sources = new AgentContextItem(
                new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, "sources", true), cloneSourceUrl);
        return new AgentContext(Set.of(sources));
    }
}
