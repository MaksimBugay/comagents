package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers to the questions agents ask in their session chats: the next message a person writes in the chat answers
 * the session's question. While the asking tool call still waits, the answer completes it; once the session has
 * given up waiting and ended its turn on the question, the answer resumes the session
 * ({@link ClaudeAgentSessionRegistry#answerDeferredQuestion}). One question per session at a time; messages written
 * while nothing is asked are not answers.
 */
@Singleton
public class ExpertAnswers implements AgentSessionChatListener {

    private static final Logger LOG = LoggerFactory.getLogger(ExpertAnswers.class);

    // provided lazily: the registry reports to the session chats, which report to this listener
    private final BeanProvider<ClaudeAgentSessionRegistry> sessions;
    private final Map<UUID, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public ExpertAnswers(BeanProvider<ClaudeAgentSessionRegistry> sessions) {
        this.sessions = sessions;
    }

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

    /**
     * Stops waiting for {@code answer}, e.g. after a timeout; a later chat message is no answer to it.
     *
     * @return false if it was answered meanwhile
     */
    public boolean forget(UUID sessionId, CompletableFuture<String> answer) {
        return pending.remove(sessionId, answer);
    }

    // resuming starts a process: off the Pushca client's receiving thread
    @Override
    public void onChatMessage(UUID sessionId, AiAgent agent, String senderAccountId, String text) {
        CompletableFuture<String> answer = pending.remove(sessionId);
        if (answer != null) {
            answer.complete(text);
            return;
        }
        Thread.ofVirtual().name("expert-answer-" + sessionId).start(() -> {
            try {
                sessions.get().answerDeferredQuestion(sessionId, text);
            } catch (RuntimeException e) {
                LOG.error("[{} {}] handling the answer failed", agent.getAgentName(), sessionId, e);
            }
        });
    }
}
