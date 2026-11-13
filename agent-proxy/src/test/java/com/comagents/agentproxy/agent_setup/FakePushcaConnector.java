package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.PChannel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Records what would have been sent to Pushca; no test reaches a real server. */
final class FakePushcaConnector implements PushcaConnector {

    final List<FakeConnection> connections = new CopyOnWriteArrayList<>();
    // set to make the next connects fail, as with a wrong API key or an unreachable server
    volatile RuntimeException connectFailure;
    // set to make adding the actor to a new chat fail, after sign-in and channel creation
    volatile RuntimeException addMembersFailure;

    @Override
    public PushcaConnection connect(PClient client, Consumer<ChannelMessage> channelMessages) {
        if (connectFailure != null) {
            throw connectFailure;
        }
        FakeConnection connection = new FakeConnection(client, addMembersFailure, channelMessages);
        connections.add(connection);
        return connection;
    }

    static final class FakeConnection implements PushcaConnection {

        final PClient client;
        final List<PChannel> createdChannels = new CopyOnWriteArrayList<>();
        final List<ClientFilter> members = new CopyOnWriteArrayList<>();
        final List<String> messages = new CopyOnWriteArrayList<>();
        final List<PChannel> removedChannels = new CopyOnWriteArrayList<>();
        volatile boolean closed;
        private final RuntimeException addMembersFailure;
        private final Consumer<ChannelMessage> channelMessages;

        private FakeConnection(PClient client, RuntimeException addMembersFailure,
                               Consumer<ChannelMessage> channelMessages) {
            this.client = client;
            this.addMembersFailure = addMembersFailure;
            this.channelMessages = channelMessages;
        }

        /** A message Pushca delivers to this connection: someone wrote {@code body} in channel {@code channelId}. */
        void receive(String channelId, String senderAccountId, String body) {
            ChannelMessage message = new ChannelMessage();
            message.channelId = channelId;
            message.sender = new PClient("comagents-secure-chat", senderAccountId, "device-1", "CHAT_ROOM");
            message.body = body;
            channelMessages.accept(message);
        }

        @Override
        public PChannel createClosePrivateChannel(String name) {
            PChannel channel = new PChannel(name, name, true);
            createdChannels.add(channel);
            return channel;
        }

        @Override
        public void addMembers(PChannel channel, ClientFilter... members) {
            if (addMembersFailure != null) {
                throw addMembersFailure;
            }
            this.members.addAll(List.of(members));
        }

        @Override
        public void sendMessage(PChannel channel, String message) {
            messages.add(message);
        }

        @Override
        public void removeChannel(PChannel channel) {
            removedChannels.add(channel);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
