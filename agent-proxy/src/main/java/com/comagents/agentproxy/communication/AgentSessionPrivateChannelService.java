package com.comagents.agentproxy.communication;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.core.PChannel;

/**
 * One session's chat: a close private channel of the session's own client. Commands are blocking and throw on
 * failure.
 */
public interface AgentSessionPrivateChannelService {

    /**
     * Creates a channel only its owner (this connection's account) and the members it adds can see. Its id is its
     * name, so the chat page opens it by name; a close private channel can only be created with a new id.
     */
    PChannel createClosePrivateChannel(String name);

    void addMembers(PChannel channel, ClientFilter... members);

    void sendMessage(PChannel channel, String message);

    void removeChannel(PChannel channel);
}
