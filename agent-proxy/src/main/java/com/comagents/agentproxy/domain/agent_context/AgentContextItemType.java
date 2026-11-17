package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum AgentContextItemType {
    STRING, FILE, GITHUB_REPOSITORY
}
