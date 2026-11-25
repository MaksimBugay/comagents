package com.comagents.agentproxy.mcp;

import com.comagents.agentproxy.agent_call.AgentCallResponses;
import com.comagents.agentproxy.agent_call.AgentCallToolDefinition;
import com.comagents.agentproxy.agent_call.AgentOrchestratorClient;
import com.comagents.agentproxy.agent_setup.AgentSessionWsClientRegistry;
import com.comagents.agentproxy.agent_setup.ExpertAnswers;
import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSession;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionOptions;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionExternalRequest;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.response.StartAgentSessionExternalResponse;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Calls agents through a running fake {@code claude} process and a fake orchestrator: no test reaches Claude or Pushca. */
class AgentCallToolTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    // first run: records its arguments (the --mcp-config with the run's token), then works until the test creates
    // "end-turn"; resumed run (--resume): records its arguments and gives the final answer
    private static final String CLAUDE = """
            #!/bin/sh
            case " $* " in
              *" --resume "*)
                printf '%s\\n' "$@" > "$(dirname "$0")/resume-args.txt"
                printf '%s\\n' '{"type":"result","subtype":"success","is_error":false,"result":"review done"}' ;;
              *)
                printf '%s\\n' "$@" > "$(dirname "$0")/args.txt"
                while [ ! -f "$(dirname "$0")/end-turn" ]; do sleep 0.05; done
                printf '%s\\n' '{"type":"result","subtype":"success","is_error":false,"result":"waiting"}' ;;
            esac
            """;

    private static final Map<String, Object> INTERVIEW_REQUEST = Map.of("items", List.of(
            Map.of("type", "STRING", "name", "company", "value", "Wise"),
            Map.of("type", "STRING", "name", "position", "value", "Senior Java Engineer")));

    private static final AgentCallToolDefinition INTERVIEW_INSIGHTS_TOOL = definition(AiAgent.INTERVIEW_INSIGHTS);

    private static final AgentContext INSIGHTS = new AgentContext(List.of(new StringAgentContextItem("insights", "Expect system design")));

    @TempDir
    Path scripts;

    @TempDir
    Path workingDirectory;

    @TempDir
    Path sessionsRoot;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID calledSessionId = UUID.randomUUID();
    private final AgentOrchestratorClient orchestrator = mock(AgentOrchestratorClient.class);
    // valid unless a test says otherwise; AgentCallToolsTest checks the server's own validator
    private JsonSchemaValidator schemaValidator = (_, content) -> ValidationResponse.asValid(JsonUtility.toJson(content));
    private SessionFolders sessionFolders;
    private ClaudeAgentSessionRegistry sessions;
    private AgentCallResponses responses;
    private ClaudeAgentSession session;

    @BeforeEach
    void startSession() throws IOException {
        sessionFolders = new SessionFolders(sessionsRoot.toString());
        Path agentFolder = Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        Path claude = scripts.resolve("claude");
        Files.writeString(claude, CLAUDE);
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));

        AgentSessionWsClientRegistry chats = new AgentSessionWsClientRegistry(new FakePushcaConnectionFactory(),
                new ExpertAnswers(() -> this.sessions), "https://chat.example/chat-room.html");
        sessions = new ClaudeAgentSessionRegistry(claude.toString(), 1, Duration.ofMinutes(1),
                Duration.ofMinutes(1), sessionFolders, chats,
                new AgentProxyMcpServer("http://127.0.0.1:8080/mcp", Duration.ofMinutes(4)),
                ApplicationEventPublisher.noOp());
        responses = new AgentCallResponses(() -> this.sessions);
        // started by the weather_forecast agent: the call chain is weather_forecast -> code_review
        AgentSessionAttributes attributes = new AgentSessionAttributes(sessionId, AiAgent.CODE_REVIEW, null,
                "sequence-1", "orchestrator-device-1", "waiter-device-1", "executor-device-1",
                List.of("weather_forecast", "code_review"), "Maks", AgentSessionLoggingLevel.DEBUG, 1L);
        try (ClaudeAgentSessionRegistry.SessionSlot slot = sessions.reserve(sessionId)) {
            session = slot.start(AiAgent.CODE_REVIEW, workingDirectory, new ClaudeSessionOptions(true, "Maks", attributes));
        }
        await().atMost(WAIT).until(() -> Files.exists(scripts.resolve("args.txt")));
        when(orchestrator.start(any())).thenReturn(new StartAgentSessionExternalResponse(calledSessionId));
    }

    @AfterEach
    void cleanUp() {
        sessions.cleanAllSessionData(sessionId);
    }

    @Test
    void startsTheCalledAgentThroughTheOrchestratorAndReturnsItsResponse() throws Exception {
        CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> text(
                tool(Duration.ofSeconds(30)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext())));

        ArgumentCaptor<StartAgentSessionExternalRequest> started = ArgumentCaptor.forClass(StartAgentSessionExternalRequest.class);
        verify(orchestrator, timeout(WAIT.toMillis())).start(started.capture());
        assertEquals(AiAgent.INTERVIEW_INSIGHTS, started.getValue().agent());
        assertEquals(JsonUtility.convertToObjectFromMap(INTERVIEW_REQUEST, AgentContext.class), started.getValue().agentContext());
        assertEquals(List.of("weather_forecast", "code_review"), started.getValue().agentCallSequence(),
                "the caller's chain: the orchestrator appends the called agent");
        assertEquals("executor-device-1", started.getValue().agentResponseWaiterDeviceId(), "this executor waits for it");
        assertEquals("Maks", started.getValue().actorAccountId());
        assertEquals(AgentSessionLoggingLevel.DEBUG, started.getValue().loggingLevel());
        await().atMost(WAIT).until(() -> session.getState() == AgentSessionState.WAITING);

        responses.onResponse(response());

        assertEquals(JsonUtility.toJson(INSIGHTS), result.get());
        assertEquals(AgentSessionState.RUNNING, session.getState());
    }

    @Test
    void aResponseBeforeTheCallWaitsIsKeptForIt() {
        responses.onResponse(response());

        CallToolResult result = tool(Duration.ofSeconds(30)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext());

        assertFalse(result.isError());
        assertEquals(JsonUtility.toJson(INSIGHTS), text(result));
    }

    @Test
    void withoutResponseInTimeTheSessionIsArchivedAndTheResponseResumesIt() throws IOException {
        String result = text(tool(Duration.ofMillis(300)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext()));

        assertEquals(AgentCallTool.endTurn(AiAgent.INTERVIEW_INSIGHTS), result);
        assertTrue(result.startsWith(AgentProxyMcpServer.NO_ANSWER_YET));
        // the agent ends its turn as told
        Files.createFile(scripts.resolve("end-turn"));
        await().atMost(WAIT).until(() -> session.getState() == AgentSessionState.ARCHIVED);

        responses.onResponse(response());

        await().atMost(WAIT).until(() -> Files.exists(scripts.resolve("resume-args.txt")));
        List<String> args = Files.readAllLines(scripts.resolve("resume-args.txt"));
        assertEquals(List.of("-p", "The interview_insights agent you called responded: " + JsonUtility.toJson(INSIGHTS),
                "Continue the task and return your final answer as before.", "--resume", sessionId.toString()),
                args.subList(0, 5));
    }

    @Test
    void aResponseWhileAllSlotsAreBusyIsStoredAndResumesTheSessionWhenOneIsFree() throws IOException {
        tool(Duration.ofMillis(300)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext());
        Files.createFile(scripts.resolve("end-turn"));
        await().atMost(WAIT).until(() -> session.getState() == AgentSessionState.ARCHIVED);
        Path storedResponse = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW)
                .resolve(ClaudeAgentSessionRegistry.AGENT_CALL_RESPONSE_FILE);

        // the only slot is taken
        try (ClaudeAgentSessionRegistry.SessionSlot _ = sessions.reserve(UUID.randomUUID())) {
            responses.onResponse(response());

            assertEquals(JsonUtility.toJson(INSIGHTS), Files.readString(storedResponse));
            assertFalse(Files.exists(scripts.resolve("resume-args.txt")));
        }
        // another run frees the slot when it ends (at once: "end-turn" exists)
        UUID otherSessionId = UUID.randomUUID();
        Path otherAgentFolder = Files.createDirectories(sessionFolders.agentFolder(otherSessionId, AiAgent.CODE_REVIEW));
        Files.writeString(otherAgentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        try {
            sessions.createSession(otherSessionId, AiAgent.CODE_REVIEW, otherAgentFolder);

            await().atMost(WAIT).until(() -> Files.exists(scripts.resolve("resume-args.txt")));
            assertEquals("The interview_insights agent you called responded: " + JsonUtility.toJson(INSIGHTS),
                    Files.readAllLines(scripts.resolve("resume-args.txt")).get(1));
            assertFalse(Files.exists(storedResponse), "used up");
        } finally {
            sessions.cleanAllSessionData(otherSessionId);
        }
    }

    @Test
    void callsMustNotLoop() {
        for (AiAgent calledAgain : List.of(AiAgent.WEATHER_FORECAST, AiAgent.CODE_REVIEW)) {
            CallToolResult result = tool(Duration.ofSeconds(30)).call(definition(calledAgain), Map.of(), runContext());

            assertTrue(result.isError());
            assertEquals("Calling the " + calledAgain.getAgentName() + " agent would make a loop: it is already in "
                    + "the call chain weather_forecast -> code_review", text(result));
        }
        verifyNoInteractions(orchestrator);
    }

    @Test
    void aBusyAgentIsAnErrorResultToCallAgainLater() {
        when(orchestrator.start(any())).thenThrow(new HttpClientResponseException("No CLAUDE_CODE executor with a free slot",
                HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)));

        CallToolResult result = tool(Duration.ofSeconds(30)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext());

        assertTrue(result.isError());
        assertEquals("The interview_insights agent is busy: all its sessions are taken (No CLAUDE_CODE executor with a "
                + "free slot). It was not called. Call it again later, or continue without it and say in your final "
                + "answer what is missing because of that.", text(result));
        assertEquals(AgentSessionState.RUNNING, session.getState());
    }

    @Test
    void aRefusedStartIsAnErrorResult() {
        when(orchestrator.start(any())).thenThrow(new HttpClientResponseException("Unknown item type",
                HttpResponse.status(HttpStatus.BAD_REQUEST)));

        CallToolResult result = tool(Duration.ofSeconds(30)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext());

        assertTrue(result.isError());
        assertEquals("The orchestrator did not start the interview_insights agent (400): Unknown item type", text(result));
    }

    @Test
    void onlyTheRunItselfCanCall() {
        CallToolResult result = tool(Duration.ofSeconds(30)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST,
                McpTransportContext.create(Map.of(AgentProxyMcpServer.SESSION_ID_HEADER, sessionId.toString(),
                        AgentProxyMcpServer.SESSION_TOKEN_HEADER, "guessed")));

        assertTrue(result.isError());
        assertEquals("Unknown session or wrong session token", text(result));
        verifyNoInteractions(orchestrator);
    }

    @Test
    void aResponseNotMatchingTheResponseSchemaIsAnErrorResult() {
        schemaValidator = (_, _) -> ValidationResponse.asInvalid("$.items[0].name: does not have the value \"insights\"");
        responses.onResponse(response());

        CallToolResult result = tool(Duration.ofSeconds(30)).call(INTERVIEW_INSIGHTS_TOOL, INTERVIEW_REQUEST, runContext());

        assertTrue(result.isError());
        assertEquals("The response of the interview_insights agent does not match its response schema ($.items[0].name: "
                + "does not have the value \"insights\"): " + JsonUtility.toJson(INSIGHTS), text(result));
    }

    // as the comagents sources define the agent
    private static AgentCallToolDefinition definition(AiAgent agent) {
        try {
            return AgentCallToolDefinition.of(agent, JsonUtility.fromJson(Files.readAllBytes(Path.of("../agent/claude_code")
                    .resolve(agent.getAgentName()).resolve(ClaudeAgentConfig.FILE_NAME)), ClaudeAgentConfig.class));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private AgentSessionResponse response() {
        StartAgentSessionRequest request = new StartAgentSessionRequest(calledSessionId, AiAgent.INTERVIEW_INSIGHTS, null,
                "sequence-2", "orchestrator-device-1", "executor-device-1", List.of(), "Maks",
                AgentSessionLoggingLevel.INFO, 1L);
        return new AgentSessionResponse(request, INSIGHTS, 2L, "executor-device-2");
    }

    private AgentCallTool tool(Duration answerTimeout) {
        AgentProxyConf conf = new AgentProxyConf("0.1", "https://github.com/example/comagents.git", "token", "key",
                "https://pushca.example", "pushca-key", AiHarnessProvider.CLAUDE_CODE,
                new DeviceIdExtractor("executor-device-1", "host"));
        return new AgentCallTool(sessions, responses, orchestrator, conf, schemaValidator, answerTimeout);
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    // the headers Claude Code sends, as the run's --mcp-config sets them
    private McpTransportContext runContext() {
        return McpTransportContext.create(Map.of(AgentProxyMcpServer.SESSION_ID_HEADER, sessionId.toString(),
                AgentProxyMcpServer.SESSION_TOKEN_HEADER, runToken()));
    }

    private String runToken() {
        try {
            List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
            String mcpConfig = args.get(args.indexOf("--mcp-config") + 1);
            return JsonUtility.getSimpleMapper().readTree(mcpConfig).path("mcpServers").path(AgentProxyMcpServer.NAME)
                    .path("headers").path(AgentProxyMcpServer.SESSION_TOKEN_HEADER).stringValue();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
