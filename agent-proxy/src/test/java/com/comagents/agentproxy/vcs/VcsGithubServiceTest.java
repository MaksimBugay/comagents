package com.comagents.agentproxy.vcs;

import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.domain.AiHarnessProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Folders of the comagents sources, downloaded from a fake GitHub. */
class VcsGithubServiceTest {

    private static final String CODE_REVIEW = "agent/claude_code/code_review";

    private static EmbeddedServer githubServer;
    private static StreamingHttpClient githubClient;

    @TempDir
    Path comAgentsSources;

    private FakeGithub github;
    private VcsGithubService vcsService;

    @BeforeAll
    static void startGithub() {
        githubServer = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", FakeGithub.SPEC), Environment.TEST);
        githubClient = StreamingHttpClient.create(githubServer.getURL());
    }

    @AfterAll
    static void stopGithub() {
        githubClient.close();
        githubServer.close();
    }

    @BeforeEach
    void createService() {
        github = githubServer.getApplicationContext().getBean(FakeGithub.class);
        github.files.clear();
        github.symlinks.clear();
        github.extraEntries.clear();
        github.requests.clear();
        github.files.putAll(Map.of(
                CODE_REVIEW + "/CLAUDE.md", "role",
                CODE_REVIEW + "/.gitignore", "",
                CODE_REVIEW + "/.claude/rules/read-only.md", "read only",
                "agent/claude_code/weather_forecast/CLAUDE.md", "forecaster",
                "agent-proxy/pom.xml", "<project/>"));
        vcsService = new VcsGithubService(new AgentProxyConf("0.1", "https://github.com/acme/comagents.git", "token",
                "https://pushca.example", "pushca-key", AiHarnessProvider.CLAUDE_CODE,
                new DeviceIdExtractor("test-device", "host")), githubClient);
    }

    @Test
    void downloadsOnlyTheFolderWithItsSubfolders() throws IOException {
        vcsService.downloadComAgentsFolder(comAgentsSources, CODE_REVIEW);

        Path folder = comAgentsSources.resolve(CODE_REVIEW);
        assertEquals("role", Files.readString(folder.resolve("CLAUDE.md")));
        assertEquals("", Files.readString(folder.resolve(".gitignore")));
        assertEquals("read only", Files.readString(folder.resolve(".claude/rules/read-only.md")));
        assertFalse(Files.exists(comAgentsSources.resolve("agent/claude_code/weather_forecast")));
        assertFalse(Files.exists(comAgentsSources.resolve("agent-proxy")));
        assertTrue(github.requests.stream()
                .allMatch(request -> "Bearer token".equals(request.getHeaders().get("Authorization"))));
    }

    @Test
    void symlinksAreSkipped() {
        github.symlinks.add(CODE_REVIEW + "/link.md");

        vcsService.downloadComAgentsFolder(comAgentsSources, CODE_REVIEW);

        assertFalse(Files.exists(comAgentsSources.resolve(CODE_REVIEW + "/link.md")));
    }

    @Test
    void aFolderAlreadyThereIsNotDownloadedAgain() {
        vcsService.downloadComAgentsFolder(comAgentsSources, CODE_REVIEW);
        int requests = github.requests.size();

        vcsService.downloadComAgentsFolder(comAgentsSources, CODE_REVIEW);

        assertEquals(requests, github.requests.size());
    }

    @Test
    void anEntryOutsideTheSourcesFolderIsRejected() {
        github.extraEntries.put(CODE_REVIEW, List.of(new GithubContent("file", "../escaped.md")));
        github.files.put("../escaped.md", "outside");

        assertThrows(UncheckedIOException.class, () -> vcsService.downloadComAgentsFolder(comAgentsSources, CODE_REVIEW));

        assertFalse(Files.exists(comAgentsSources.resolve("../escaped.md").normalize()));
    }

    @Test
    void aMissingFolderFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> vcsService.downloadComAgentsFolder(comAgentsSources, "agent/claude_code/unknown"));

        assertTrue(e.getMessage().contains("404"), e.getMessage());
    }
}
