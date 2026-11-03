package com.hubc.magnusblog.media;

import com.hubc.magnusblog.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Boundary for all filesystem access to media files. Every resolved path is
 * verified to stay inside the configured storage root.
 */
@Component
public class MediaStorage {

    private final Path root;

    public MediaStorage(AppProperties properties) {
        this.root = properties.storage().root().toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public void init() throws IOException {
        Files.createDirectories(root.resolve("media"));
        Files.createDirectories(root.resolve("tmp"));
    }

    /** Resolve a stored relative path, rejecting anything escaping the root. */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("empty storage path");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("path escapes storage root");
        }
        return resolved;
    }

    public Path tmpDir() {
        return root.resolve("tmp");
    }

    public long usableSpace() {
        try {
            init();
            return Files.getFileStore(root).getUsableSpace();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception ignored) {
            // best effort cleanup; orphan files are swept by housekeeping
        }
    }
}
