package com.comagents.agentorchestrator.controller;

import com.comagents.commons.response.AgentSessionResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The agent sessions' responses, by session id: each is forgotten 5 min after it was appended.
 */
@Singleton
public class AgentSessionResponseRegistry {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Cache<UUID, AgentSessionResponse> responses = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .build();

    public void append(AgentSessionResponse response) {
        Validate.notNull(response, "response must not be null");
        responses.put(response.request().sessionId(), response);
    }

    public void remove(UUID sessionId) {
        responses.invalidate(sessionId);
    }

    public Optional<AgentSessionResponse> get(UUID sessionId) {
        return Optional.ofNullable(responses.getIfPresent(sessionId));
    }
}
