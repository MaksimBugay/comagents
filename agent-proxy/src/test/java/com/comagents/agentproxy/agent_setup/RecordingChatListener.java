package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records what people wrote in session chats. */
final class RecordingChatListener implements AgentSessionChatListener {

    record ChatMessage(UUID sessionId, AiAgent agent, String senderAccountId, String text) {
    }

    final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void onChatMessage(UUID sessionId, AiAgent agent, String senderAccountId, String text) {
        messages.add(new ChatMessage(sessionId, agent, senderAccountId, text));
    }
}
