package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.exception.PushcaAuthenticationException;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import com.comagents.agentproxy.conf.AgentProxyConf;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(PushcaConnectionFactory.class);

    // how often the Pushca client checks its connection, and signs in or reconnects when it isn't open
    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(2);

    // sent by the server once the connection is registered and accepts commands
    private static final String READY_MESSAGE = "CONNECTION_ALIAS";

    private final AgentProxyConf conf;
    private final int connectTimeoutMs;
    private final Duration waitForReconnectTimeout;

    public PushcaConnectionFactory(AgentProxyConf conf,
                                   @Value("${pushca.connect-timeout:10s}") Duration connectTimeout) {
        this.conf = conf;
        this.connectTimeoutMs = Math.toIntExact(connectTimeout.toMillis());
        // the next keep-alive check, the connect itself, and a second to spare
        this.waitForReconnectTimeout = KEEP_ALIVE_INTERVAL.plus(connectTimeout).plusSeconds(1);
    }

    /** How long a broken connection takes at most to be open again: the Pushca client reconnects it by itself. */
    public Duration getWaitForReconnectTimeout() {
        return waitForReconnectTimeout;
    }

    /**
     * Signs {@code client} in and returns once its connection is open, or after {@code pushca.connect-timeout}. A
     * connection that isn't open by then is returned all the same: the Pushca client signs in and reconnects by
     * itself (every 2 s), and sends fail until it has.
     *
     * @param messages        receives the messages sent to the client, on the client's receiving thread
     * @param channelMessages receives the messages of the channels the client is a member of, on the client's
     *                        receiving thread
     * @throws PushcaAuthenticationException when Pushca refuses the API key ({@code pushca.api-key}): a
     *                                       configuration mistake that no retry fixes
     */
    public PushcaWebSocket createConnection(PClient client, Consumer<? super String> messages,
                                            Consumer<? super ChannelMessage> channelMessages) {
        CountDownLatch ready = new CountDownLatch(1);
        PushcaWebSocket webSocket = new PushcaWebSocketBuilder(conf.getPushcaApiUrl(), conf.getPushcaApiKey(), client, null)
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
