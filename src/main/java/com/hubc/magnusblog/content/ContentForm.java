package com.hubc.magnusblog.content;

import java.util.List;
import java.util.UUID;

/** Flat admin form payload; module-specific structured data arrives as JSON in {@link #detailJson}. */
public class ContentForm {

    private long entryVersion;
    private long revisionVersion;
    private String title;
    private String summary;
    private String category;
    private String slug;
    private String bodyHtml;
    private boolean featured;
    private String tags;       // comma separated
    private UUID coverMediaId;
    private String detailJson; // module structured data

    public List<String> parsedTags() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    public long getEntryVersion() {
        return entryVersion;
    }

    public void setEntryVersion(long entryVersion) {
        this.entryVersion = entryVersion;
    }

    public long getRevisionVersion() {
        return revisionVersion;
    }

    public void setRevisionVersion(long revisionVersion) {
        this.revisionVersion = revisionVersion;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public UUID getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(UUID coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }
}
