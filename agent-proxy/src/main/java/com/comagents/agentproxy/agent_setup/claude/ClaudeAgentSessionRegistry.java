package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs Claude Code headless ({@code claude -p}), one child process per session, and keeps track of the runs.
 * Design: {@code spec/claude_q_and_a.md}.
 * <p>
 * A run is started asynchronously in the session's working directory with the agent's kick-off prompt and our
 * session id as Claude's session id, so later runs can {@code --resume} it. Its stream-json output is written to a
 * transcript file and summarised into the log; the final answer is written to
 * {@code <session folder>/<agent name>/agent-response-<session id>.md}.
 */
@Singleton
public class ClaudeAgentSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeAgentSessionRegistry.class);

    // how long a stopped process gets to exit before it is killed forcibly
    private static final Duration STOP_GRACE_PERIOD = Duration.ofSeconds(10);

    private final JsonMapper jsonMapper;
    private final String claudeExecutable;
    private final int maxConcurrentSessions;
    private final Duration sessionTimeout;
    private final Semaphore sessionSlots;
    private final Map<UUID, ClaudeAgentSession> sessions = new ConcurrentHashMap<>();

    public ClaudeAgentSessionRegistry(JsonMapper jsonMapper,
                                      @Value("${claude.executable:claude}") String claudeExecutable,
                                      @Value("${claude.session.max-concurrent:2}") int maxConcurrentSessions,
                                      @Value("${claude.session.timeout:60m}") Duration sessionTimeout) {
        this.jsonMapper = jsonMapper;
        this.claudeExecutable = claudeExecutable;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.sessionTimeout = sessionTimeout;
        this.sessionSlots = new Semaphore(maxConcurrentSessions);
    }

    /**
     * Starts the agent's kick-off prompt in {@code workingDirectory} and returns immediately. Setup must have
     * prepared the session first: the working directory and {@code <session folder>/<agent name>} must exist, and
     * the agent folder must hold the agent's {@code agent.json} (model, effort, kick-off prompt).
     *
     * @throws ClaudeSessionLimitException when all concurrent session slots are busy
     * @throws IllegalStateException       when the session id is already registered or the session is not prepared
     */
    public ClaudeAgentSession createSession(UUID sessionId, AiAgent agent, Path workingDirectory) {
        Path agentFolder = SessionFolders.agentFolder(sessionId, agent);
        requirePrepared(sessionId, workingDirectory, agentFolder);
        List<String> command = command(readConfig(agent, agentFolder), sessionId, readAllowRules(workingDirectory));
        ClaudeAgentSession session = new ClaudeAgentSession(sessionId, agent, workingDirectory,
                agentFolder.resolve("claude-stream.jsonl"), SessionFolders.agentResponseFile(sessionId, agent));
        if (sessions.putIfAbsent(sessionId, session) != null) {
            throw new IllegalStateException("Claude session " + sessionId + " already exists");
        }
        if (!sessionSlots.tryAcquire()) {
            sessions.remove(sessionId);
            throw new ClaudeSessionLimitException(maxConcurrentSessions);
        }
        try {
            session.attach(new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    // nothing is piped in: without this, claude -p waits 3 s for stdin data on every run
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectErrorStream(true)
                    .start());
        } catch (IOException | RuntimeException e) {
            sessions.remove(sessionId);
            sessionSlots.release();
            throw new IllegalStateException("Failed to start Claude Code for session " + sessionId, e);
        }
        LOG.info("[{} {}] started in {}", agent.getAgentName(), sessionId, workingDirectory);
        Thread.ofVirtual().name("claude-output-" + sessionId).start(() -> streamOutput(session));
        Thread.ofVirtual().name("claude-watchdog-" + sessionId).start(() -> enforceTimeout(session));
        return session;
    }

    public Optional<ClaudeAgentSession> findSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /** Stops a running session; does nothing if it is unknown or already finished. */
    public void killSession(UUID sessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session != null && session.transition(AgentSessionState.RUNNING, AgentSessionState.KILLED)) {
            LOG.info("[{} {}] killing", session.getAgent().getAgentName(), sessionId);
            stop(session.process());
        }
    }

    /**
     * Stops the session if it is running, forgets it, and deletes its session folder (cloned repository, agent
     * files, transcript, response) and Claude Code's own record of the conversation, so it can't be resumed.
     */
    public void cleanAllSessionData(UUID sessionId) {
        killSession(sessionId);
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session != null) {
            awaitFinished(session);
            sessions.remove(sessionId);
        }
        FileTrees.deleteIfExists(SessionFolders.of(sessionId));
        deleteClaudeConversation(sessionId);
        LOG.info("[{}] session data deleted", sessionId);
    }

    @PreDestroy
    void killAllSessions() {
        sessions.keySet().forEach(this::killSession);
    }

    private static void requirePrepared(UUID sessionId, Path... folders) {
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                throw new IllegalStateException("Session " + sessionId + " is not prepared: " + folder + " does not exist");
            }
        }
    }

    private List<String> command(ClaudeAgentConfig config, UUID sessionId, List<String> allowRules) {
        List<String> command = new ArrayList<>(List.of(
                claudeExecutable, "-p", config.kickOffPrompt(),
                "--session-id", sessionId.toString(),
                "--output-format", "stream-json", "--verbose",
                // nobody can answer Claude Code's built-in question tool in a headless run
                "--disallowedTools", "AskUserQuestion"));
        if (config.model() != null) {
            command.addAll(List.of("--model", config.model()));
        }
        if (config.effort() != null) {
            command.addAll(List.of("--effort", config.effort()));
        }
        if (!allowRules.isEmpty()) {
            command.add("--allowedTools");
            command.addAll(allowRules);
        }
        return command;
    }

    private ClaudeAgentConfig readConfig(AiAgent agent, Path agentFolder) {
        Path configFile = agentFolder.resolve(ClaudeAgentConfig.FILE_NAME);
        try {
            ClaudeAgentConfig config = jsonMapper.readValue(Files.readAllBytes(configFile), ClaudeAgentConfig.class);
            if (config == null || config.kickOffPrompt() == null || config.kickOffPrompt().isBlank()) {
                throw new IllegalStateException(configFile + " has no kickOffPrompt");
            }
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Agent " + agent.getAgentName() + " has no readable " + configFile, e);
        }
    }

    /**
     * A headless run never trusts the folder it starts in, so Claude Code ignores the {@code permissions.allow}
     * rules of the project's {@code .claude/settings.json} (deny rules still apply). The same rules are passed on the
     * command line instead, where the trust check does not apply.
     */
    private List<String> readAllowRules(Path workingDirectory) {
        Path settingsFile = workingDirectory.resolve(".claude/settings.json");
        if (!Files.isRegularFile(settingsFile)) {
            return List.of();
        }
        try {
            JsonNode settings = jsonMapper.readValue(Files.readAllBytes(settingsFile), JsonNode.class);
            JsonNode permissions = settings == null || !settings.isObject() ? null : settings.get("permissions");
            JsonNode allow = permissions == null || !permissions.isObject() ? null : permissions.get("allow");
            List<String> rules = new ArrayList<>();
            if (allow != null && allow.isArray()) {
                allow.values().forEach(rule -> rules.add(rule.coerceStringValue()));
            }
            return rules;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + settingsFile, e);
        }
    }

    // reading continuously also keeps Claude Code from stalling on a full output pipe
    private void streamOutput(ClaudeAgentSession session) {
        try (BufferedReader output = session.process().inputReader(StandardCharsets.UTF_8);
             BufferedWriter transcript = Files.newBufferedWriter(session.getTranscriptFile(), StandardCharsets.UTF_8)) {
            for (String line; (line = output.readLine()) != null; ) {
                transcript.write(line);
                transcript.newLine();
                transcript.flush();
                handleOutputLine(session, line);
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("[{} {}] reading Claude Code output failed", session.getAgent().getAgentName(), session.getSessionId(), e);
        } finally {
            onSessionFinishedHandler(session.getSessionId());
        }
    }

    private void handleOutputLine(ClaudeAgentSession session, String line) {
        String agentName = session.getAgent().getAgentName();
        Optional<JsonNode> parsed = parseEvent(line);
        if (parsed.isEmpty()) {
            // not an event: stderr is merged into the same stream (warnings, errors)
            LOG.info("[{} {}] {}", agentName, session.getSessionId(), ClaudeStreamEvents.truncate(line));
            return;
        }
        JsonNode event = parsed.get();
        ClaudeStreamEvents.summarize(event)
                .ifPresent(summary -> LOG.info("[{} {}] {}", agentName, session.getSessionId(), summary));
        if (ClaudeStreamEvents.isResult(event)) {
            session.recordResult(ClaudeStreamEvents.resultText(event).orElse(null), ClaudeStreamEvents.isSuccessfulResult(event));
        }
    }

    // empty for lines that are not JSON, and for blank or "null" lines, which parse to no value
    private Optional<JsonNode> parseEvent(String line) {
        try {
            return Optional.ofNullable(jsonMapper.readValue(line, JsonNode.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void enforceTimeout(ClaudeAgentSession session) {
        if (!waitForExit(session.process(), sessionTimeout)
                && session.transition(AgentSessionState.RUNNING, AgentSessionState.TIMED_OUT)) {
            LOG.warn("[{} {}] still running after {}, stopping", session.getAgent().getAgentName(), session.getSessionId(), sessionTimeout);
            stop(session.process());
        }
    }

    // runs once per session, on the output thread, after Claude Code closed its output
    private void onSessionFinishedHandler(UUID sessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        Process process = session.process();
        int exitCode = waitForExit(process, STOP_GRACE_PERIOD) ? process.exitValue() : forceStop(process);
        boolean succeeded = exitCode == 0 && session.isResultSuccessful();
        session.transition(AgentSessionState.RUNNING, succeeded ? AgentSessionState.DONE : AgentSessionState.FAILED);
        session.resultText().ifPresent(text -> writeResponse(session, text));
        sessionSlots.release();
        session.markFinished();
        LOG.info("[{} {}] finished: {}, exit code {}", session.getAgent().getAgentName(), sessionId, session.getState(), exitCode);
    }

    // written to a temporary file and renamed, so a reader never sees a half-written response
    private void writeResponse(ClaudeAgentSession session, String text) {
        Path responseFile = session.getResponseFile();
        Path partial = responseFile.resolveSibling(responseFile.getFileName() + ".partial");
        try {
            Files.writeString(partial, text, StandardCharsets.UTF_8);
            Files.move(partial, responseFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("[{} {}] writing {} failed", session.getAgent().getAgentName(), session.getSessionId(), session.getResponseFile(), e);
        }
    }

    // SIGTERM to Claude Code and the commands it started (mvn, scripts), then SIGKILL after the grace period
    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!waitForExit(process, STOP_GRACE_PERIOD)) {
            forceStop(process);
        }
    }

    private static int forceStop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static boolean waitForExit(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void awaitFinished(ClaudeAgentSession session) {
        try {
            session.awaitFinished(STOP_GRACE_PERIOD.multipliedBy(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Claude Code keeps each conversation (what {@code --resume} reads) as {@code <session id>.jsonl}, plus an
     * optional {@code <session id>/} folder, in a per-project folder under {@code <config dir>/projects}.
     * Matching by file name avoids depending on how project folders are named. Best effort.
     */
    private static void deleteClaudeConversation(UUID sessionId) {
        String configDir = System.getenv("CLAUDE_CONFIG_DIR");
        Path projects = (configDir != null ? Path.of(configDir) : Path.of(System.getProperty("user.home"), ".claude")).resolve("projects");
        if (!Files.isDirectory(projects)) {
            return;
        }
        try (Stream<Path> projectFolders = Files.list(projects)) {
            projectFolders.filter(Files::isDirectory).forEach(project -> {
                FileTrees.deleteIfExists(project.resolve(sessionId + ".jsonl"));
                FileTrees.deleteIfExists(project.resolve(sessionId.toString()));
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("[{}] deleting Claude Code's conversation files failed", sessionId, e);
        }
    }
}
