package com.comagents.agentproxy.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a session works on (cloned repository, agent files) lives in
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
}
