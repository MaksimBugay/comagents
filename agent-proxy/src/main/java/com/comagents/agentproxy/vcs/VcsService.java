package com.comagents.agentproxy.vcs;

import com.comagents.agentproxy.workspace.SessionFolders;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public sealed interface VcsService permits VcsGithubService {

    void clone(String sourcesRepoUrl, String destinationPath);

    /**
     * Clones the comagents sources into a temporary folder, hands it to {@code sourcesConsumer}, and
     * deletes it afterwards, so the folder is only valid while the consumer runs.
     */
    void cloneComAgentsSources(Consumer<Path> sourcesConsumer);

    /**
     * Clones into {@code <session folder>/<repo name>} (see {@link SessionFolders}), the repo name being the last URL
     * path segment without {@code .git}, e.g. {@code https://github.com/acme/agents.git} →
     * {@code /tmp/3f2b…-9c1d/agents}.
     *
     * @return the folder the repository was cloned into
     */
    default Path clone(String sourcesRepoUrl, UUID sessionId) {
        Path destination = SessionFolders.of(sessionId).resolve(repoName(sourcesRepoUrl));
        clone(sourcesRepoUrl, destination.toString());
        return destination;
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
