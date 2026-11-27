package com.comagents.agentproxy.vcs;

import io.micronaut.core.annotation.Introspected;

/**
 * One entry of a folder listing from the GitHub REST API contents endpoint.
 *
 * @param type {@code file}, {@code dir}, {@code symlink} or {@code submodule}
 * @param path the entry's path in the repository, e.g. {@code agent/claude_code/code_review/CLAUDE.md}
 */
@Introspected
record GithubContent(String type, String path) {
}
