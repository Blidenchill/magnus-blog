package com.hubc.magnusblog.media;

import com.hubc.magnusblog.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageTest {

    @TempDir
    Path tempDir;

    private MediaStorage storage() {
        return new MediaStorage(new AppProperties(
                new AppProperties.Storage(tempDir, 0),
                new AppProperties.Admin("admin"),
                new AppProperties.Upload(10_000_000, 12_000, 12_000, 2),
                new AppProperties.Site("http://localhost:8080")));
    }

    @Test
    void resolvesPathsInsideRoot() {
        Path resolved = storage().resolve("media/2026/11/x/display.jpg");
        assertThat(resolved.startsWith(tempDir)).isTrue();
    }

    @Test
    void rejectsTraversal() {
        MediaStorage storage = storage();
        assertThatThrownBy(() -> storage.resolve("../../etc/passwd"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.resolve("/etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }
}
