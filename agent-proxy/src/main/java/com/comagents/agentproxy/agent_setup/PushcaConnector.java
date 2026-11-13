package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;

import java.util.function.Consumer;

/** Opens Pushca connections. */
public interface PushcaConnector {

    /**
     * Signs {@code client} in and returns once its connection is open.
     *
     * @param channelMessages receives the messages of the channels the client is a member of, on the client's
     *                        receiving thread
     * @throws IllegalStateException when the connection cannot be opened
     */
    PushcaConnection connect(PClient client, Consumer<ChannelMessage> channelMessages);
}
