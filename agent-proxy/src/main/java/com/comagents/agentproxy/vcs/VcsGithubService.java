package com.comagents.agentproxy.vcs;

import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.domain.GithubRepositoryUrl;
import io.micronaut.core.type.Argument;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * "Clones" a GitHub repository by downloading a snapshot of its default branch through the
 * GitHub REST API zipball endpoint. The result has no {@code .git} directory. Folders of the comagents sources are
 * downloaded alone, file by file, through the contents endpoint.
 */
@Singleton
public final class VcsGithubService implements VcsService {

    private static final String GITHUB_API_URL = "https://api.github.com";

    private static final String JSON = "application/vnd.github+json";

    private static final String RAW = "application/vnd.github.raw+json";

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
    public Path clone(String sourcesRepoUrl, Path parentFolder) {
        GithubRepositoryUrl repository = GithubRepositoryUrl.parse(sourcesRepoUrl);
        String repoPath = repository.fullName();
        Path destination = parentFolder.resolve(repository.repository()).toAbsolutePath().normalize();

        // the API answers with a redirect to codeload.github.com; the client follows it and drops
        // the Authorization header on the cross-origin hop (the redirect URL carries its own token)
        HttpRequest<?> request = githubRequest("/repos/" + repoPath + "/zipball", JSON);
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
            return destination;
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
    public void downloadComAgentsFolder(Path comAgentsSources, String folder) {
        String repoPath = GithubRepositoryUrl.parse(comAgentsSourcesUrl).fullName();
        Path root = comAgentsSources.toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(insideOf(root, folder))) {
                downloadFolder(repoPath, folder, root);
            }
        } catch (HttpClientResponseException e) {
            throw new IllegalStateException("GitHub returned " + e.code() + " " + e.getStatus().getReason() + " for "
                    + folder + " of " + repoPath, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + folder + " of " + repoPath, e);
        }
    }

    // the contents endpoint lists a folder: each file is fetched raw, each subfolder listed in turn
    private void downloadFolder(String repoPath, String folder, Path root) throws IOException {
        Files.createDirectories(insideOf(root, folder));
        List<GithubContent> entries = githubClient.toBlocking()
                .retrieve(contentsRequest(repoPath, folder, JSON), Argument.listOf(GithubContent.class));
        for (GithubContent entry : entries) {
            switch (entry.type()) {
                case "dir" -> downloadFolder(repoPath, entry.path(), root);
                case "file" -> Files.write(insideOf(root, entry.path()), fileContent(repoPath, entry.path()));
                default -> {
                    // symlinks and submodules are no agent files
                }
            }
        }
    }

    private byte[] fileContent(String repoPath, String file) {
        return githubClient.toBlocking().exchange(contentsRequest(repoPath, file, RAW), byte[].class)
                .getBody().orElseGet(() -> new byte[0]);
    }

    private HttpRequest<?> contentsRequest(String repoPath, String path, String accept) {
        String encodedPath = Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        return githubRequest("/repos/" + repoPath + "/contents/" + encodedPath, accept);
    }

    private HttpRequest<?> githubRequest(String uri, String accept) {
        return HttpRequest.GET(uri)
                .header(HttpHeaders.ACCEPT, accept)
                .header(HttpHeaders.USER_AGENT, "agent-proxy")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .bearerAuth(accessToken);
    }

    // a path from the caller or from GitHub's listing never lands outside the sources folder
    private static Path insideOf(Path root, String path) throws IOException {
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Path escapes " + root + ": " + path);
        }
        return target;
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
