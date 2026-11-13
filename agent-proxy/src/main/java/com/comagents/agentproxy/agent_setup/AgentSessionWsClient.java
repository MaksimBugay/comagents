package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.PChannel;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * One session's Pushca client and its chat: a close private channel owned by the agent, with the session's actor
 * as its only member. The channel is created on the first connect and kept across reconnects.
 */
final class AgentSessionWsClient {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionWsClient.class);

    private final UUID sessionId;
    private final PClient client;
    private final String channelName;
    @Nullable
    private final ClientFilter actor;
    // the chat page with this chat and the actor filled in, logged once the chat exists
    private final String joinUrl;
    // one virtual thread per session: keeps messages in output order, and Claude's output reader never waits for Pushca
    private final ExecutorService sender;

    // guarded by this
    private PushcaConnection connection;
    private PChannel channel;
    // only the first of a run of failed sends is logged as a warning, so an unreachable Pushca doesn't flood the log
    private boolean sendFailing;

    AgentSessionWsClient(UUID sessionId, PClient client, String channelName, @Nullable ClientFilter actor,
                         String joinUrl) {
        this.sessionId = sessionId;
        this.client = client;
        this.channelName = channelName;
        this.actor = actor;
        this.joinUrl = joinUrl;
        this.sender = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("pushca-" + sessionId).factory());
    }

    /** Opens the connection unless it is open, and creates the chat on the first connect. */
    synchronized void connect(PushcaConnector connector) {
        if (connection != null) {
            return;
        }
        connection = connector.connect(client);
        if (channel == null) {
            channel = connection.createClosePrivateChannel(channelName);
            if (actor != null) {
                connection.addMembers(channel, actor);
            }
            LOG.info("[{}] chat {} created, join: {}", sessionId, channelName, joinUrl);
        }
    }

    /** Queues one line of output for the chat. */
    void send(String message) {
        try {
            sender.execute(() -> deliver(message));
        } catch (RejectedExecutionException e) {
            // removed meanwhile: the chat is gone
        }
    }

    /** Closes the connection; the chat stays, and {@link #connect} opens it again. */
    synchronized void close() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    /** Drops queued messages, removes the chat and closes the connection; the client can't be used afterwards. */
    synchronized void removeChannelAndClose() {
        sender.shutdownNow();
        if (connection != null && channel != null) {
            try {
                connection.removeChannel(channel);
                LOG.info("[{}] chat {} removed", sessionId, channelName);
            } catch (RuntimeException e) {
                LOG.warn("[{}] cannot remove chat {}: {}", sessionId, channelName, e.getMessage());
            }
        }
        close();
    }

    private synchronized void deliver(String message) {
        if (connection == null || channel == null) {
            return;
        }
        try {
            connection.sendMessage(channel, message);
            sendFailing = false;
        } catch (RuntimeException e) {
            if (!sendFailing) {
                LOG.warn("[{}] cannot send to chat {}: {}", sessionId, channelName, e.getMessage());
            }
            sendFailing = true;
        }
    }
}
