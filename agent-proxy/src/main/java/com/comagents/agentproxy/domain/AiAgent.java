package com.comagents.agentproxy.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiAgent {
    CODE_REVIEW(AiHarnessProvider.CLAUDE_CODE, """
            Review the Java project in the current working directory with the java-code-review skill.
            Review the whole project: it is a snapshot of the sources without git history, so there is no diff to review.
            You are running non-interactively and nobody can answer questions: when something is unclear, make the most
            reasonable assumption, list it under Open questions in the report, and continue.
            Return the complete review report, in the format the skill defines, as your final answer.
            """),
    IMAGE_GENERATOR(AiHarnessProvider.CODEX, null);

    private final AiHarnessProvider aiHarnessProvider;

    private final String kickOffPrompt;

    AiAgent(AiHarnessProvider aiHarnessProvider, String kickOffPrompt) {
        this.aiHarnessProvider = aiHarnessProvider;
        this.kickOffPrompt = kickOffPrompt;
    }

    public AiHarnessProvider getAiHarnessProvider() {
        return aiHarnessProvider;
    }

    public String getKickOffPrompt() {
        return kickOffPrompt;
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
