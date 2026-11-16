package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AgentContextItemAttributes(AgentContextItemType type, String name, boolean required) {
}
