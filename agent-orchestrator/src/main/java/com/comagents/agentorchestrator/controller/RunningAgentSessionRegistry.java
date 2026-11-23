package com.comagents.agentorchestrator.controller;

import com.comagents.commons.domain.AgentSessionAttributes;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The agent sessions started by this orchestrator, by session id: each is forgotten
 * {@code agent-orchestrator.running-session-ttl} (60 min by default, an executor's {@code claude.session.timeout})
 * after it was appended.
 */
@Singleton
public class RunningAgentSessionRegistry {

    private final Cache<UUID, AgentSessionAttributes> sessions;

    public RunningAgentSessionRegistry(@Value("${agent-orchestrator.running-session-ttl:60m}") Duration ttl) {
        this.sessions = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .build();
    }

    public void append(UUID sessionId, AgentSessionAttributes attributes) {
        Validate.notNull(sessionId, "sessionId must not be null");
        Validate.notNull(attributes, "attributes must not be null");
        sessions.put(sessionId, attributes);
    }

    public void remove(UUID sessionId) {
        sessions.invalidate(sessionId);
    }

    public Optional<AgentSessionAttributes> get(UUID sessionId) {
        return Optional.ofNullable(sessions.getIfPresent(sessionId));
    }
}
