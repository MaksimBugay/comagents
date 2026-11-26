package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.request.SessionCleanupRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The orchestrator's {@link SessionCleanupRequest}: it keeps a session's response, so the session's files here are
 * deleted at once ({@link AgentResponseService#cleanUpNow}), not after {@code agent-response.cleanup-delay}. Nothing
 * is answered.
 */
@Singleton
public class SessionCleanupListener implements ComagentsMessageListener<SessionCleanupRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupListener.class);

    private final AgentResponseService agentResponseService;

    public SessionCleanupListener(AgentResponseService agentResponseService) {
        this.agentResponseService = agentResponseService;
    }

    @Override
    public Class<SessionCleanupRequest> getPayloadClass() {
        return SessionCleanupRequest.class;
    }

    // deleting a session waits for its run and its chat: off the Pushca client's receiving thread
    @Override
    public void consumePayload(String sequenceId, SessionCleanupRequest request) {
        Thread.ofVirtual().name("session-cleanup-" + request.sessionId()).start(() -> {
            try {
                agentResponseService.cleanUpNow(request.agent(), request.sessionId());
            } catch (RuntimeException e) {
                LOG.error("[{} {}] deleting session files failed", request.agent().getAgentName(), request.sessionId(), e);
            }
        });
    }
}
