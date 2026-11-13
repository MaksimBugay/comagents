package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.FakePushcaConnector.FakeConnection;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionArchive;
import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Archived sessions found on disk after a restart get their chats back; nothing reaches a real Pushca server. */
class ArchivedSessionChatsTest {

    @TempDir
    Path sessionsRoot;

    private SessionFolders sessionFolders;
    private final FakePushcaConnector connector = new FakePushcaConnector();
    private final RecordingChatListener chatListener = new RecordingChatListener();
    private ArchivedSessionChats archivedSessionChats;

    @BeforeEach
    void createServices() {
        sessionFolders = new SessionFolders(sessionsRoot.toString());
        AgentSessionWsClientRegistry wsClientRegistry = new AgentSessionWsClientRegistry(connector, chatListener,
                "https://chat.example/chat-room.html");
        ClaudeAgentSessionRegistry claudeRegistry = new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), "false", 2,
                Duration.ofMinutes(1), Duration.ofMinutes(1), sessionFolders, wsClientRegistry,
                new AgentProxyMcpServer(ObjectMapper.getDefault(), "http://127.0.0.1:8080/mcp", Duration.ofMinutes(4)));
        archivedSessionChats = new ArchivedSessionChats(claudeRegistry, wsClientRegistry);
    }

    @Test
    void reopensTheExistingChatOfEachArchivedSession() throws IOException {
        UUID archived = UUID.randomUUID();
        writeArchive(archived);
        // a session that was not archived (e.g. running when agent-proxy stopped) gets no chat back
        Files.createDirectories(sessionFolders.agentFolder(UUID.randomUUID(), AiAgent.CODE_REVIEW));

        assertEquals(1, archivedSessionChats.reopenAll());

        assertEquals(1, connector.connections.size());
        FakeConnection connection = connector.connections.getFirst();
        assertEquals(archived.toString(), connection.client.deviceId);
        assertTrue(connection.createdChannels.isEmpty(), "the chat exists already");
        connection.receive("code_review_session_" + archived, "MBugai", "answer while archived");
        assertEquals(List.of(new RecordingChatListener.ChatMessage(archived, AiAgent.CODE_REVIEW, "MBugai",
                "answer while archived")), chatListener.messages);
    }

    @Test
    void sessionsStayArchivedWhenPushcaIsUnavailable() throws IOException {
        writeArchive(UUID.randomUUID());
        connector.connectFailure = new IllegalStateException("Cannot sign in to Pushca");

        assertEquals(0, archivedSessionChats.reopenAll());
    }

    // archived by actor MBugai
    private void writeArchive(UUID sessionId) throws IOException {
        Path agentFolder = Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        Instant now = Instant.now();
        ClaudeSessionArchive archive = new ClaudeSessionArchive(sessionId, AiAgent.CODE_REVIEW, agentFolder.toString(),
                AgentSessionState.WAITING, now, now, now, null, false, "MBugai");
        Files.write(agentFolder.resolve(ClaudeSessionArchive.STATE_FILE), ObjectMapper.getDefault().writeValueAsBytes(archive));
    }
}
