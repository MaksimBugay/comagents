package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.agent_setup.claude.ClaudeSessionArchive;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After a restart, reconnects the chats of the archived sessions on disk, so their chats keep receiving messages
 * while the sessions wait to be resumed. Runs in the background: startup does not wait for Pushca.
 */
@Singleton
public class ArchivedSessionChats implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ArchivedSessionChats.class);

    private final ClaudeAgentSessionRegistry claudeAgentSessionRegistry;
    private final AgentSessionWsClientRegistry agentSessionWsClientRegistry;

    public ArchivedSessionChats(ClaudeAgentSessionRegistry claudeAgentSessionRegistry,
                                AgentSessionWsClientRegistry agentSessionWsClientRegistry) {
        this.claudeAgentSessionRegistry = claudeAgentSessionRegistry;
        this.agentSessionWsClientRegistry = agentSessionWsClientRegistry;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Thread.ofVirtual().name("archived-session-chats").start(this::reopenAll);
    }

    /** Reopens the chat of every archived session; returns how many are connected again. */
    int reopenAll() {
        int reopened = 0;
        int archived = 0;
        for (ClaudeSessionArchive archive : claudeAgentSessionRegistry.findArchives()) {
            archived++;
            if (agentSessionWsClientRegistry.reopenConnection(archive.sessionId(), archive.agent(), archive.actorAccountId())) {
                reopened++;
            }
        }
        if (archived > 0) {
            LOG.info("Reopened the chats of {} of {} archived sessions", reopened, archived);
        }
        return reopened;
    }
}
