package com.comagents.agentproxy.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void ignoresMissingPath() {
        assertDoesNotThrow(() -> FileTrees.deleteIfExists(tempDir.resolve("missing")));
    }
}
