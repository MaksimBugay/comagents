package com.comagents.agentproxy.domain.agent_context;

import com.comagents.agentproxy.domain.GithubRepositoryUrl;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

/**
 * A GitHub repository, given by its clone URL (the value); its name is {@code <owner>/<repository>} from that URL,
 * so repositories of the same name under different owners are different items. The primary one is
 * cloned as the session's working directory when the context has several.
 */
@Introspected
public final class GithubRepositoryAgentContextItem extends AgentContextItem {

    private final boolean primary;

    /** @throws InvalidAgentContextException when the URL is blank or not a GitHub repository URL */
    @JsonCreator
    public GithubRepositoryAgentContextItem(@JsonProperty("value") String cloneUrl, boolean primary,
                                            @Nullable String description) {
        super(AgentContextItemType.GITHUB_REPOSITORY, fullName(cloneUrl), cloneUrl, description);
        this.primary = primary;
    }

    public GithubRepositoryAgentContextItem(String cloneUrl, boolean primary) {
        this(cloneUrl, primary, null);
    }

    public GithubRepositoryAgentContextItem(String cloneUrl) {
        this(cloneUrl, false);
    }

    public boolean isPrimary() {
        return primary;
    }

    // the item's name: the repository's full name, e.g. acme/kn-challenge
    private static String fullName(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new InvalidAgentContextException("GITHUB_REPOSITORY item has no clone URL");
        }
        try {
            return GithubRepositoryUrl.parse(cloneUrl).fullName();
        } catch (IllegalArgumentException e) {
            throw new InvalidAgentContextException(e.getMessage());
        }
    }
}
