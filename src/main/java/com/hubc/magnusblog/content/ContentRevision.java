package com.hubc.magnusblog.content;

import com.hubc.magnusblog.common.RevisionKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An editable draft or an immutable published snapshot of a {@link ContentEntry}.
 * Module-specific detail rows (photos, recipe, travel) reference this revision.
 */
@Entity
@Table(name = "content_revision")
public class ContentRevision {

    @Id
    private UUID id;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RevisionKind kind;

    @Column(nullable = false, length = 120)
    private String title = "";

    @Column(length = 500)
    private String summary;

    @Column(length = 60)
    private String category;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(nullable = false)
    private boolean featured;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "content_tag", joinColumns = @JoinColumn(name = "revision_id"))
    @Column(name = "tag", length = 24)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public RevisionKind getKind() {
        return kind;
    }

    public void setKind(RevisionKind kind) {
        this.kind = kind;
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

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public UUID getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(UUID coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
