package com.comagents.agentproxy.workspace;

import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a session works on (cloned repository, agent files, Claude Code's state, transcript, response) lives in
 * {@code <sessions dir>/<session id>}. The sessions dir is {@code agent-proxy.sessions-dir}
 * ({@code AGENT_PROXY_SESSIONS_DIR}); put it on persistent storage for sessions to survive restarts.
 */
@Singleton
public class SessionFolders {

    private final Path root;

    // created at startup, so a missing or read-only sessions dir fails the start instead of the first session
    public SessionFolders(@Value("${agent-proxy.sessions-dir:/var/lib/agent-proxy/sessions}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the sessions dir " + this.root, e);
        }
    }

    public Path root() {
        return root;
    }

    public Path of(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return root.resolve(sessionId.toString());
    }

    /** {@code <session folder>/<agent name>}: the agent's README, run transcript and response. */
    public Path agentFolder(UUID sessionId, AiAgent agent) {
        return of(sessionId).resolve(agent.getAgentName());
    }

    /** The agent's final answer for the session. */
    public Path agentResponseFile(UUID sessionId, AiAgent agent) {
        return agentFolder(sessionId, agent).resolve("agent-response-" + sessionId + ".md");
    }
}
