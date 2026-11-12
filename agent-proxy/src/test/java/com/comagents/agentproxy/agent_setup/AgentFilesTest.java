package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.request.StartAgentRequest;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFilesTest {

    private static final List<String> ENTRIES = List.of("CLAUDE.md", ".claude", ".mcp.json");

    @TempDir
    Path comAgentsSources;

    @TempDir
    Path workingDirectory;

    @BeforeEach
    void createAgent() throws IOException {
        Path agentSource = comAgentsSources.resolve("agent/claude_code/code_review");
        Files.createDirectories(agentSource.resolve(".claude/skills/java-code-review"));
        Files.writeString(agentSource.resolve("CLAUDE.md"), "role");
        Files.writeString(agentSource.resolve(".claude/skills/java-code-review/SKILL.md"), "skill");
        Files.writeString(agentSource.resolve("README.md"), "agent readme");
    }

    @Test
    void copiesListedEntriesIntoWorkingDirectory() throws IOException {
        AgentFiles.copyInto(workingDirectory, comAgentsSources, AiAgent.CODE_REVIEW, ENTRIES);

        assertEquals("role", Files.readString(workingDirectory.resolve("CLAUDE.md")));
        assertEquals("skill", Files.readString(workingDirectory.resolve(".claude/skills/java-code-review/SKILL.md")));
    }

    @Test
    void leavesUnlistedAndMissingEntriesAlone() throws IOException {
        Files.writeString(workingDirectory.resolve("README.md"), "repo readme");

        AgentFiles.copyInto(workingDirectory, comAgentsSources, AiAgent.CODE_REVIEW, ENTRIES);

        assertEquals("repo readme", Files.readString(workingDirectory.resolve("README.md")));
        assertFalse(Files.exists(workingDirectory.resolve(".mcp.json")));
    }

    @Test
    void replacesExistingFiles() throws IOException {
        Files.writeString(workingDirectory.resolve("CLAUDE.md"), "repo instructions");

        AgentFiles.copyInto(workingDirectory, comAgentsSources, AiAgent.CODE_REVIEW, ENTRIES);

        assertEquals("role", Files.readString(workingDirectory.resolve("CLAUDE.md")));
    }

    @Test
    void removesFilesTheAgentNoLongerHas() throws IOException {
        Path staleSkill = workingDirectory.resolve(".claude/skills/removed-skill/SKILL.md");
        Files.createDirectories(staleSkill.getParent());
        Files.writeString(staleSkill, "stale");

        AgentFiles.copyInto(workingDirectory, comAgentsSources, AiAgent.CODE_REVIEW, ENTRIES);

        assertFalse(Files.exists(staleSkill));
        assertTrue(Files.exists(workingDirectory.resolve(".claude/skills/java-code-review/SKILL.md")));
    }

    @Test
    void createsMissingWorkingDirectory() {
        Path sessionFolder = workingDirectory.resolve("not-yet-created");

        AgentFiles.copyInto(sessionFolder, comAgentsSources, AiAgent.CODE_REVIEW, ENTRIES);

        assertTrue(Files.exists(sessionFolder.resolve("CLAUDE.md")));
    }

    @Test
    void failsWhenAgentHasNoReadme() throws IOException {
        Files.delete(comAgentsSources.resolve("agent/claude_code/code_review/README.md"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AgentFiles.copyInto(workingDirectory, comAgentsSources, AiAgent.CODE_REVIEW, ENTRIES));
        assertTrue(e.getMessage().contains("README.md"));
    }

    @Test
    void copyingReadmeCreatesAgentFolder() {
        Path agentFolder = workingDirectory.resolve("session/code_review");

        AgentFiles.copyInto(agentFolder, comAgentsSources, AiAgent.CODE_REVIEW, List.of(AgentFiles.README));

        assertTrue(Files.isRegularFile(agentFolder.resolve("README.md")));
    }

    @Test
    void failsWhenAgentHasNoFiles() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AgentFiles.copyInto(workingDirectory, comAgentsSources, AiAgent.IMAGE_GENERATOR, ENTRIES));
        assertTrue(e.getMessage().contains("image_generator"));
    }

    @Test
    void requestSample() throws IOException {
        var request = new StartAgentRequest(
                UUID.randomUUID(),
                AiAgent.CODE_REVIEW,
                "https://github.com/MaksimBugay/dynamic-captcha.git"
        );
        ObjectMapper objectMapper = ObjectMapper.getDefault();

        String json = objectMapper.writeValueAsString(request);
        System.out.println(json);

        // the printed body is what POST /{agentName}/start accepts
        assertEquals(request, objectMapper.readValue(json, StartAgentRequest.class));
    }
}
