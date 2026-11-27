package com.comagents.agentproxy.vcs;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The GitHub REST API contents endpoint of {@code acme/comagents}, in an embedded server of its own: lists a folder
 * ({@code application/vnd.github+json}) or answers a file's raw content ({@code application/vnd.github.raw+json}).
 */
@Requires(property = "spec.name", value = FakeGithub.SPEC)
@Controller("/repos/acme/comagents/contents")
public class FakeGithub {

    public static final String SPEC = "FakeGithub";

    // repository path -> content; a folder is every path below it
    public final Map<String, String> files = new ConcurrentHashMap<>();
    // listed as symlinks wherever they are
    final Set<String> symlinks = ConcurrentHashMap.newKeySet();
    // added to the listing of the folder they name, e.g. an entry whose path leaves the folder
    final Map<String, List<GithubContent>> extraEntries = new ConcurrentHashMap<>();
    final List<HttpRequest<?>> requests = new CopyOnWriteArrayList<>();

    @Get(uri = "/{+path}", produces = MediaType.ALL)
    HttpResponse<?> contents(HttpRequest<?> request, @PathVariable String path) {
        requests.add(request);
        if (request.getHeaders().accept().contains(MediaType.of("application/vnd.github.raw+json"))) {
            String content = files.get(path);
            return content == null ? HttpResponse.notFound() : HttpResponse.ok(content).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        }
        List<GithubContent> listing = listing(path);
        return listing.isEmpty() ? HttpResponse.notFound() : HttpResponse.ok(listing).contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private List<GithubContent> listing(String folder) {
        String prefix = folder + "/";
        Map<String, GithubContent> entries = new TreeMap<>();
        for (String file : files.keySet()) {
            if (file.startsWith(prefix)) {
                int slash = file.indexOf('/', prefix.length());
                String child = slash < 0 ? file : file.substring(0, slash);
                entries.put(child, new GithubContent(slash < 0 ? "file" : "dir", child));
            }
        }
        symlinks.stream().filter(link -> link.startsWith(prefix) && link.indexOf('/', prefix.length()) < 0)
                .forEach(link -> entries.put(link, new GithubContent("symlink", link)));
        List<GithubContent> listing = new CopyOnWriteArrayList<>(entries.values());
        listing.addAll(extraEntries.getOrDefault(folder, List.of()));
        return listing;
    }
}
