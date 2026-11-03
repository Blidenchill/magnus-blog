package com.hubc.magnusblog.content.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "photo_item")
public class PhotoItem {

    @Id
    private UUID id;

    @Column(name = "revision_id", nullable = false)
    private UUID revisionId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(length = 500)
    private String caption;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(UUID revisionId) {
        this.revisionId = revisionId;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public void setMediaId(UUID mediaId) {
        this.mediaId = mediaId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
}
