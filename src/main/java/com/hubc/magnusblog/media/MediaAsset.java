package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.MediaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
public class MediaAsset {

    @Id
    private UUID id;

    /** Original client-side filename, display only; never used to build storage paths. */
    @Column(nullable = false, length = 200)
    private String filename = "";

    @Column(nullable = false, length = 60)
    private String mime = "";

    @Column(nullable = false, length = 64)
    private String sha256 = "";

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MediaStatus status = MediaStatus.PROCESSING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public void setStatus(MediaStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
