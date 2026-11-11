package com.comagents.agentproxy.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiAgent {
    CODE_REVIEW;

    public String getAgentName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AiAgent> fromAgentName(String agentName) {
        return Arrays.stream(values())
                .filter(agent -> agent.getAgentName().equals(agentName))
                .findFirst();
    }
}
