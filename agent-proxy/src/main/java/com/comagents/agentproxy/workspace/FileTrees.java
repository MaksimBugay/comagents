package com.comagents.agentproxy.workspace;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

public final class FileTrees {

    private FileTrees() {
    }

    /**
     * Copies a file, or a folder with everything in it, to {@code target}, creating missing parent folders.
     * Fails if a file already exists at a destination.
     */
    public static void copy(Path source, Path target) {
        // Files.walk visits a directory before its contents, so parents always exist when files are copied
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new UncheckedIOException("Failed to copy " + source + " to " + target,
                    e instanceof UncheckedIOException u ? u.getCause() : (IOException) e);
        }
    }

    /**
     * Deletes a file, or a folder with everything in it, if it exists. Symbolic links are deleted, never followed.
     * Safe to run while another cleanup deletes the same tree: entries that vanish meanwhile are skipped.
     */
    public static void deleteIfExists(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                @Nonnull
                public FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                @Nonnull
                public FileVisitResult visitFileFailed(@Nonnull Path file, @Nonnull IOException e) throws IOException {
                    if (e instanceof NoSuchFileException) {
                        return FileVisitResult.CONTINUE;
                    }
                    throw e;
                }

                // a folder's contents are deleted before the folder itself
                @Override
                @Nonnull
                public FileVisitResult postVisitDirectory(@Nonnull Path directory, IOException e) throws IOException {
                    if (e != null && !(e instanceof NoSuchFileException)) {
                        throw e;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (NoSuchFileException e) {
            // already gone
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + path, e);
        }
    }
}
