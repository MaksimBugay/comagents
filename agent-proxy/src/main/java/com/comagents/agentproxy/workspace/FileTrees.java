package com.comagents.agentproxy.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class FileTrees {

    private FileTrees() {
    }

    /**
     * Deletes a file, or a folder with everything in it, if it exists. Symbolic links are deleted,
     * never followed.
     */
    public static void deleteIfExists(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        // reverse order: a folder's contents are deleted before the folder itself
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path entry : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + path, e);
        }
    }
}
