package com.comagents.agentorchestrator.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import com.comagents.commons.communication.pushca.PushcaConnectionFactory;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/**
 * Records what would have been sent to Pushca; no test reaches a real server. It is also the
 * {@link PushcaConnectionFactory} of every test application context.
 */
@Singleton
@Replaces(bean = PushcaConnectionFactory.class, factory = PushcaConnectionFactoryProvider.class)
public final class FakePushcaConnectionFactory extends PushcaConnectionFactory {

    public final List<FakeConnection> connections = new CopyOnWriteArrayList<>();

    public FakePushcaConnectionFactory() {
        super(null, null, Duration.ZERO);
    }

    @Override
    public PushcaWebSocket createConnection(PClient client, Consumer<? super String> messages,
                                            Consumer<? super ChannelMessage> channelMessages) {
        FakeConnection connection = new FakeConnection(client, messages);
        connections.add(connection);
        return connection.webSocket;
    }

    public record Broadcast(ClientFilter receivers, String message) {
    }

    /** One connection: its {@link PushcaWebSocket} records the broadcasts the code under test sends. */
    public static final class FakeConnection {

        public final PClient client;
        public final List<Broadcast> broadcasts = new CopyOnWriteArrayList<>();
        // set to make broadcasts fail, as when the socket is broken
        public volatile RuntimeException broadcastFailure;
        public volatile boolean closed;
        private final Consumer<? super String> incomingMessages;
        private final PushcaWebSocket webSocket;

        private FakeConnection(PClient client, Consumer<? super String> incomingMessages) {
            this.client = client;
            this.incomingMessages = incomingMessages;
            this.webSocket = mock(PushcaWebSocket.class, this::answer);
        }

        /** A message Pushca delivers to this connection's client, e.g. an executor's reply. */
        public void receive(String message) {
            incomingMessages.accept(message);
        }

        // the PushcaWebSocket commands agent-orchestrator sends; anything else answers with defaults
        private Object answer(InvocationOnMock call) throws Throwable {
            Object[] arguments = call.getArguments();
            switch (call.getMethod().getName()) {
                case "broadcastMessage" -> {
                    if (broadcastFailure != null) {
                        throw broadcastFailure;
                    }
                    broadcasts.add(new Broadcast((ClientFilter) arguments[0], (String) arguments[1]));
                    return null;
                }
                case "close" -> {
                    closed = true;
                    return null;
                }
                default -> {
                    return RETURNS_DEFAULTS.answer(call);
                }
            }
        }
    }
}
