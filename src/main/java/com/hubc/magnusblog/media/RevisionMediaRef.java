package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.MediaRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "revision_media_ref")
@IdClass(RevisionMediaRef.PK.class)
public class RevisionMediaRef {

    @Id
    @Column(name = "revision_id")
    private UUID revisionId;

    @Id
    @Column(name = "media_id")
    private UUID mediaId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16)
    private MediaRole role;

    public RevisionMediaRef() {
    }

    public RevisionMediaRef(UUID revisionId, UUID mediaId, MediaRole role) {
        this.revisionId = revisionId;
        this.mediaId = mediaId;
        this.role = role;
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

    public MediaRole getRole() {
        return role;
    }

    public void setRole(MediaRole role) {
        this.role = role;
    }

    public static class PK implements Serializable {
        private UUID revisionId;
        private UUID mediaId;
        private MediaRole role;

        public PK() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(revisionId, pk.revisionId)
                    && Objects.equals(mediaId, pk.mediaId)
                    && role == pk.role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(revisionId, mediaId, role);
        }
    }
}
