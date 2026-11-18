package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_response.AgentResponseUnavailableException;
import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.agent_setup.PushcaConnector;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentConfig;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionOptions;
import com.comagents.agentproxy.communication.pushca.AgentExecutorPushcaClient;
import com.comagents.agentproxy.domain.AgentSessionAttributes;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.response.AgentResponseReply;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
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
import static org.junit.jupiter.api.Assertions.assertNull;

// "false" stands in for Claude Code: it exits with an error at once, so a started session ends FAILED without a
// response; the tests write the response themselves
@MicronautTest
@Property(name = "agent-response.cleanup-delay", value = "1s")
@Property(name = "claude.executable", value = "false")
class AgentResponseListenerTest {

    private static final AgentContext ALL_GOOD =
            new AgentContext(List.of(new StringAgentContextItem("response", "All good")));

    private final UUID sessionId = UUID.randomUUID();
    // the start request was sequence-1; this response request is sequence-2
    private final AgentResponseRequest request = new AgentResponseRequest(AiAgent.CODE_REVIEW, sessionId, "sequence-2",
            "orchestrator-device-2");
    private final FakePushcaConnector connector = new FakePushcaConnector();

    @Inject
    AgentResponseListener listener;

    @Inject
    ClaudeAgentSessionRegistry registry;

    @Inject
    SessionFolders sessionFolders;

    @Inject
    AgentExecutorPushcaClient executorClient;

    @MockBean(PushcaConnector.class)
    PushcaConnector pushcaConnector() {
        return connector;
    }

    // the executor's client connected on startup, through another test's connector; closed, the delivery connects
    // through this test's connector
    @BeforeEach
    void closeExecutorClient() {
        executorClient.close();
    }

    @AfterEach
    void cleanUp() {
        registry.cleanAllSessionData(sessionId);
    }

    @Test
    void sendsTheResponseWithTheResponseRequestsSequenceIdAndDeletesSessionFilesAfterDelay() throws Exception {
        startSession();
        Files.write(sessionFolders.agentResponseFile(sessionId, AiAgent.CODE_REVIEW),
                JsonUtility.toJsonAsBytes(ALL_GOOD));

        listener.consumePayload("sequence-2", request);

        ComagentsMessage message = sentMessage();
        assertEquals("sequence-2", message.id());
        AgentResponseReply reply = (AgentResponseReply) message.comagentsMessagePayload().payload();
        assertEquals(ALL_GOOD, reply.response().agentContext());
        assertEquals("sequence-1", reply.response().sequenceId(), "the start request's, kept in the response");
        await("session files deleted after the cleanup delay, as after a fetch").atMost(Duration.ofSeconds(10))
                .until(() -> !Files.exists(sessionFolders.of(sessionId)));
    }

    @Test
    void sendsBackWhyThereIsNoResponse() throws Exception {
        startSession();

        listener.consumePayload("sequence-2", request);

        ComagentsMessage message = sentMessage();
        assertEquals("sequence-2", message.id());
        // in place of the AgentResponseReply
        assertEquals(AgentResponseReply.class.getName(), message.payloadClassName());
        assertNull(message.payloadString());
        assertEquals(AgentResponseUnavailableException.class.getName(), message.error().errorClassName());
        assertEquals("No response from agent code_review for session " + sessionId + ": session is FAILED",
                message.error().errorMessage());
    }

    // started by a sequence-1 request; "false" ends it FAILED at once, without a response
    private void startSession() throws Exception {
        Path agentFolder = sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW);
        Files.createDirectories(agentFolder);
        Files.writeString(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME), """
                {"kickOffPrompt": "Review it"}""");
        AgentSessionAttributes attributes = new AgentSessionAttributes(sessionId, null, "sequence-1",
                "orchestrator-device-1", "MBugai", AgentSessionLoggingLevel.INFO, 1_700_000_000_000L);
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            slot.start(AiAgent.CODE_REVIEW, agentFolder, new ClaudeSessionOptions(false, "MBugai", attributes))
                    .awaitFinished(Duration.ofSeconds(20));
        }
    }

    // the only message sent, to the device that asked
    private ComagentsMessage sentMessage() {
        List<FakePushcaConnector.Broadcast> broadcasts = connector.connections.getLast().broadcasts;
        assertEquals(1, broadcasts.size());
        assertEquals("orchestrator-device-2", broadcasts.getFirst().receivers().deviceId);
        return ComagentsMessage.fromComagentsMessageString(broadcasts.getFirst().message());
    }
}
