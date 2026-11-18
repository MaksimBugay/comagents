package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.PChannel;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/**
 * Records what would have been sent to Pushca; no test reaches a real server. It is also the
 * {@link PushcaConnectionFactory} of every test application context; tests that check what was sent inject it and
 * {@link #reset()} it, or create their own.
 */
@Singleton
@Replaces(PushcaConnectionFactory.class)
public final class FakePushcaConnectionFactory extends PushcaConnectionFactory {

    public final List<FakeConnection> connections = new CopyOnWriteArrayList<>();
    // set to make the next connects fail, as with a wrong API key or an unreachable server
    public volatile RuntimeException connectFailure;
    // set to make adding the actor to a new chat fail, after sign-in and channel creation
    public volatile RuntimeException addMembersFailure;

    public FakePushcaConnectionFactory() {
        super(null, Duration.ZERO);
    }

    /** Forgets the connections opened so far and the failures set. */
    public void reset() {
        connections.clear();
        connectFailure = null;
        addMembersFailure = null;
    }

    @Override
    public PushcaWebSocket createConnection(PClient client, Consumer<? super String> messages,
                                            Consumer<? super ChannelMessage> channelMessages) {
        if (connectFailure != null) {
            throw connectFailure;
        }
        FakeConnection connection = new FakeConnection(client, addMembersFailure, messages, channelMessages);
        connections.add(connection);
        return connection.webSocket;
    }

    public record Broadcast(ClientFilter receivers, String message) {
    }

    /** One connection: its {@link PushcaWebSocket} records the commands the code under test sends. */
    public static final class FakeConnection {

        public final PClient client;
        public final List<PChannel> createdChannels = new CopyOnWriteArrayList<>();
        public final List<ClientFilter> members = new CopyOnWriteArrayList<>();
        // what was sent to chats
        public final List<String> messages = new CopyOnWriteArrayList<>();
        public final List<PChannel> removedChannels = new CopyOnWriteArrayList<>();
        public final List<Broadcast> broadcasts = new CopyOnWriteArrayList<>();
        // set to make broadcasts fail, as when the socket is broken
        public volatile RuntimeException broadcastFailure;
        public volatile boolean closed;
        private final RuntimeException addMembersFailure;
        private final Consumer<? super String> incomingMessages;
        private final Consumer<? super ChannelMessage> channelMessages;
        private final PushcaWebSocket webSocket;

        private FakeConnection(PClient client, RuntimeException addMembersFailure, Consumer<? super String> incomingMessages,
                               Consumer<? super ChannelMessage> channelMessages) {
            this.client = client;
            this.addMembersFailure = addMembersFailure;
            this.incomingMessages = incomingMessages;
            this.channelMessages = channelMessages;
            this.webSocket = mock(PushcaWebSocket.class, this::answer);
        }

        /** A message Pushca delivers to this connection: someone wrote {@code body} in channel {@code channelId}. */
        public void receive(String channelId, String senderAccountId, String body) {
            ChannelMessage message = new ChannelMessage();
            message.channelId = channelId;
            message.sender = new PClient("comagents-secure-chat", senderAccountId, "device-1", "CHAT_ROOM");
            message.body = body;
            channelMessages.accept(message);
        }

        /** A message Pushca delivers to this connection's client, e.g. an orchestrator's request. */
        public void receive(String message) {
            incomingMessages.accept(message);
        }

        // the PushcaWebSocket commands agent-proxy sends; anything else answers with defaults
        private Object answer(InvocationOnMock call) throws Throwable {
            Object[] arguments = call.getArguments();
            switch (call.getMethod().getName()) {
                case "createChannel" -> {
                    PChannel channel = new PChannel((String) arguments[0], (String) arguments[1], true);
                    createdChannels.add(channel);
                    return channel;
                }
                case "addMembersToChannel" -> {
                    if (addMembersFailure != null) {
                        throw addMembersFailure;
                    }
                    members.addAll(clientFilters(arguments));
                    return null;
                }
                case "sendMessageToChannel" -> {
                    messages.add((String) arguments[arguments.length - 1]);
                    return null;
                }
                case "removeChannel" -> {
                    removedChannels.add((PChannel) arguments[0]);
                    return null;
                }
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

        // the members after the channel, whether Mockito passes the varargs as an array or one by one
        private static List<ClientFilter> clientFilters(Object[] arguments) {
            List<ClientFilter> filters = new ArrayList<>();
            for (int i = 1; i < arguments.length; i++) {
                if (arguments[i] instanceof ClientFilter[] array) {
                    filters.addAll(List.of(array));
                } else if (arguments[i] instanceof ClientFilter filter) {
                    filters.add(filter);
                }
            }
            return filters;
        }
    }
}
