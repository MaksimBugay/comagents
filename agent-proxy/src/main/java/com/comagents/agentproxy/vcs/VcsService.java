package com.comagents.agentproxy.vcs;

import java.nio.file.Path;
import java.util.function.Consumer;

public sealed interface VcsService permits VcsGithubService {

    void clone(String sourcesRepoUrl, String destinationPath);

    /**
     * Clones the comagents sources into a temporary folder, hands it to {@code sourcesConsumer}, and
     * deletes it afterwards, so the folder is only valid while the consumer runs.
     */
    void cloneComAgentsSources(Consumer<Path> sourcesConsumer);

    /**
     * Clones into {@code <parentFolder>/<repo name>}, the repo name being the last URL path segment without
     * {@code .git}, e.g. {@code https://github.com/acme/agents.git} into a session folder →
     * {@code <session folder>/agents}.
     *
     * @return the folder the repository was cloned into
     */
    default Path clone(String sourcesRepoUrl, Path parentFolder) {
        Path destination = parentFolder.resolve(repoName(sourcesRepoUrl));
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
