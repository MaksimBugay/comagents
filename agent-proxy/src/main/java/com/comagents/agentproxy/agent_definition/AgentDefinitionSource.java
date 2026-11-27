package com.comagents.agentproxy.agent_definition;

/**
 * The values of {@code comagents.agent-definition.source.type}, where agent definitions come from; each selects one
 * {@link AgentDefinitionService} implementation. Code never references the constants: implementations are chosen by
 * {@code @Requires(property = "comagents.agent-definition.source.type", value = "...")}, and an annotation value must be a
 * compile-time string, so it repeats the constant's name.
 */
@SuppressWarnings("unused")
public enum AgentDefinitionSource {
    /** The comagents sources on GitHub ({@code AgentDefinitionGithubService}); the default. */
    GIT_HUB
}
