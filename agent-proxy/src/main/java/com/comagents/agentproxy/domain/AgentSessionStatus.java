package com.comagents.agentproxy.domain;

import java.time.Instant;
import java.util.Optional;

/** Where an agent run stands, whatever harness runs it. */
public record AgentSessionStatus(AgentSessionState state, Instant startedAt, Optional<Instant> finishedAt) {
}
