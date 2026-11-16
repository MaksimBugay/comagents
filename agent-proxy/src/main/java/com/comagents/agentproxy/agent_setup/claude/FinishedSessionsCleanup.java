package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes finished sessions, with their chats, {@code agent-response.retention} (1 hour by default) after they
 * finished, even if nobody fetched their response; checked every minute. A fetched response goes sooner
 * ({@code agent-response.cleanup-delay}); archived sessions are kept for a resume.
 */
@Singleton
public class FinishedSessionsCleanup {

    private final ClaudeAgentSessionRegistry claudeAgentSessionRegistry;
    private final Duration retention;

    public FinishedSessionsCleanup(ClaudeAgentSessionRegistry claudeAgentSessionRegistry,
                                   @Value("${agent-response.retention:1h}") Duration retention) {
        this.claudeAgentSessionRegistry = claudeAgentSessionRegistry;
        this.retention = retention;
    }

    @Scheduled(fixedDelay = "1m", initialDelay = "1m")
    void deleteExpiredSessions() {
        claudeAgentSessionRegistry.deleteSessionsFinishedBefore(Instant.now().minus(retention));
    }
}
