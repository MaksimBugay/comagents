package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
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
        registry = new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), "false", 1, Duration.ofMinutes(1),
                Duration.ofMinutes(1), sessionFolders);
        setupService = new AgentWithClaudeCodeSetupService(
                new VcsGithubService(new AgentProxyConf("https://github.com/example/comagents.git", "token", "key"), null),
                registry, sessionFolders);
    }

    @Test
    void failedSetupFreesSlotAndDeletesSessionFolder() throws IOException {
        Path leftover = Files.createDirectories(sessionFolders.of(sessionId)).resolve("partial-clone.txt");
        Files.writeString(leftover, "half-prepared");

        // not a GitHub URL: the clone step fails after the slot was reserved
        assertThrows(IllegalArgumentException.class,
                () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, "https://gitlab.com/a/b")));

        assertFalse(Files.exists(sessionFolders.of(sessionId)));
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(UUID.randomUUID())) {
            assertNotNull(slot, "the failed setup freed its slot");
        }
    }

    @Test
    void busyServiceRejectsBeforeAnySetupWork() {
        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(UUID.randomUUID())) {
            assertThrows(ClaudeSessionLimitException.class,
                    () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, "https://github.com/example/repo.git")));
        }

        assertFalse(Files.exists(sessionFolders.of(sessionId)), "nothing was downloaded or created");
    }

    @Test
    void sessionIdInUseIsRejectedWithoutTouchingItsFolder() throws IOException {
        Path otherSetupsFile = Files.createDirectories(sessionFolders.of(sessionId)).resolve("in-progress.txt");
        Files.writeString(otherSetupsFile, "another setup owns this session id");

        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(sessionId)) {
            assertThrows(IllegalStateException.class,
                    () -> setupService.setup(AiAgent.CODE_REVIEW, new AiAgentAttributes(sessionId, null)));
        }

        assertTrue(Files.exists(otherSetupsFile), "the folder belongs to the setup that holds the id");
    }
}
