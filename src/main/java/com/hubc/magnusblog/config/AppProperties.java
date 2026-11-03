package com.hubc.magnusblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Admin admin, Upload upload, Site site) {

    public record Storage(Path root, long minFreeBytes) {
    }

    public record Admin(String username) {
    }

    public record Upload(long maxPixels, int maxWidth, int maxHeight, int concurrentJobs) {
    }

    public record Site(String baseUrl) {
    }
}
