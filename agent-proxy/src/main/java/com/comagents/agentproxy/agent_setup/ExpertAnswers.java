package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers to the questions agents ask in their session chats: while a session waits for one, the next message a
 * person writes in its chat is the answer. One question per session at a time; messages written while nothing is
 * asked are not answers.
 */
@Singleton
public class ExpertAnswers implements AgentSessionChatListener {

    private final Map<UUID, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /**
     * Starts waiting for the answer to the session's question: register before posting the question, so a quick
     * answer can't arrive first.
     *
     * @return completes with the next message in the session's chat
     * @throws IllegalStateException when the session already waits for an answer
     */
    public CompletableFuture<String> expect(UUID sessionId) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        if (pending.putIfAbsent(sessionId, answer) != null) {
            throw new IllegalStateException("Session " + sessionId + " already waits for an answer");
        }
        return answer;
    }

    /** Stops waiting for {@code answer}, e.g. after a timeout; a later chat message is no answer to it. */
    public void forget(UUID sessionId, CompletableFuture<String> answer) {
        pending.remove(sessionId, answer);
    }

    @Override
    public void onChatMessage(UUID sessionId, AiAgent agent, String senderAccountId, String text) {
        CompletableFuture<String> answer = pending.remove(sessionId);
        if (answer != null) {
            answer.complete(text);
        }
    }
}
