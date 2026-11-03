package com.hubc.magnusblog.content;

import com.hubc.magnusblog.common.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContentEntryRepository extends JpaRepository<ContentEntry, UUID> {

    boolean existsBySlug(String slug);

    Optional<ContentEntry> findBySlug(String slug);

    /** Public detail: only entries with a live published snapshot. */
    @Query("SELECT e FROM ContentEntry e WHERE e.slug = :slug AND e.type = :type "
            + "AND e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL")
    Optional<ContentEntry> findPublicBySlug(@Param("type") ContentType type, @Param("slug") String slug);

    /** Public list page, newest first; optional tag filter. */
    @Query(value = "SELECT e FROM ContentEntry e JOIN ContentRevision r ON r.id = e.publishedRevisionId "
            + "WHERE e.type = :type AND e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL "
            + "AND (:tag IS NULL OR :tag MEMBER OF r.tags) "
            + "AND (:category IS NULL OR r.category = :category) "
            + "ORDER BY e.firstPublishedAt DESC, e.id DESC",
            countQuery = "SELECT COUNT(e) FROM ContentEntry e JOIN ContentRevision r ON r.id = e.publishedRevisionId "
            + "WHERE e.type = :type AND e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL "
            + "AND (:tag IS NULL OR :tag MEMBER OF r.tags) "
            + "AND (:category IS NULL OR r.category = :category)")
    Page<ContentEntry> findPublicPage(@Param("type") ContentType type,
                                      @Param("tag") String tag,
                                      @Param("category") String category,
                                      Pageable pageable);

    /** Admin list: filter by lifecycle status. status in ALL / DRAFT / PUBLISHED / TRASH */
    @Query(value = "SELECT e FROM ContentEntry e WHERE (:type IS NULL OR e.type = :type) "
            + "AND (:status = 'ALL' "
            + "     OR (:status = 'TRASH' AND e.deletedAt IS NOT NULL) "
            + "     OR (:status = 'PUBLISHED' AND e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL) "
            + "     OR (:status = 'DRAFT' AND e.deletedAt IS NULL AND e.publishedRevisionId IS NULL)) "
            + "ORDER BY e.createdAt DESC, e.id DESC",
            countQuery = "SELECT COUNT(e) FROM ContentEntry e WHERE (:type IS NULL OR e.type = :type) "
            + "AND (:status = 'ALL' "
            + "     OR (:status = 'TRASH' AND e.deletedAt IS NOT NULL) "
            + "     OR (:status = 'PUBLISHED' AND e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL) "
            + "     OR (:status = 'DRAFT' AND e.deletedAt IS NULL AND e.publishedRevisionId IS NULL))")
    Page<ContentEntry> findAdminPage(@Param("type") ContentType type,
                                     @Param("status") String status,
                                     Pageable pageable);

    @Query(value = "SELECT e FROM ContentEntry e WHERE e.deletedAt IS NOT NULL "
            + "AND (:type IS NULL OR e.type = :type) ORDER BY e.deletedAt DESC, e.id DESC",
            countQuery = "SELECT COUNT(e) FROM ContentEntry e WHERE e.deletedAt IS NOT NULL "
            + "AND (:type IS NULL OR e.type = :type)")
    Page<ContentEntry> findTrashPage(@Param("type") ContentType type, Pageable pageable);

    long countByDeletedAtIsNull();

    @Query("SELECT COUNT(e) FROM ContentEntry e WHERE e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL")
    long countPublished();

    @Query("SELECT COUNT(e) FROM ContentEntry e WHERE e.deletedAt IS NOT NULL")
    long countTrashed();
}
