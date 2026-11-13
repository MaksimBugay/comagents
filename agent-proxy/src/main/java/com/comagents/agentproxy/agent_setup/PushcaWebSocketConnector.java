package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.PChannel;
import com.comagents.agentproxy.conf.AgentProxyConf;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Connections to the Pushca server ({@code pushca.api-url}) with the organization's API key ({@code pushca.api-key}). */
@Singleton
public class PushcaWebSocketConnector implements PushcaConnector {

    // sent by the server once the connection is registered and accepts commands
    private static final String READY_MESSAGE = "CONNECTION_ALIAS";

    private final AgentProxyConf conf;
    private final int connectTimeoutMs;

    public PushcaWebSocketConnector(AgentProxyConf conf,
                                    @Value("${pushca.connect-timeout:10s}") Duration connectTimeout) {
        this.conf = conf;
        this.connectTimeoutMs = Math.toIntExact(connectTimeout.toMillis());
    }

    @Override
    public PushcaConnection connect(PClient client) {
        CountDownLatch ready = new CountDownLatch(1);
        PushcaWebSocket webSocket = new PushcaWebSocketBuilder(conf.getPushcaApiUrl(), conf.getPushcaApiKey(), client, null)
                .withConnectTimeoutMs(connectTimeoutMs)
                .withMessageConsumer((_, message) -> {
                    if (READY_MESSAGE.equals(message)) {
                        ready.countDown();
                    }
                })
                .build();
        // no pusher instance: the sign-in itself failed (wrong API key, server unreachable)
        if (webSocket.getPusherInstanceId() == null) {
            webSocket.close();
            throw new IllegalStateException("Cannot sign in to Pushca as " + webSocket.getClientInfo());
        }
        awaitReady(webSocket, ready);
        return new WebSocketConnection(webSocket);
    }

    // The server takes a moment after the socket opens to register the connection, and announces it with
    // CONNECTION_ALIAS. A command sent before that is never answered, and the connection stays unusable.
    private void awaitReady(PushcaWebSocket webSocket, CountDownLatch ready) {
        try {
            if (ready.await(connectTimeoutMs, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        webSocket.close();
        throw new IllegalStateException("Pushca did not confirm the connection of " + webSocket.getClientInfo()
                + " within " + connectTimeoutMs + " ms");
    }

    private record WebSocketConnection(PushcaWebSocket webSocket) implements PushcaConnection {

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
}
