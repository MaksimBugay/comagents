package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_setup.AgentSessionWsClientRegistry;
import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.agent_setup.ExpertAnswers;
import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSession;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Asks through a running fake {@code claude} process and a fake Pushca: no test reaches Claude or Pushca. */
class AskExpertToolTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    // records its arguments (the --mcp-config with the run's token), then works until stopped
    private static final String RUNNING_CLAUDE = """
            #!/bin/sh
            printf '%s\\n' "$@" > "$(dirname "$0")/args.txt"
            exec sleep 60
            """;

    @TempDir
    Path scripts;

    @TempDir
    Path workingDirectory;

    @TempDir
    Path sessionsRoot;

    private final UUID sessionId = UUID.randomUUID();
    private final String chat = "code_review_session_" + sessionId;
    private final FakePushcaConnector connector = new FakePushcaConnector();
    private final ExpertAnswers answers = new ExpertAnswers();
    private AgentSessionWsClientRegistry chats;
    private ClaudeAgentSessionRegistry sessions;
    private ClaudeAgentSession session;

    @BeforeEach
    void startSession() throws IOException {
        SessionFolders sessionFolders = new SessionFolders(sessionsRoot.toString());
        Path agentFolder = Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        Path claude = scripts.resolve("claude");
        Files.writeString(claude, RUNNING_CLAUDE);
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));

        chats = new AgentSessionWsClientRegistry(connector, answers, "https://chat.example/chat-room.html");
        sessions = new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), claude.toString(), 1, Duration.ofMinutes(1),
                Duration.ofMinutes(1), sessionFolders, chats,
                new AgentProxyMcpServer(ObjectMapper.getDefault(), "http://127.0.0.1:8080/mcp", Duration.ofMinutes(4)));
        session = sessions.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        await().atMost(WAIT).until(() -> Files.exists(scripts.resolve("args.txt")));
    }

    @AfterEach
    void cleanUp() {
        sessions.cleanAllSessionData(sessionId);
    }

    @Test
    void postsTheQuestionAndReturnsTheAnswer() throws Exception {
        chats.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");
        AskExpertTool tool = tool(Duration.ofSeconds(30));

        CompletableFuture<String> result = CompletableFuture.supplyAsync(
                () -> text(tool.askExpert("Which branch should I review?", runContext())));

        FakePushcaConnector.FakeConnection connection = connector.connections.getFirst();
        await().atMost(WAIT).until(() -> connection.messages.contains("Question: Which branch should I review?"));
        assertEquals(AgentSessionState.WAITING, session.getState());
        connection.receive(chat, "Maks", "main, please");

        assertEquals("main, please", result.get());
        assertEquals(AgentSessionState.RUNNING, session.getState());
    }

    @Test
    void goesOnWithoutAnswerAfterTheTimeout() {
        chats.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        String result = text(tool(Duration.ofMillis(300)).askExpert("Which branch?", runContext()));

        assertTrue(result.startsWith("The expert did not answer within 300 ms."), result);
        FakePushcaConnector.FakeConnection connection = connector.connections.getFirst();
        await().atMost(WAIT).until(() -> connection.messages.size() == 2);
        assertEquals("No answer within 300 ms: the agent goes on without it.", connection.messages.getLast());
        assertEquals(AgentSessionState.RUNNING, session.getState());
        // a late reply answers nothing: the next question waits for its own answer
        connection.receive(chat, "Maks", "main");
        assertFalse(answers.expect(sessionId).isDone());
    }

    @Test
    void goesOnWithoutAnswerWhenTheSessionHasNoChat() {
        assertEquals(AskExpertTool.NO_CHAT, text(tool(Duration.ofSeconds(30)).askExpert("Which branch?", runContext())));
        assertEquals(AgentSessionState.RUNNING, session.getState());
    }

    @Test
    void onlyTheRunItselfCanAsk() {
        AskExpertTool tool = tool(Duration.ofSeconds(30));

        for (McpTransportContext context : List.of(context(sessionId.toString(), "guessed"),
                context("not-a-uuid", runToken()), McpTransportContext.EMPTY)) {
            CallToolResult result = tool.askExpert("Which branch?", context);
            assertTrue(result.isError());
            assertEquals("Unknown session or wrong session token", text(result));
        }
        assertTrue(connector.connections.isEmpty(), "nothing was asked");
    }

    @Test
    void oneQuestionAtATime() {
        chats.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");
        answers.expect(sessionId);

        CallToolResult result = tool(Duration.ofSeconds(30)).askExpert("Which branch?", runContext());

        assertTrue(result.isError());
        assertEquals("Session " + sessionId + " already waits for an answer", text(result));
    }

    @Test
    void describesTimeouts() {
        assertEquals("4 min", AskExpertTool.describe(Duration.ofMinutes(4)));
        assertEquals("90 s", AskExpertTool.describe(Duration.ofSeconds(90)));
        assertEquals("300 ms", AskExpertTool.describe(Duration.ofMillis(300)));
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    private AskExpertTool tool(Duration answerTimeout) {
        return new AskExpertTool(sessions, chats, answers, answerTimeout);
    }

    // the headers Claude Code sends, as the run's --mcp-config sets them
    private McpTransportContext runContext() {
        return context(sessionId.toString(), runToken());
    }

    private String runToken() {
        try {
            return path(mcpServer(Files.readAllLines(scripts.resolve("args.txt"))), "headers", "X-Agent-Session-Token")
                    .getStringValue();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // field.field...: fails the test when one is missing
    private static JsonNode path(JsonNode node, String... fields) {
        JsonNode current = node;
        for (String field : fields) {
            current = Objects.requireNonNull(current.get(field), field);
        }
        return current;
    }

    // the --mcp-config the fake claude was started with
    private static JsonNode mcpServer(List<String> args) throws IOException {
        JsonNode config = ObjectMapper.getDefault().readValue(args.get(args.indexOf("--mcp-config") + 1), JsonNode.class);
        return path(Objects.requireNonNull(config, "--mcp-config"), "mcpServers", "agentproxy");
    }

    private static McpTransportContext context(String sessionId, String token) {
        return McpTransportContext.create(Map.of(AgentProxyMcpServer.SESSION_ID_HEADER, sessionId,
                AgentProxyMcpServer.SESSION_TOKEN_HEADER, token));
    }
}
