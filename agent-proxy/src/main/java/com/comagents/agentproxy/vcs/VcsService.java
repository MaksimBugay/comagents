package com.comagents.agentproxy.vcs;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public interface VcsService {

    void clone(String sourcesRepoUrl, String destinationPath);

    void cloneComAgentsSources();

    /**
     * Clones into {@code <java.io.tmpdir>/<session id>/<repo name>}, the repo name being the last URL
     * path segment without {@code .git}, e.g. {@code https://github.com/acme/agents.git} →
     * {@code /tmp/3f2b…-9c1d/agents}.
     */
    default void clone(String sourcesRepoUrl, UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        clone(sourcesRepoUrl, Path.of(System.getProperty("java.io.tmpdir"), sessionId.toString(), repoName(sourcesRepoUrl)).toString());
    }

    private static String repoName(String sourcesRepoUrl) {
        String path = sourcesRepoUrl.replaceAll("/+$", "");
        String repoName = path.substring(path.lastIndexOf('/') + 1).replaceAll("\\.git$", "");
        if (repoName.isEmpty() || repoName.equals(".") || repoName.equals("..")) {
            throw new IllegalArgumentException("No repository name in URL: " + sourcesRepoUrl);
        }
        return repoName;
    }
}
