package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.PChannel;
import com.comagents.agentproxy.communication.AgentSessionPrivateChannelService;

import java.util.function.Consumer;

import static com.comagents.agentproxy.communication.pushca.PushcaConnectionFactory.IGNORE_MESSAGES;

/**
 * One session's Pushca connection, signed in as {@code client}: its chat is a close private channel. Opened on
 * creation; {@link #close()} closes it.
 */
public class AgentSessionPrivateChannelWithPushcaService implements AgentSessionPrivateChannelService, AutoCloseable {

    private final PushcaWebSocket webSocket;

    /**
     * Signs {@code client} in and returns once its connection is open.
     *
     * @param channelMessages receives the messages of the session's chat, on the client's receiving thread
     * @throws IllegalStateException when the connection cannot be opened
     */
    public AgentSessionPrivateChannelWithPushcaService(PushcaConnectionFactory connectionFactory, PClient client,
                                                       Consumer<ChannelMessage> channelMessages) {
        // a session's client gets only chat messages
        this.webSocket = connectionFactory.createConnection(client, IGNORE_MESSAGES, channelMessages);
    }

    @Override
    public PChannel createClosePrivateChannel(String name) {
        // the chat page (chat-room.html?channelId=...) opens a chat by id: with id = name, it opens this one
        return webSocket.createChannel(name, name, true);
    }

    @Override
    public void addMembers(PChannel channel, ClientFilter... members) {
        webSocket.addMembersToChannel(channel, members);
    }

    @Override
    public void sendMessage(PChannel channel, String message) {
        webSocket.sendMessageToChannel(channel, null, message);
    }

    @Override
    public void removeChannel(PChannel channel) {
        webSocket.removeChannel(channel);
    }

    @Override
    public void close() {
        webSocket.close();
    }
}
