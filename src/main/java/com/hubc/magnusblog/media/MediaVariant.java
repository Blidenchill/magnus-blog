package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.VariantType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "media_variant")
public class MediaVariant {

    @Id
    private UUID id;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant_type", nullable = false, length = 10)
    private VariantType variantType;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** Path relative to the storage root; never contains host or scheme. */
    @Column(nullable = false, length = 300)
    private String path = "";

    @Column(nullable = false, length = 60)
    private String mime = "";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public void setMediaId(UUID mediaId) {
        this.mediaId = mediaId;
    }

    public VariantType getVariantType() {
        return variantType;
    }

    public void setVariantType(VariantType variantType) {
        this.variantType = variantType;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
