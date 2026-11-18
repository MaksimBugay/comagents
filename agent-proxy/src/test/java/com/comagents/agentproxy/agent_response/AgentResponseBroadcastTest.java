package com.comagents.agentproxy.agent_response;

import bmv.org.pushca.client.exception.PushcaCommandException;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionOptions;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentproxy.domain.AgentResponseReady;
import com.comagents.agentproxy.domain.AgentSessionAttributes;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "false" stands in for Claude Code: it exits with an error at once, so a started session ends FAILED without a
// response; the tests write the response themselves
@MicronautTest
@Property(name = "agent-response.cleanup-delay", value = "1s")
@Property(name = "claude.executable", value = "false")
@Property(name = "agent-proxy.device-id", value = "executor-device-1")
class AgentResponseBroadcastTest {

    private static final AgentContext ALL_GOOD = new AgentContext(List.of(new StringAgentContextItem("response", "# Report\n\nAll good")));

    private final UUID sessionId = UUID.randomUUID();
    private final AgentSessionAttributes attributes = new AgentSessionAttributes(sessionId,
            new AgentContext(List.of(new StringAgentContextItem("focus", "security"))), "sequence-1", "orchestrator-device-1", "MBugai",
            AgentSessionLoggingLevel.INFO, 1_700_000_000_000L);

    @Inject
    AgentResponseBroadcast broadcast;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @Inject
    SessionFolders sessionFolders;

    // the context's Pushca: records what was sent
    @Inject
    FakePushcaConnectionFactory connectionFactory;

    // the executor's connection, opened on startup, stays; what earlier tests sent is forgotten
    @BeforeEach
    void resetPushca() {
        connectionFactory.reset();
    }

    @AfterEach
    void deleteSessionFolder() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void sendsTheResponseToTheOrchestratorDeviceAndDeletesSessionFilesAfterDelay() throws Exception {
        startSession();
        writeAllGoodResponse();

        broadcast.send(new AgentResponseReady(AiAgent.CODE_REVIEW, sessionId));

        // from the executor's own client, not the session's chat client
        assertEquals(1, connectionFactory.connections.size());
        FakePushcaConnectionFactory.FakeConnection executor = connectionFactory.connections.getFirst();
        assertEquals("comagents-main", executor.client.workSpaceId);
        assertEquals("executor-device-1", executor.client.deviceId);
        assertEquals("CLAUDE_CODE_EXECUTOR", executor.client.applicationId);
        List<FakePushcaConnectionFactory.Broadcast> broadcasts = executor.broadcasts;
        assertEquals(1, broadcasts.size());
        assertEquals("comagents-main", broadcasts.getFirst().receivers().workSpaceId);
        assertEquals("agent-orchestrator@comagents.com", broadcasts.getFirst().receivers().accountId);
        assertEquals("orchestrator-device-1", broadcasts.getFirst().receivers().deviceId);
        assertEquals("AGENT_ORCHESTRATOR", broadcasts.getFirst().receivers().applicationId);
        // a ComagentsMessage, read as the orchestrator reads it
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(broadcasts.getFirst().message());
        assertEquals("sequence-1", message.id(), "the start request's sequence id");
        assertEquals(AgentSessionResponse.class.getName(), message.payloadClassName());
        AgentSessionResponse response = (AgentSessionResponse) message.comagentsMessagePayload().payload();
        assertEquals(new StartAgentSessionRequest(sessionId, AiAgent.CODE_REVIEW, attributes.agentContext(), "sequence-1", "orchestrator-device-1", "MBugai",
                AgentSessionLoggingLevel.INFO, 1_700_000_000_000L), response.request());
        assertEquals(ALL_GOOD, response.agentContext());

        await("session files deleted after the cleanup delay").atMost(Duration.ofSeconds(10))
                .until(() -> !Files.exists(sessionFolders.of(sessionId)));
    }

    @Test
    void responseThatCannotBeSentStaysToBeFetched() throws Exception {
        startSession();
        writeAllGoodResponse();
        FakePushcaConnectionFactory.FakeConnection executor = connectionFactory.connections.getFirst();
        // refused, as by Pushca: not retried
        executor.broadcastFailure = new PushcaCommandException("Send message", "no such receiver");

        broadcast.send(new AgentResponseReady(AiAgent.CODE_REVIEW, sessionId));

        assertTrue(executor.broadcasts.isEmpty());
        // the cleanup delay (1 s) passes several times; nothing was scheduled
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> Files.exists(sessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW)));
    }

    private void startSession() throws Exception {
        Path agentFolder = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            slot.start(AiAgent.CODE_REVIEW, agentFolder, new ClaudeSessionOptions(false, "MBugai", attributes))
                    .awaitFinished(Duration.ofSeconds(20));
        }
    }

    private void writeAllGoodResponse() throws Exception {
        Path responseFile = sessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW);
        Files.write(responseFile, JsonUtility.toJsonAsBytes(ALL_GOOD));
    }
}
