package com.comagents.agentproxy.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTreesTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesFolderWithContents() throws IOException {
        Path folder = tempDir.resolve("comagents");
        Files.createDirectories(folder.resolve("agent/claude_code"));
        Files.writeString(folder.resolve("agent/claude_code/CLAUDE.md"), "role");

        FileTrees.deleteIfExists(folder);

        assertFalse(Files.exists(folder));
    }

    @Test
    void deletesLinkButNotItsTarget() throws IOException {
        Path target = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(target.resolve("keep.txt"), "keep");
        Path folder = Files.createDirectories(tempDir.resolve("folder"));
        Files.createSymbolicLink(folder.resolve("link"), target);

        FileTrees.deleteIfExists(folder);

        assertFalse(Files.exists(folder));
        assertTrue(Files.exists(target.resolve("keep.txt")));
    }

    @Test
    void concurrentDeletesOfTheSameTreeDoNotFail() throws Exception {
        for (int round = 0; round < 20; round++) {
            Path folder = tempDir.resolve("session-" + round);
            for (int i = 0; i < 50; i++) {
                Files.createDirectories(folder.resolve("dir" + i % 5));
                Files.writeString(folder.resolve("dir" + i % 5).resolve("file" + i), "x");
            }
            // e.g. the idle cleanup and the scheduled cleanup after a response fetch, at the same moment
            java.util.concurrent.CompletableFuture<Void> other =
                    java.util.concurrent.CompletableFuture.runAsync(() -> FileTrees.deleteIfExists(folder));
            FileTrees.deleteIfExists(folder);
            other.get();

            assertFalse(Files.exists(folder));
        }
    }

    @Test
    void copiesFolderWithContents() throws IOException {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("a/b"));
        Files.writeString(source.resolve("a/b/file.txt"), "content");

        FileTrees.copy(source, tempDir.resolve("target/copy"));

        assertEquals("content", Files.readString(tempDir.resolve("target/copy/a/b/file.txt")));
    }

    @Test
    void copiesSingleFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("one.txt"), "x");

        FileTrees.copy(file, tempDir.resolve("nested/one.txt"));

        assertTrue(Files.exists(tempDir.resolve("nested/one.txt")));
    }

    @Test
    void ignoresMissingPath() {
        assertDoesNotThrow(() -> FileTrees.deleteIfExists(tempDir.resolve("missing")));
    }
}
