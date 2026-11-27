package com.comagents.agentproxy.vcs;

import java.nio.file.Path;

public sealed interface VcsService permits VcsGithubService {

    /**
     * Clones into {@code <parentFolder>/<repo name>}, the repo name being the last URL path segment without
     * {@code .git}, e.g. {@code https://github.com/acme/agents.git} into a session folder →
     * {@code <session folder>/agents}.
     *
     * @return the folder the repository was cloned into
     * @throws IllegalArgumentException when the URL names no GitHub repository
     */
    Path clone(String sourcesRepoUrl, Path parentFolder);

    /**
     * Downloads one folder of the comagents sources, e.g. {@code agent/claude_code/code_review}, with all its files and
     * subfolders, from the default branch to the same path under {@code comAgentsSources}. A folder already there is
     * not downloaded again.
     */
    void downloadComAgentsFolder(Path comAgentsSources, String folder);

    /**
     * Downloads one file of the comagents sources, e.g. {@code agent/claude_code/code_review/agent.json}, from the
     * default branch.
     *
     * @return the file's content
     */
    byte[] downloadComAgentsFile(String file);
}
