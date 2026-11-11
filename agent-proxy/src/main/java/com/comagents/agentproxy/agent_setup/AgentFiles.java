package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.FileTrees;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies an agent's configuration from the comagents sources, laid out as
 * {@code agent/<harness>/<agent name>/}, into the folder the harness will run in.
 */
final class AgentFiles {

    private AgentFiles() {
    }

    /**
     * Copies the given top-level {@code entries} (files or folders) of
     * {@code <comagents sources>/agent/<harness>/<agent name>} into {@code workingDirectory}.
     * An entry already in {@code workingDirectory} is deleted first, so files the agent no longer has
     * do not linger. Entries the agent does not have are skipped.
     */
    static void copyInto(Path workingDirectory, Path comAgentsSources, AiAgent agent, List<String> entries) {
        Path source = agentFolder(comAgentsSources, agent);
        try {
            for (String entry : entries) {
                Path from = source.resolve(entry);
                if (Files.exists(from)) {
                    Path to = workingDirectory.resolve(entry);
                    FileTrees.deleteIfExists(to);
                    copy(from, to);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy files of agent " + agent.getAgentName() + " into " + workingDirectory, e);
        }
    }

    private static Path agentFolder(Path comAgentsSources, AiAgent agent) {
        Path folder = comAgentsSources.resolve("agent")
                .resolve(agent.getAiHarnessProvider().getHarnessName())
                .resolve(agent.getAgentName());
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("No files for agent " + agent.getAgentName() + " in " + folder);
        }
        return folder;
    }

    // Files.walk visits a directory before its contents, so parents always exist when files are copied
    private static void copy(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }
}
