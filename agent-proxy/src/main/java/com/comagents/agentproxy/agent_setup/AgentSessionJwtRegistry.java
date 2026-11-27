package com.comagents.agentproxy.agent_setup;

import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The JWT of each agent session, which agent-proxy attaches to the session's outgoing calls (LLM, MCP tools). Kept in
 * memory only: never written to disk, logged, or handed to the harness, so the agent never sees it.
 */
@Singleton
public class AgentSessionJwtRegistry {

    private final Map<UUID, String> jwts = new ConcurrentHashMap<>();

    /** Stores the session's JWT, replacing the one it had. */
    public void store(UUID sessionId, String jwt) {
        Validate.notNull(sessionId, "sessionId must not be null");
        Validate.notBlank(jwt, "jwt must not be blank");
        jwts.put(sessionId, jwt);
    }

    /** The session's JWT, or empty when none was stored. */
    public Optional<String> get(UUID sessionId) {
        return Optional.ofNullable(jwts.get(sessionId));
    }

    /** Forgets the session's JWT; nothing happens when none was stored. */
    public void remove(UUID sessionId) {
        jwts.remove(sessionId);
    }
}
