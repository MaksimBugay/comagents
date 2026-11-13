package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;

import java.util.UUID;

/**
 * Receives what people write in a session's chat; the agent's own messages are not passed on. Called on the Pushca
 * client's receiving thread: implementations must return quickly and never throw.
 */
public interface AgentSessionChatListener {

    void onChatMessage(UUID sessionId, AiAgent agent, String senderAccountId, String text);
}
