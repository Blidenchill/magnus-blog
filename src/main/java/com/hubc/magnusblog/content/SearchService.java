package com.hubc.magnusblog.content;

import com.hubc.magnusblog.common.ContentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified search over published snapshots: title, summary and exact tags.
 * LIKE wildcards are escaped; the HTML body is never searched.
 */
@Service
public class SearchService {

    private static final String WHERE = " FROM ContentEntry e JOIN ContentRevision r ON r.id = e.publishedRevisionId "
            + "WHERE e.deletedAt IS NULL AND e.publishedRevisionId IS NOT NULL "
            + "AND (:type IS NULL OR e.type = :type) "
            + "AND (:tag IS NULL OR :tag MEMBER OF r.tags) "
            + "AND (:q IS NULL OR LOWER(r.title) LIKE :q ESCAPE '\\' "
            + "     OR LOWER(r.summary) LIKE :q ESCAPE '\\' "
            + "     OR :rawq MEMBER OF r.tags) ";

    private final EntityManager entityManager;
    private final ContentService contentService;

    public SearchService(EntityManager entityManager, ContentService contentService) {
        this.entityManager = entityManager;
        this.contentService = contentService;
    }

    @Transactional(readOnly = true)
    public Page<ContentViews.CardView> search(String query, ContentType type, String tag, Pageable pageable) {
        String escaped = escapeLike(query);
        String like = escaped == null ? null
                : "%" + escaped.toLowerCase(java.util.Locale.ROOT) + "%";
        String rawQuery = ContentService.blankToNull(query);
        String tagParam = ContentService.blankToNull(tag);

        TypedQuery<ContentEntry> select = entityManager.createQuery(
                "SELECT e" + WHERE + "ORDER BY e.firstPublishedAt DESC, e.id DESC", ContentEntry.class);
        TypedQuery<Long> count = entityManager.createQuery("SELECT COUNT(e)" + WHERE, Long.class);

        for (TypedQuery<?> q : List.of(select, count)) {
            q.setParameter("type", type);
            q.setParameter("tag", tagParam);
            q.setParameter("q", like);
            q.setParameter("rawq", rawQuery);
        }
        select.setFirstResult((int) pageable.getOffset());
        select.setMaxResults(pageable.getPageSize());
        List<ContentEntry> entries = select.getResultList();
        long total = count.getSingleResult();

        List<UUID> revIds = entries.stream().map(ContentEntry::getPublishedRevisionId).toList();
        Map<UUID, ContentRevision> revisions = contentService.revisionsById(revIds);
        List<ContentViews.CardView> cards = entries.stream()
                .map(e -> contentService.toCard(e, revisions.get(e.getPublishedRevisionId())))
                .toList();
        return new PageImpl<>(cards, pageable, total);
    }

    /** Escape LIKE special characters so user input is matched literally. */
    static String escapeLike(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
