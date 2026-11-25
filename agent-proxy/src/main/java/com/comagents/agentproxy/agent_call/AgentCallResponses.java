package com.comagents.agentproxy.agent_call;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.commons.response.AgentSessionResponse;
import com.comagents.commons.serialization.JsonUtility;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The responses of the agent sessions that sessions here called through their agent tools, by the called session's
 * id. While the calling tool call still waits, the response completes it; once the caller has given up waiting and
 * ended its turn on the call, the response resumes the caller
 * ({@link ClaudeAgentSessionRegistry#answerDeferredAgentCall}). A response may also come before the call waits for it
 * (the orchestrator answered the start late): it is kept for the call.
 */
@Singleton
public class AgentCallResponses {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCallResponses.class);

    // provided lazily, like for ExpertAnswers: the registry reports to the session chats
    private final BeanProvider<ClaudeAgentSessionRegistry> sessions;
    private final Map<UUID, CompletableFuture<AgentSessionResponse>> pending = new ConcurrentHashMap<>();

    public AgentCallResponses(BeanProvider<ClaudeAgentSessionRegistry> sessions) {
        this.sessions = sessions;
    }

    /** The agent context of a response, as an agent tool returns it and a resumed caller gets it. */
    public static String toText(AgentSessionResponse response) {
        return JsonUtility.toJson(response.agentContext());
    }

    /**
     * Starts waiting for the response of the called session {@code calledSessionId}.
     *
     * @return completes with the response; already complete when it came first
     */
    public CompletableFuture<AgentSessionResponse> expect(UUID calledSessionId) {
        return pending.computeIfAbsent(calledSessionId, _ -> new CompletableFuture<>());
    }

    /**
     * Stops waiting for {@code response}, e.g. after a timeout; a later response resumes the deferred caller instead.
     *
     * @return false if it was responded meanwhile
     */
    public boolean forget(UUID calledSessionId, CompletableFuture<AgentSessionResponse> response) {
        return pending.remove(calledSessionId, response) && !response.isDone();
    }

    /** A called session's response, as this executor gets it over Pushca; resuming starts a process. */
    public void onResponse(AgentSessionResponse response) {
        UUID calledSessionId = response.request().sessionId();
        CompletableFuture<AgentSessionResponse> waiting = pending.remove(calledSessionId);
        if (waiting != null) {
            waiting.complete(response);
            return;
        }
        if (sessions.get().answerDeferredAgentCall(calledSessionId, toText(response))) {
            return;
        }
        LOG.info("Response of session {} came before its call waits for it", calledSessionId);
        pending.putIfAbsent(calledSessionId, CompletableFuture.completedFuture(response));
    }
}
