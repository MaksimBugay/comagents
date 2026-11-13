package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpertAnswersTest {

    private final UUID sessionId = UUID.randomUUID();
    private final ExpertAnswers answers = new ExpertAnswers();

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
