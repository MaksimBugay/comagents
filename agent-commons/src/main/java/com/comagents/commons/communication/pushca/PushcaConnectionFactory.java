package com.comagents.commons.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.exception.PushcaAuthenticationException;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opens connections to the Pushca server at {@code apiUrl} with the organization's API key {@code apiKey}.
 */
public class PushcaConnectionFactory {

    /** For a client that ignores what it receives: plain messages, channel messages, or both. */
    public static final Consumer<Object> IGNORE_MESSAGES = _ -> {
    };

    private static final Logger LOG = LoggerFactory.getLogger(PushcaConnectionFactory.class);

    // how often the Pushca client checks its connection, and signs in or reconnects when it isn't open
    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(2);

    // sent by the server once the connection is registered and accepts commands
    private static final String READY_MESSAGE = "CONNECTION_ALIAS";

    private final String apiUrl;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final Duration waitForReconnectTimeout;

    public PushcaConnectionFactory(String apiUrl, String apiKey, Duration connectTimeout) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.connectTimeoutMs = Math.toIntExact(connectTimeout.toMillis());
        // the next keep-alive check, the connect itself, and a second to spare
        this.waitForReconnectTimeout = KEEP_ALIVE_INTERVAL.plus(connectTimeout).plusSeconds(1);
    }

    /** How long a broken connection takes at most to be open again: the Pushca client reconnects it by itself. */
    public Duration getWaitForReconnectTimeout() {
        return waitForReconnectTimeout;
    }

    /**
     * Signs {@code client} in and returns once its connection is open, or after the connect timeout. A connection
     * that isn't open by then is returned all the same: the Pushca client signs in and reconnects by itself (every
     * 2 s), and sends fail until it has.
     *
     * @param messages        receives the messages sent to the client, on the client's receiving thread
     * @param channelMessages receives the messages of the channels the client is a member of, on the client's
     *                        receiving thread
     * @throws PushcaAuthenticationException when Pushca refuses the API key: a configuration mistake that no retry
     *                                       fixes
     */
    public PushcaWebSocket createConnection(PClient client, Consumer<? super String> messages,
                                            Consumer<? super ChannelMessage> channelMessages) {
        CountDownLatch ready = new CountDownLatch(1);
        PushcaWebSocket webSocket = new PushcaWebSocketBuilder(apiUrl, apiKey, client, null)
                .withConnectTimeoutMs(connectTimeoutMs)
                .withKeepAliveInterval(KEEP_ALIVE_INTERVAL)
                .withMessageConsumer((_, message) -> {
                    if (READY_MESSAGE.equals(message)) {
                        ready.countDown();
                    } else {
                        messages.accept(message);
                    }
                })
                .withChannelMessageConsumer((_, message) -> channelMessages.accept(message))
                .build();
        // no pusher instance: Pushca couldn't be reached
        if (webSocket.getPusherInstanceId() == null) {
            LOG.warn("Cannot reach Pushca to sign in {}, the Pushca client tries again by itself", webSocket.getClientInfo());
            return webSocket;
        }
        awaitReady(webSocket, ready);
        return webSocket;
    }

    // The server takes a moment after the socket opens to register the connection, and announces it with
    // CONNECTION_ALIAS. A command sent before that is never answered.
    private void awaitReady(PushcaWebSocket webSocket, CountDownLatch ready) {
        try {
            if (ready.await(connectTimeoutMs, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.warn("Pushca did not confirm the connection of {} within {} ms, the Pushca client reconnects by itself",
                webSocket.getClientInfo(), connectTimeoutMs);
    }
}
