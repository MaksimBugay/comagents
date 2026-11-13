package com.comagents.agentproxy.agent_setup;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionListener;
import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushca clients of agent sessions, one per session. Each client signs in as the agent
 * ({@code <agent name>@comagents.com}, the session id as device id) and mirrors the session's output into the
 * session's own close private chat {@code <agent name>_session_<session id>}, whose only member is the actor who
 * started the session. Clients and chats are removed together with the session's files.
 */
@Singleton
public class AgentSessionWsClientRegistry implements ClaudeSessionListener {

    static final String WORKSPACE_ID = "comagents-secure-chat";
    static final String APPLICATION_ID = "CHAT_ROOM";
    static final String ACCOUNT_DOMAIN = "@comagents.com";

    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionWsClientRegistry.class);

    private final PushcaConnector connector;
    private final AgentSessionChatListener chatListener;
    private final String chatPageUrl;
    private final Map<UUID, AgentSessionWsClient> clients = new ConcurrentHashMap<>();

    /**
     * @param chatListener receives what people write in session chats
     * @param chatPageUrl  the chat page ({@code pushca.chat-url}), for the join link logged with each chat
     */
    public AgentSessionWsClientRegistry(PushcaConnector connector, AgentSessionChatListener chatListener,
                                        @Value("${pushca.chat-url}") String chatPageUrl) {
        this.connector = connector;
        this.chatListener = chatListener;
        this.chatPageUrl = chatPageUrl;
    }

    /**
     * Adds the session's client unless it is registered, and opens its connection unless it is open; on the
     * first connect it creates the chat and adds the actor to it. Blocks until done.
     * <p>
     * The chat is optional: when Pushca can't be reached or refuses a command, the client (and the chat, if it was
     * created) is removed again and the session runs without one.
     *
     * @param actorAccountId the Pushca account that started the session; without one the chat has no member
     * @return whether the session has its chat
     */
    public boolean openConnection(UUID sessionId, AiAgent agent, @Nullable String actorAccountId) {
        try {
            clients.computeIfAbsent(sessionId, _ -> newClient(sessionId, agent, actorAccountId))
                    .connect(connector, false);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("[{} {}] Pushca unavailable, the session runs without a chat: {}", agent.getAgentName(), sessionId,
                    e.getMessage());
            removeConnection(sessionId);
            return false;
        }
    }

    /**
     * {@link #openConnection} for a session whose chat was created before, e.g. an archived session after a restart:
     * the existing chat is used, never created again, so its history and members stay. When Pushca can't be
     * reached, the client is dropped and the chat is left as it is.
     *
     * @return whether the session's chat is connected again
     */
    public boolean reopenConnection(UUID sessionId, AiAgent agent, @Nullable String actorAccountId) {
        try {
            clients.computeIfAbsent(sessionId, _ -> newClient(sessionId, agent, actorAccountId))
                    .connect(connector, true);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("[{} {}] Pushca unavailable, the chat is not reopened: {}", agent.getAgentName(), sessionId,
                    e.getMessage());
            Optional.ofNullable(clients.remove(sessionId)).ifPresent(AgentSessionWsClient::shutdown);
            return false;
        }
    }

    /** Closes the session's connection, keeping the client and its chat. */
    public void closeConnection(UUID sessionId) {
        Optional.ofNullable(clients.get(sessionId)).ifPresent(AgentSessionWsClient::close);
    }

    /** Removes the session's client from the registry, removes its chat and closes its connection. */
    public void removeConnection(UUID sessionId) {
        Optional.ofNullable(clients.remove(sessionId)).ifPresent(AgentSessionWsClient::removeChannelAndClose);
    }

    @Override
    public void onOutput(UUID sessionId, String output) {
        Optional.ofNullable(clients.get(sessionId)).ifPresent(client -> client.send(output));
    }

    // queued after all of the session's output, so it is the last message in the chat's history
    @Override
    public void onResponse(UUID sessionId, String response) {
        Optional.ofNullable(clients.get(sessionId)).ifPresent(client -> client.send(response));
    }

    @Override
    public void onSessionDataDeleted(UUID sessionId) {
        removeConnection(sessionId);
    }

    // the chats stay: sessions archived for a resume keep their files, and so their chats; ArchivedSessionChats
    // reopens them after the restart
    @PreDestroy
    void closeAllConnections() {
        clients.values().forEach(AgentSessionWsClient::close);
    }

    /** The chat page opening chat {@code channelId} as {@code actorAccountId}, e.g. {@code chat-room.html?channelId=...}. */
    static String joinUrl(String chatPageUrl, String channelId, @Nullable String actorAccountId) {
        StringBuilder url = new StringBuilder(chatPageUrl).append("?channelId=").append(encode(channelId));
        if (StringUtils.isNotEmpty(actorAccountId)) {
            url.append("&accountId=").append(encode(actorAccountId));
        }
        // the page signs in to secure-chat unless told otherwise, and a room only admits members of its own workspace
        return url.append("&avatar=human-manager&workspaceId=").append(encode(WORKSPACE_ID)).toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private AgentSessionWsClient newClient(UUID sessionId, AiAgent agent, @Nullable String actorAccountId) {
        PClient client = new PClient(WORKSPACE_ID, agent.getAgentName() + ACCOUNT_DOMAIN, sessionId.toString(), APPLICATION_ID);
        // any device of the actor's account
        ClientFilter actor = StringUtils.isEmpty(actorAccountId) ? null
                : new ClientFilter(WORKSPACE_ID, actorAccountId, null, APPLICATION_ID);
        String channelName = agent.getAgentName() + "_session_" + sessionId;
        return new AgentSessionWsClient(sessionId, agent, client, channelName, actor,
                joinUrl(chatPageUrl, channelName, actorAccountId), chatListener);
    }
}
