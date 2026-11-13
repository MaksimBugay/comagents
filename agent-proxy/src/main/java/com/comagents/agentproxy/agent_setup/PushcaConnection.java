package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.core.PChannel;

/** The part of a Pushca connection that a session's chat uses. Commands are blocking and throw on failure. */
public interface PushcaConnection extends AutoCloseable {

    /**
     * Creates a channel only its owner (this connection's account) and the members it adds can see. Its id is its
     * name, so the chat page opens it by name; a close private channel can only be created with a new id.
     */
    PChannel createClosePrivateChannel(String name);

    void addMembers(PChannel channel, ClientFilter... members);

    void sendMessage(PChannel channel, String message);

    void removeChannel(PChannel channel);

    @Override
    void close();
}
