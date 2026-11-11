package com.comagents.agentproxy;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AgentInfo(String name, String proxy, String version, String instance) {
}
