package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.FileTrees;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Copies an agent's configuration from the comagents sources, laid out as
 * {@code agent/<harness>/<agent name>/}, into the folder the harness will run in.
 * Every agent must have a {@code README.md}.
 */
final class AgentFiles {

    static final String README = "README.md";

    private AgentFiles() {
    }

    /**
     * Copies the given top-level {@code entries} (files or folders) of
     * {@code <comagents sources>/agent/<harness>/<agent name>} into {@code workingDirectory}.
     * An entry already in {@code workingDirectory} is deleted first, so files the agent no longer has
     * do not linger. Entries the agent does not have are skipped.
     *
     * @throws IllegalStateException when the agent folder or its {@code README.md} is missing
     */
    static void copyInto(Path workingDirectory, Path comAgentsSources, AiAgent agent, List<String> entries) {
        Path source = agentFolder(comAgentsSources, agent);
        for (String entry : entries) {
            Path from = source.resolve(entry);
            if (Files.exists(from)) {
                Path to = workingDirectory.resolve(entry);
                FileTrees.deleteIfExists(to);
                FileTrees.copy(from, to);
            }
        }
    }

    private static Path agentFolder(Path comAgentsSources, AiAgent agent) {
        Path folder = comAgentsSources.resolve("agent")
                .resolve(agent.getAiHarnessProvider().getHarnessName())
                .resolve(agent.getAgentName());
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("No files for agent " + agent.getAgentName() + " in " + folder);
        }
        if (!Files.isRegularFile(folder.resolve(README))) {
            throw new IllegalStateException("Agent " + agent.getAgentName() + " has no " + README + " in " + folder);
        }
        return folder;
    }
}
