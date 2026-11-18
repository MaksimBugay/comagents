package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.exception.PushcaAuthenticationException;
import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory.FakeConnection;
import com.comagents.commons.domain.AiAgent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSessionWsClientRegistryTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final PushcaAuthenticationException REFUSED =
            new PushcaAuthenticationException("code_review@comagents.com", 401);

    private final UUID sessionId = UUID.randomUUID();
    private final FakePushcaConnectionFactory connectionFactory = new FakePushcaConnectionFactory();
    private final RecordingChatListener chatListener = new RecordingChatListener();
    private final AgentSessionWsClientRegistry registry = new AgentSessionWsClientRegistry(connectionFactory, chatListener,
            "https://secure.fileshare.ovh/chat-room.html");

    @Test
    void signsInAsAgentAndCreatesChatWithActorAsOnlyMember() {
        assertTrue(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));

        FakeConnection connection = onlyConnection();
        assertEquals("comagents-secure-chat", connection.client.workSpaceId);
        assertEquals("CHAT_ROOM", connection.client.applicationId);
        assertEquals("code_review@comagents.com", connection.client.accountId);
        assertEquals(sessionId.toString(), connection.client.deviceId);
        assertEquals("code_review_session_" + sessionId, connection.createdChannels.getFirst().name);
        assertEquals("code_review_session_" + sessionId, connection.createdChannels.getFirst().id);
        assertEquals(List.of(new ClientFilter("comagents-secure-chat", "Maks", null, "CHAT_ROOM")), connection.members);
    }

    @Test
    void joinUrlOpensTheChatAsTheActor() {
        String channelId = "code_review_session_" + sessionId;

        assertEquals("https://secure.fileshare.ovh/chat-room.html?channelId=" + channelId
                        + "&accountId=MBugai&avatar=human-manager&workspaceId=comagents-secure-chat",
                AgentSessionWsClientRegistry.joinUrl("https://secure.fileshare.ovh/chat-room.html", channelId, "MBugai"));
        assertEquals("https://chat.example/chat-room.html?channelId=" + channelId
                        + "&avatar=human-manager&workspaceId=comagents-secure-chat",
                AgentSessionWsClientRegistry.joinUrl("https://chat.example/chat-room.html", channelId, null));
        assertEquals("https://chat.example/chat-room.html?channelId=c&accountId=a+b%40x.com"
                        + "&avatar=human-manager&workspaceId=comagents-secure-chat",
                AgentSessionWsClientRegistry.joinUrl("https://chat.example/chat-room.html", "c", "a b@x.com"));
    }

    @Test
    void chatWithoutActorHasNoMembers() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, null);

        assertEquals(1, onlyConnection().createdChannels.size());
        assertTrue(onlyConnection().members.isEmpty());
    }

    @Test
    void sendsOutputInOrder() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        registry.onOutput(sessionId, "thinking: Read the pom first");
        registry.onOutput(sessionId, "tool: Read pom.xml");

        await().atMost(WAIT).until(() -> onlyConnection().messages.size() == 2);
        assertEquals(List.of("thinking: Read the pom first", "tool: Read pom.xml"), onlyConnection().messages);
    }

    @Test
    void responseIsTheLastMessage() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        registry.onOutput(sessionId, "tool: Read pom.xml");
        registry.onOutput(sessionId, "done: success, 3 turns");
        registry.onResponse(sessionId, "# Code review\n\nAll good");

        await().atMost(WAIT).until(() -> onlyConnection().messages.size() == 3);
        assertEquals("# Code review\n\nAll good", onlyConnection().messages.getLast());
    }

    @Test
    void messagesPeopleWriteInTheChatReachTheListener() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "MBugai");
        String chat = "code_review_session_" + sessionId;

        onlyConnection().receive(chat, "MBugai", "Use Java 25, not 21");
        onlyConnection().receive(chat, "code_review@comagents.com", "the agent's own message");
        onlyConnection().receive("another_chat", "MBugai", "elsewhere");

        assertEquals(List.of(new RecordingChatListener.ChatMessage(sessionId, AiAgent.CODE_REVIEW, "MBugai",
                "Use Java 25, not 21")), chatListener.messages);
    }

    @Test
    void reopenedChatIsUsedNotCreatedAgain() {
        assertTrue(registry.reopenConnection(sessionId, AiAgent.CODE_REVIEW, "MBugai"));
        String chat = "code_review_session_" + sessionId;

        registry.onOutput(sessionId, "after restart");
        onlyConnection().receive(chat, "MBugai", "the answer");

        FakeConnection connection = onlyConnection();
        assertTrue(connection.createdChannels.isEmpty(), "the chat exists already");
        assertTrue(connection.members.isEmpty(), "the actor is a member already");
        await().atMost(WAIT).until(() -> connection.messages.equals(List.of("after restart")));
        assertEquals(List.of(new RecordingChatListener.ChatMessage(sessionId, AiAgent.CODE_REVIEW, "MBugai",
                "the answer")), chatListener.messages);
    }

    @Test
    void refusedReopenLeavesTheChatAndDropsTheClient() {
        connectionFactory.connectFailure = REFUSED;

        assertFalse(registry.reopenConnection(sessionId, AiAgent.CODE_REVIEW, "MBugai"));

        connectionFactory.connectFailure = null;
        assertTrue(registry.reopenConnection(sessionId, AiAgent.CODE_REVIEW, "MBugai"));
        assertTrue(onlyConnection().createdChannels.isEmpty());
        assertTrue(onlyConnection().removedChannels.isEmpty());
    }

    @Test
    void outputOfUnknownSessionIsIgnored() {
        registry.onOutput(sessionId, "tool: Read pom.xml");

        assertTrue(connectionFactory.connections.isEmpty());
    }

    @Test
    void openingAgainKeepsTheOneConnectionAndChat() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        assertEquals(1, onlyConnection().createdChannels.size());
    }

    @Test
    void reopenedConnectionKeepsUsingTheChat() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");
        registry.closeConnection(sessionId);
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        registry.onOutput(sessionId, "after reconnect");

        assertEquals(2, connectionFactory.connections.size());
        FakeConnection first = connectionFactory.connections.getFirst();
        FakeConnection second = connectionFactory.connections.getLast();
        assertTrue(first.closed);
        assertTrue(second.createdChannels.isEmpty(), "the chat of the first connect is reused");
        await().atMost(WAIT).until(() -> second.messages.equals(List.of("after reconnect")));
    }

    @Test
    void removingRemovesChatAndClosesConnection() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        registry.removeConnection(sessionId);
        registry.onOutput(sessionId, "too late");

        FakeConnection connection = onlyConnection();
        assertEquals(connection.createdChannels, connection.removedChannels);
        assertTrue(connection.closed);
        assertTrue(connection.messages.isEmpty());
    }

    @Test
    void deletedSessionDataRemovesItsChat() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        registry.onSessionDataDeleted(sessionId);

        assertEquals(1, onlyConnection().removedChannels.size());
        assertTrue(onlyConnection().closed);
    }

    @Test
    void removesTheChatOfASessionWithoutClient() {
        // e.g. a session left behind by a restart
        registry.removeChat(sessionId, AiAgent.CODE_REVIEW);

        FakeConnection connection = onlyConnection();
        assertEquals("code_review@comagents.com", connection.client.accountId);
        assertEquals(sessionId.toString(), connection.client.deviceId);
        assertTrue(connection.createdChannels.isEmpty(), "the chat is not created again");
        assertEquals(List.of("code_review_session_" + sessionId), connection.removedChannels.stream().map(channel -> channel.id).toList());
        assertTrue(connection.closed);
    }

    @Test
    void removingTheChatOfAConnectedSessionUsesItsClient() {
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        registry.removeChat(sessionId, AiAgent.CODE_REVIEW);

        FakeConnection connection = onlyConnection();
        assertEquals(connection.createdChannels, connection.removedChannels);
        assertTrue(connection.closed);
    }

    @Test
    void chatStaysWhenPushcaIsUnavailableForItsRemoval() {
        connectionFactory.notOpen = true;

        // retried until the Pushca client would have reconnected
        registry.removeChat(sessionId, AiAgent.CODE_REVIEW);

        FakeConnection connection = onlyConnection();
        assertTrue(connection.removedChannels.isEmpty());
        assertTrue(connection.closed);
    }

    @Test
    void sessionRunsWithoutChatWhenPushcaIsUnavailable() {
        connectionFactory.notOpen = true;

        // creating the chat is retried until the Pushca client would have reconnected
        assertFalse(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));
        FakeConnection unavailable = onlyConnection();
        assertTrue(unavailable.createdChannels.isEmpty());
        assertTrue(unavailable.closed);
        registry.onOutput(sessionId, "tool: Read pom.xml");
        assertTrue(unavailable.messages.isEmpty());

        // the failed client was removed, so a later open starts afresh
        connectionFactory.notOpen = false;
        assertTrue(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));
        assertEquals(1, connectionFactory.connections.getLast().createdChannels.size());
    }

    @Test
    void sessionRunsWithoutChatWhenPushcaRefusesToSignIn() {
        connectionFactory.connectFailure = REFUSED;

        assertFalse(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));

        assertTrue(connectionFactory.connections.isEmpty());
    }

    @Test
    void chatThatFailsAfterSignInIsRemovedAgain() {
        connectionFactory.addMembersFailure = new IllegalStateException("Cannot add members to channel");

        assertFalse(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));

        FakeConnection connection = onlyConnection();
        assertEquals(connection.createdChannels, connection.removedChannels);
        assertTrue(connection.closed);
    }

    @Test
    void sendsMessagesToTheSessionChat() {
        assertFalse(registry.sendMessage(sessionId, "Question: which branch?"), "no chat yet");
        registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks");

        assertTrue(registry.sendMessage(sessionId, "Question: which branch?"));

        await().atMost(WAIT).until(() -> onlyConnection().messages.size() == 1);
        assertEquals("Question: which branch?", onlyConnection().messages.getFirst());
    }

    private FakeConnection onlyConnection() {
        assertEquals(1, connectionFactory.connections.size());
        return connectionFactory.connections.getFirst();
    }
}
