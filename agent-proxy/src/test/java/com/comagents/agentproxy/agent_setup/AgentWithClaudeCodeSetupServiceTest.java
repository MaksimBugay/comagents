package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionExistsException;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionLimitException;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.AiAgentAttributes;
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
import java.util.UUID;

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
                Duration.ofMinutes(1), sessionFolders, wsClientRegistry);
        setupService = new AgentWithClaudeCodeSetupService(
                new VcsGithubService(new AgentProxyConf("https://github.com/example/comagents.git", "token", "key",
                        "https://pushca.example", "pushca-key"), null),
                registry, sessionFolders, wsClientRegistry);
    }

    @Test
    void failedSetupFreesSlotAndDeletesSessionFolder() {
        // not a GitHub URL: the clone step fails after the slot was reserved
        assertThrows(IllegalArgumentException.class,
                () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, "https://gitlab.com/a/b", "Maks", null)));

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
                () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, "https://gitlab.com/a/b", "Maks", null)));

        assertTrue(e.getMessage().contains(sessionId.toString()), e.getMessage());
        assertTrue(Files.exists(earlierSessionsFile), "the earlier session's data is not deleted");
        assertSlotFreed();
    }

    @Test
    void busyServiceRejectsBeforeAnySetupWork() {
        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(UUID.randomUUID())) {
            assertThrows(ClaudeSessionLimitException.class,
                    () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, "https://github.com/example/repo.git", "Maks", null)));
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

    private void assertSlotFreed() {
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(UUID.randomUUID())) {
            assertNotNull(slot, "the rejected or failed setup freed its slot");
        }
    }
}
