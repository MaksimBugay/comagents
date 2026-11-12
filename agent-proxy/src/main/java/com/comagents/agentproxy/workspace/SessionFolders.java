package com.comagents.agentproxy.workspace;

import com.comagents.agentproxy.domain.AiAgent;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a session works on (cloned repository, agent files, agent response) lives in
 * {@code <java.io.tmpdir>/<session id>}.
 */
public final class SessionFolders {

    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"));

    private SessionFolders() {
    }

    public static Path of(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return ROOT.resolve(sessionId.toString());
    }

    /** {@code <session folder>/<agent name>}: the agent's README, run transcript and response. */
    public static Path agentFolder(UUID sessionId, AiAgent agent) {
        return of(sessionId).resolve(agent.getAgentName());
    }

    /** The agent's final answer for the session. */
    public static Path agentResponseFile(UUID sessionId, AiAgent agent) {
        return agentFolder(sessionId, agent).resolve("agent-response-" + sessionId + ".md");
    }
}
