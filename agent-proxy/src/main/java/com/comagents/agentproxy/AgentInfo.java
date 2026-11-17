package com.comagents.agentproxy;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record AgentInfo(String name, String version, String deviceId) {
}
