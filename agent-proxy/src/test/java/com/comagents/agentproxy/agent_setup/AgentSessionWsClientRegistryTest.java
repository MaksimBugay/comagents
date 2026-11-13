package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.agentproxy.agent_setup.FakePushcaConnector.FakeConnection;
import com.comagents.agentproxy.domain.AiAgent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("resource")
class AgentSessionWsClientRegistryTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private final UUID sessionId = UUID.randomUUID();
    private final FakePushcaConnector connector = new FakePushcaConnector();
    private final AgentSessionWsClientRegistry registry = new AgentSessionWsClientRegistry(connector,
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
    void outputOfUnknownSessionIsIgnored() {
        registry.onOutput(sessionId, "tool: Read pom.xml");

        assertTrue(connector.connections.isEmpty());
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

        assertEquals(2, connector.connections.size());
        FakeConnection first = connector.connections.getFirst();
        FakeConnection second = connector.connections.getLast();
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
    void sessionRunsWithoutChatWhenPushcaIsUnavailable() {
        connector.connectFailure = new IllegalStateException("Cannot sign in to Pushca");

        assertFalse(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));
        registry.onOutput(sessionId, "tool: Read pom.xml");
        assertTrue(connector.connections.isEmpty());

        // the failed client was removed, so a later open starts afresh
        connector.connectFailure = null;
        assertTrue(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));
        assertEquals(1, onlyConnection().createdChannels.size());
    }

    @Test
    void chatThatFailsAfterSignInIsRemovedAgain() {
        connector.addMembersFailure = new IllegalStateException("Cannot add members to channel");

        assertFalse(registry.openConnection(sessionId, AiAgent.CODE_REVIEW, "Maks"));

        FakeConnection connection = onlyConnection();
        assertEquals(connection.createdChannels, connection.removedChannels);
        assertTrue(connection.closed);
    }

    private FakeConnection onlyConnection() {
        assertEquals(1, connector.connections.size());
        return connector.connections.getFirst();
    }
}
