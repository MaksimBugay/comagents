package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * After a restart, deletes the sessions it left behind (see {@link ClaudeAgentSessionRegistry#deleteLeftoverSessions})
 * together with their chats. Archived sessions stay for a resume, see {@link ArchivedSessionChats}.
 */
@Singleton
public class LeftoverSessions implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(LeftoverSessions.class);

    private final ClaudeAgentSessionRegistry claudeAgentSessionRegistry;
    private final AgentSessionWsClientRegistry agentSessionWsClientRegistry;

    public LeftoverSessions(ClaudeAgentSessionRegistry claudeAgentSessionRegistry,
                            AgentSessionWsClientRegistry agentSessionWsClientRegistry) {
        this.claudeAgentSessionRegistry = claudeAgentSessionRegistry;
        this.agentSessionWsClientRegistry = agentSessionWsClientRegistry;
    }

    // the folders go right away, before the server takes requests; the chats in the background: startup does not
    // wait for Pushca
    @SuppressWarnings("NullableProblems")
    @Override
    public void onApplicationEvent(StartupEvent event) {
        Map<UUID, AiAgent> deleted = claudeAgentSessionRegistry.deleteLeftoverSessions();
        if (!deleted.isEmpty()) {
            LOG.info("Deleted {} sessions left behind by a restart, removing their chats", deleted.size());
            Thread.ofVirtual().name("leftover-session-chats").start(() -> removeChats(deleted));
        }
    }

    void removeChats(Map<UUID, AiAgent> sessions) {
        sessions.forEach(agentSessionWsClientRegistry::removeChat);
    }
}
