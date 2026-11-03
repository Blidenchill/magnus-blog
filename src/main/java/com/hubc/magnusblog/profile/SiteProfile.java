package com.hubc.magnusblog.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "site_profile")
public class SiteProfile {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name = "";

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(length = 500)
    private String contact;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public UUID getAvatarMediaId() {
        return avatarMediaId;
    }

    public void setAvatarMediaId(UUID avatarMediaId) {
        this.avatarMediaId = avatarMediaId;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
