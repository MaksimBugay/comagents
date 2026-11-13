package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Logs what people write in session chats; nothing acts on it yet. */
@Singleton
public class LoggingAgentSessionChatListener implements AgentSessionChatListener {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAgentSessionChatListener.class);

    @Override
    public void onChatMessage(UUID sessionId, AiAgent agent, String senderAccountId, String text) {
        LOG.info("[{} {}] chat message from {}: {}", agent.getAgentName(), sessionId, senderAccountId, text);
    }
}
