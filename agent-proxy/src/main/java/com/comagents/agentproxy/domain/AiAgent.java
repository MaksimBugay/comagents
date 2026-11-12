package com.comagents.agentproxy.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiAgent {
    // how each agent runs (model, effort, kick-off prompt) is in its agent.json in the comagents sources
    CODE_REVIEW(AiHarnessProvider.CLAUDE_CODE),
    IMAGE_GENERATOR(AiHarnessProvider.CODEX);

    private final AiHarnessProvider aiHarnessProvider;

    AiAgent(AiHarnessProvider aiHarnessProvider) {
        this.aiHarnessProvider = aiHarnessProvider;
    }

    public AiHarnessProvider getAiHarnessProvider() {
        return aiHarnessProvider;
    }

    public String getAgentName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AiAgent> fromAgentName(String agentName) {
        return Arrays.stream(values())
                .filter(agent -> agent.getAgentName().equals(agentName))
                .findFirst();
    }
}
