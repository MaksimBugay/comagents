package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import com.comagents.agentproxy.conf.AgentProxyConf;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opens connections to the Pushca server ({@code pushca.api-url}) with the organization's API key
 * ({@code pushca.api-key}).
 */
// @Primary for the IDE, which doesn't know @Replaces and also sees the tests' FakePushcaConnectionFactory
@Primary
@Singleton
public class PushcaConnectionFactory {

    /** For a client that ignores what it receives: plain messages, channel messages, or both. */
    public static final Consumer<Object> IGNORE_MESSAGES = _ -> {
    };

    // sent by the server once the connection is registered and accepts commands
    private static final String READY_MESSAGE = "CONNECTION_ALIAS";

    private final AgentProxyConf conf;
    private final int connectTimeoutMs;

    public PushcaConnectionFactory(AgentProxyConf conf,
                                   @Value("${pushca.connect-timeout:10s}") Duration connectTimeout) {
        this.conf = conf;
        this.connectTimeoutMs = Math.toIntExact(connectTimeout.toMillis());
    }

    /**
     * Signs {@code client} in and returns once its connection is open.
     *
     * @param messages        receives the messages sent to the client, on the client's receiving thread
     * @param channelMessages receives the messages of the channels the client is a member of, on the client's
     *                        receiving thread
     * @throws IllegalStateException when the connection cannot be opened
     */
    public PushcaWebSocket createConnection(PClient client, Consumer<? super String> messages,
                                            Consumer<? super ChannelMessage> channelMessages) {
        CountDownLatch ready = new CountDownLatch(1);
        PushcaWebSocket webSocket = new PushcaWebSocketBuilder(conf.getPushcaApiUrl(), conf.getPushcaApiKey(), client, null)
                .withConnectTimeoutMs(connectTimeoutMs)
                .withMessageConsumer((_, message) -> {
                    if (READY_MESSAGE.equals(message)) {
                        ready.countDown();
                    } else {
                        messages.accept(message);
                    }
                })
                .withChannelMessageConsumer((_, message) -> channelMessages.accept(message))
                .build();
        // no pusher instance: the sign-in itself failed (wrong API key, server unreachable)
        if (webSocket.getPusherInstanceId() == null) {
            webSocket.close();
            throw new IllegalStateException("Cannot sign in to Pushca as " + webSocket.getClientInfo());
        }
        awaitReady(webSocket, ready);
        return webSocket;
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
}
