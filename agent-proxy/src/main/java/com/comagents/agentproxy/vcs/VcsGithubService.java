package com.comagents.agentproxy.vcs;

import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.workspace.FileTrees;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * "Clones" a GitHub repository by downloading a snapshot of its default branch through the
 * GitHub REST API zipball endpoint. The result has no {@code .git} directory.
 */
@Singleton
public final class VcsGithubService implements VcsService {

    private static final String GITHUB_API_URL = "https://api.github.com";

    private static final Pattern REPO_URL = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    private final String accessToken;

    private final String comAgentsSourcesUrl;

    // streaming client: the archive is written to disk chunk by chunk instead of buffered in memory
    private final StreamingHttpClient githubClient;

    public VcsGithubService(AgentProxyConf agentProxyConf, @Client(GITHUB_API_URL) StreamingHttpClient githubClient) {
        this.accessToken = agentProxyConf.getSourcesRepoAccessToken();
        this.comAgentsSourcesUrl = agentProxyConf.getComAgentsSourcesUrl();
        this.githubClient = githubClient;
    }

    @Override
    public void clone(String sourcesRepoUrl, String destinationPath) {
        Matcher repo = REPO_URL.matcher(sourcesRepoUrl);
        if (!repo.matches()) {
            throw new IllegalArgumentException("Not a GitHub repository URL: " + sourcesRepoUrl);
        }
        String repoPath = repo.group(1) + "/" + repo.group(2);
        Path destination = Path.of(destinationPath).toAbsolutePath().normalize();

        // the API answers with a redirect to codeload.github.com; the client follows it and drops
        // the Authorization header on the cross-origin hop (the redirect URL carries its own token)
        HttpRequest<?> request = HttpRequest.GET("/repos/" + repoPath + "/zipball")
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.USER_AGENT, "agent-proxy")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bearerAuth(accessToken);
        Path zipball = null;
        try {
            ensureEmptyDirectory(destination);
            zipball = Files.createTempFile("zipball-", ".zip");
            try (OutputStream out = Files.newOutputStream(zipball)) {
                // chunks are released after onNext, so each one is copied out synchronously
                Flux.from(githubClient.dataStream(request))
                        .doOnNext(chunk -> {
                            try {
                                out.write(chunk.toByteArray());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .blockLast();
            }
            try (InputStream in = Files.newInputStream(zipball)) {
                unzip(in, destination);
            }
        } catch (HttpClientResponseException e) {
            throw new IllegalStateException("GitHub returned " + e.code() + " " + e.getStatus().getReason() + " for " + repoPath, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clone " + repoPath + " into " + destination, e);
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException("Failed to clone " + repoPath + " into " + destination, e.getCause());
        } finally {
            if (zipball != null) {
                try {
                    Files.deleteIfExists(zipball);
                } catch (IOException ignored) {
                    // best effort: a leftover temp file must not mask the clone result
                }
            }
        }
    }

    @Override
    public void cloneComAgentsSources(Consumer<Path> sourcesConsumer) {
        // a folder per call: concurrent setups never share or delete each other's sources
        Path sources = createTempDirectory("comagents-");
        try {
            clone(comAgentsSourcesUrl, sources.toString());
            sourcesConsumer.accept(sources);
        } finally {
            FileTrees.deleteIfExists(sources);
        }
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create a temporary folder", e);
        }
    }

    private static boolean hasFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + directory, e);
        }
    }

    // same rule as git clone: never mix a fresh snapshot into existing files
    private static void ensureEmptyDirectory(Path destination) throws IOException {
        if (hasFiles(destination)) {
            throw new IOException("Destination is not empty: " + destination);
        }
        Files.createDirectories(destination);
    }

    private static void unzip(InputStream in, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                // GitHub wraps the snapshot in a single top-level "<owner>-<repo>-<sha>/" directory
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (slash < 0 || slash == name.length() - 1) {
                    continue;
                }
                Path target = destination.resolve(name.substring(slash + 1)).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Zip entry escapes destination: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target);
                }
            }
        }
    }
}
