package com.comagents.agentproxy.domain;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AiAgentAttributes(@NotNull UUID sessionId, String cloneSourceUrl) {
}
