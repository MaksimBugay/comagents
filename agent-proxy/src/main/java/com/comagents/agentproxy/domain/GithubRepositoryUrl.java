package com.comagents.agentproxy.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GitHub repository URL, {@code https://github.com/<owner>/<repository>[.git][/]}: the one rule for which URLs name a
 * GitHub repository, shared by agent contexts and cloning.
 */
public record GithubRepositoryUrl(String owner, String repository) {

    private static final Pattern URL = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    /**
     * @throws IllegalArgumentException when {@code url} is blank or not a GitHub repository URL
     */
    public static GithubRepositoryUrl parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("No GitHub repository URL");
        }
        Matcher matcher = URL.matcher(url);
        if (!matcher.matches() || matcher.group(2).equals(".") || matcher.group(2).equals("..")) {
            throw new IllegalArgumentException("Not a GitHub repository URL: " + url);
        }
        return new GithubRepositoryUrl(matcher.group(1), matcher.group(2));
    }

    /** The repository's full name on GitHub, {@code <owner>/<repository>}, e.g. {@code acme/kn-challenge}. */
    public String fullName() {
        return owner + "/" + repository;
    }
}
