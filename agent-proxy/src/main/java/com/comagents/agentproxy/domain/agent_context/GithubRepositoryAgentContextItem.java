package com.comagents.agentproxy.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GitHub repository, given by its clone URL (the value); its name is the repository name in that URL. The primary one is
 * cloned as the session's working directory when the context has several.
 */
@Serdeable
public final class GithubRepositoryAgentContextItem extends AgentContextItem {

    /** {@code https://github.com/<owner>/<repository>[.git][/]}: groups 1 and 2 are the owner and the repository. */
    public static final Pattern GITHUB_REPOSITORY_URL = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    private final boolean primary;

    /** @throws InvalidAgentContextException when the URL is blank or not a GitHub repository URL */
    @Creator
    public GithubRepositoryAgentContextItem(@JsonProperty("value") String cloneUrl, boolean primary) {
        super(repositoryName(cloneUrl), cloneUrl);
        this.primary = primary;
    }

    public GithubRepositoryAgentContextItem(String cloneUrl) {
        this(cloneUrl, false);
    }

    @Override
    public AgentContextItemType type() {
        return AgentContextItemType.GITHUB_REPOSITORY;
    }

    public boolean isPrimary() {
        return primary;
    }

    /**
     * The repository name in a GitHub clone URL ({@link #GITHUB_REPOSITORY_URL}), e.g. {@code kn-challenge} for
     * {@code https://github.com/acme/kn-challenge.git}.
     *
     * @throws InvalidAgentContextException when the URL is blank or not a GitHub repository URL
     */
    public static String repositoryName(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new InvalidAgentContextException("GITHUB_REPOSITORY item has no clone URL");
        }
        Matcher url = GITHUB_REPOSITORY_URL.matcher(cloneUrl);
        if (!url.matches() || url.group(2).equals(".") || url.group(2).equals("..")) {
            throw new InvalidAgentContextException("Not a GitHub repository URL: " + cloneUrl);
        }
        return url.group(2);
    }
}
