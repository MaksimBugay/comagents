package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the registry against a fake {@code claude} script, so no test calls the real Claude API. */
class ClaudeAgentSessionRegistryTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final String KICK_OFF_PROMPT = "Review the project in the current directory.";

    private static final String SUCCESSFUL_RUN = """
            printf '%s\\n' "$@" > "$(dirname "$0")/args.txt"
            # stdin must be empty, not an open pipe: this would block otherwise
            cat > "$(dirname "$0")/stdin.txt"
            # printf, not echo: sh's echo would turn the \\n escapes inside the JSON into real line breaks
            printf '%s\\n' '{"type":"system","subtype":"init","model":"test-model","tools":["Read"],"mcp_servers":[]}'
            printf '%s\\n' '{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"file_path":"src/Main.java"}}]}}'
            printf '%s\\n' 'a warning on stderr' >&2
            printf '\\n'
            printf '%s\\n' 'null'
            printf '%s\\n' '{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":"# Report\\n\\nAll good"}'
            """;

    @TempDir
    Path scripts;

    @TempDir
    Path workingDirectory;

    private final List<UUID> sessionIds = new ArrayList<>();
    private ClaudeAgentSessionRegistry registry;

    @AfterEach
    void cleanUp() {
        sessionIds.forEach(registry::cleanAllSessionData);
    }

    @Test
    void runsKickOffPromptAndWritesResponse() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();

        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.DONE), session.awaitFinished(WAIT));
        assertEquals(SessionFolders.of(sessionId).resolve("code_review/agent-response-" + sessionId + ".md"), session.getResponseFile());
        assertEquals("# Report\n\nAll good", Files.readString(session.getResponseFile()));
        assertEquals(6, Files.readAllLines(session.getTranscriptFile()).size());
        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertEquals(List.of("-p", KICK_OFF_PROMPT), args.subList(0, 2));
        assertTrue(args.containsAll(List.of("--session-id", sessionId.toString(), "--output-format", "stream-json", "--verbose")));
        assertEquals("test-model", args.get(args.indexOf("--model") + 1));
        assertEquals("high", args.get(args.indexOf("--effort") + 1));
        assertFalse(args.contains("--allowedTools"), "agent.json lists no allowed tools");
        assertEquals("", Files.readString(scripts.resolve("stdin.txt")));
    }

    @Test
    void passesAllowedToolsFromAgentConfig() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"allowedTools": ["Skill", "WebFetch(domain:osv.dev)"], "kickOffPrompt": "Review it"}""");

        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        int allowedTools = args.indexOf("--allowedTools");
        assertEquals(List.of("Skill", "WebFetch(domain:osv.dev)"), args.subList(allowedTools + 1, allowedTools + 3));
    }

    @Test
    void ignoresAllowRulesInProjectSettings() throws Exception {
        Files.createDirectories(workingDirectory.resolve(".claude"));
        Files.writeString(workingDirectory.resolve(".claude/settings.json"), """
                {"permissions": {"allow": ["Bash(rm *)"], "deny": ["Edit"]}}""");
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);

        registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertFalse(args.contains("--allowedTools"), "only agent.json grants tools");
        assertFalse(args.contains("Bash(rm *)"));
    }

    @Test
    void omitsModelAndEffortWhenNotConfigured() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"kickOffPrompt": "Review it"}""");

        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertFalse(args.contains("--model"));
        assertFalse(args.contains("--effort"));
    }

    @Test
    void failsWithoutSuccessfulResult() throws Exception {
        registry = registry(fakeClaude("echo 'boom' >&2; exit 1"), 2, WAIT);

        ClaudeAgentSession session = registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.FAILED), session.awaitFinished(WAIT));
        assertFalse(Files.exists(session.getResponseFile()));
    }

    @Test
    void killsRunningSession() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        registry.killSession(sessionId);

        assertEquals(Optional.of(AgentSessionState.KILLED), session.awaitFinished(WAIT));
    }

    @Test
    void stopsSessionAfterTimeout() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, Duration.ofMillis(500));

        ClaudeAgentSession session = registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.TIMED_OUT), session.awaitFinished(WAIT));
    }

    @Test
    void rejectsSessionsAboveLimitAndFreesSlotWhenFinished() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 1, WAIT.multipliedBy(10));
        UUID first = newSessionId();
        ClaudeAgentSession running = registry.createSession(first, AiAgent.CODE_REVIEW, workingDirectory);

        assertThrows(ClaudeSessionLimitException.class,
                () -> registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory));

        registry.killSession(first);
        running.awaitFinished(WAIT);
        registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory);
    }

    @Test
    void rejectsDuplicateSessionId() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        assertThrows(IllegalStateException.class, () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));
    }

    @Test
    void rejectsAgentWithoutKickOffPrompt() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"model": "test-model", "kickOffPrompt": "  "}""");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));
        assertTrue(e.getMessage().contains("kickOffPrompt"));
        assertTrue(registry.findSession(sessionId).isEmpty());
    }

    @Test
    void rejectsAgentWithoutConfigFile() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        Files.delete(SessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve(ClaudeAgentConfig.FILE_NAME));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));
        assertTrue(e.getMessage().contains("agent.json"));
    }

    @Test
    void cleanAllSessionDataStopsAndDeletesEverything() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        registry.cleanAllSessionData(sessionId);

        assertTrue(registry.findSession(sessionId).isEmpty());
        assertFalse(Files.exists(SessionFolders.of(sessionId)));
    }

    @Test
    void rejectsUnpreparedSession() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = UUID.randomUUID();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));

        assertTrue(e.getMessage().contains("not prepared"));
        assertTrue(registry.findSession(sessionId).isEmpty());
    }

    // what setup does before a session starts: the agent folder exists and holds the agent's agent.json
    private UUID newSessionId() throws IOException {
        UUID sessionId = UUID.randomUUID();
        sessionIds.add(sessionId);
        Files.createDirectories(SessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        writeAgentConfig(sessionId, """
                {"model": "test-model", "effort": "high", "kickOffPrompt": "%s"}""".formatted(KICK_OFF_PROMPT));
        return sessionId;
    }

    private static void writeAgentConfig(UUID sessionId, String json) throws IOException {
        Files.writeString(SessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve(ClaudeAgentConfig.FILE_NAME), json);
    }

    private Path fakeClaude(String body) throws IOException {
        Path script = scripts.resolve("claude");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private static ClaudeAgentSessionRegistry registry(Path claude, int maxConcurrent, Duration timeout) {
        return new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), claude.toString(), maxConcurrent, timeout);
    }
}
