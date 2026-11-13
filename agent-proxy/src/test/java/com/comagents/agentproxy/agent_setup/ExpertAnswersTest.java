package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.AgentProxyMcpServer;
import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpertAnswersTest {

    private final UUID sessionId = UUID.randomUUID();
    private ExpertAnswers answers;

    @BeforeEach
    void createAnswers(@TempDir Path sessionsRoot) {
        // no session in it waits for a deferred answer
        ClaudeAgentSessionRegistry sessions = new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), "false", 1,
                Duration.ofMinutes(1), Duration.ofMinutes(1), new SessionFolders(sessionsRoot.toString()),
                new AgentSessionWsClientRegistry(new FakePushcaConnector(), new RecordingChatListener(), "https://chat.example"),
                new AgentProxyMcpServer(ObjectMapper.getDefault(), "http://127.0.0.1:8080/mcp", Duration.ofMinutes(4)));
        answers = new ExpertAnswers(() -> sessions);
    }

    @Test
    void nextChatMessageOfTheSessionIsTheAnswer() {
        CompletableFuture<String> answer = answers.expect(sessionId);

        answers.onChatMessage(UUID.randomUUID(), AiAgent.CODE_REVIEW, "Maks", "another session");
        assertFalse(answer.isDone());
        answers.onChatMessage(sessionId, AiAgent.CODE_REVIEW, "Maks", "main");
        answers.onChatMessage(sessionId, AiAgent.CODE_REVIEW, "Maks", "and one more thing");

        assertEquals("main", answer.join());
    }

    @Test
    void oneQuestionPerSessionAtATime() {
        answers.expect(sessionId);

        assertThrows(IllegalStateException.class, () -> answers.expect(sessionId));
    }

    @Test
    void forgottenQuestionGetsNoAnswerAndFreesTheSession() {
        CompletableFuture<String> first = answers.expect(sessionId);
        answers.forget(sessionId, first);

        answers.onChatMessage(sessionId, AiAgent.CODE_REVIEW, "Maks", "too late");
        CompletableFuture<String> second = answers.expect(sessionId);
        answers.onChatMessage(sessionId, AiAgent.CODE_REVIEW, "Maks", "main");

        assertFalse(first.isDone());
        assertEquals("main", second.join());
    }

    @Test
    void messageWithoutQuestionIsNoAnswer() {
        answers.onChatMessage(sessionId, AiAgent.CODE_REVIEW, "Maks", "hello");

        assertFalse(answers.expect(sessionId).isDone());
    }
}
