package com.comagents.agentproxy.communication;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.core.PChannel;
import com.comagents.commons.communication.BrokenConnectionRetry;
import com.comagents.commons.communication.pushca.PushcaConnectionFailures;

import java.time.Duration;
import java.time.Instant;

import static com.comagents.commons.communication.BrokenConnectionRetry.callWithRetry;
import static com.comagents.commons.communication.BrokenConnectionRetry.runWithRetry;

/**
 * One session's chat: a close private channel of the session's own client. Commands are blocking and throw on
 * failure; their {@code WithRetry} versions try again every second while the connection is broken, for
 * {@link #getRetryTimeout()} ({@link BrokenConnectionRetry}).
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

    /** How long the {@code WithRetry} commands try again while the connection is broken. */
    Duration getRetryTimeout();

    default PChannel createClosePrivateChannelWithRetry(String name) {
        return callWithRetry(() -> createClosePrivateChannel(name), retryDeadline(),
                PushcaConnectionFailures::isBrokenConnection);
    }

    default void addMembersWithRetry(PChannel channel, ClientFilter... members) {
        runWithRetry(() -> addMembers(channel, members), retryDeadline(), PushcaConnectionFailures::isBrokenConnection);
    }

    default void sendMessageWithRetry(PChannel channel, String message) {
        runWithRetry(() -> sendMessage(channel, message), retryDeadline(),
                PushcaConnectionFailures::isBrokenConnection);
    }

    default void removeChannelWithRetry(PChannel channel) {
        runWithRetry(() -> removeChannel(channel), retryDeadline(), PushcaConnectionFailures::isBrokenConnection);
    }

    private Instant retryDeadline() {
        return Instant.now().plus(getRetryTimeout());
    }
}
