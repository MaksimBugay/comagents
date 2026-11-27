package com.comagents.agentproxy.agent_data;

/**
 * The values of {@code comagents.agent-data.source.type}, where agent data comes from; each selects one
 * {@link AgentDataService} implementation. Code never references the constants: implementations are chosen by
 * {@code @Requires(property = "comagents.agent-data.source.type", value = "...")}, and an annotation value must be a
 * compile-time string, so it repeats the constant's name.
 */
@SuppressWarnings("unused")
public enum AgentDataSource {
    /** The comagents sources on GitHub ({@code AgentDataGithubService}); the default. */
    GIT_HUB
}
