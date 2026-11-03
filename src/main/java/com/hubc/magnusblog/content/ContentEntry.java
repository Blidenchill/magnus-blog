package com.hubc.magnusblog.content;

import com.hubc.magnusblog.common.ContentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per piece of content. Points at the current draft revision and the
 * current published snapshot; never carries module detail fields itself.
 */
@Entity
@Table(name = "content_entry")
public class ContentEntry {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentType type;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "draft_revision_id")
    private UUID draftRevisionId;

    @Column(name = "published_revision_id")
    private UUID publishedRevisionId;

    @Column(name = "first_published_at")
    private Instant firstPublishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPublished() {
        return publishedRevisionId != null && deletedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ContentType getType() {
        return type;
    }

    public void setType(ContentType type) {
        this.type = type;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public UUID getDraftRevisionId() {
        return draftRevisionId;
    }

    public void setDraftRevisionId(UUID draftRevisionId) {
        this.draftRevisionId = draftRevisionId;
    }

    public UUID getPublishedRevisionId() {
        return publishedRevisionId;
    }

    public void setPublishedRevisionId(UUID publishedRevisionId) {
        this.publishedRevisionId = publishedRevisionId;
    }

    public Instant getFirstPublishedAt() {
        return firstPublishedAt;
    }

    public void setFirstPublishedAt(Instant firstPublishedAt) {
        this.firstPublishedAt = firstPublishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
