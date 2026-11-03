package com.hubc.magnusblog.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RevisionMediaRefRepository extends JpaRepository<RevisionMediaRef, RevisionMediaRef.PK> {

    List<RevisionMediaRef> findByRevisionId(UUID revisionId);

    void deleteByRevisionId(UUID revisionId);

    boolean existsByMediaId(UUID mediaId);

    /** Media is public when at least one live entry's published snapshot references it. */
    @Query("SELECT COUNT(e) FROM RevisionMediaRef ref, ContentRevision rev, ContentEntry e "
            + "WHERE ref.mediaId = :mediaId AND rev.id = ref.revisionId "
            + "AND e.publishedRevisionId = rev.id AND e.deletedAt IS NULL")
    long countPublicReferences(@Param("mediaId") UUID mediaId);
}
