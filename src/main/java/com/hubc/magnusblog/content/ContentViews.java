package com.hubc.magnusblog.content;

import com.hubc.magnusblog.common.ContentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable view models handed to templates. Entities never cross the controller boundary. */
public final class ContentViews {

    private ContentViews() {
    }

    public record CardView(ContentType type, String slug, String title, String summary,
                           String category, Set<String> tags, Instant publishedAt,
                           String coverThumbUrl, boolean featured) {
    }

    public record AdminRow(UUID id, ContentType type, String slug, String title, String status,
                           Instant firstPublishedAt, Instant updatedAt, long entryVersion,
                           long revisionVersion, boolean hasDraft) {
    }

    public record PhotoItemView(UUID mediaId, String displayUrl, String thumbUrl,
                                String altText, String caption, int sortOrder) {
    }

    public record IngredientView(String name, String amount, int sortOrder) {
    }

    public record StepView(String text, UUID mediaId, String displayUrl, String altText, int sortOrder) {
    }

    public record DestinationView(String name, String region, String summary, String recommendReason,
                                  LocalDate visitDate, String mapUrl, UUID mediaId, String displayUrl,
                                  String altText, int sortOrder) {
    }

    public record RecipeView(String difficulty, Integer durationMinutes, String servings, String intro) {
    }

    /** Full detail for public pages and admin preview/edit. */
    public record DetailView(ContentType type, String slug, String title, String summary,
                             String category, Set<String> tags, boolean featured,
                             Instant publishedAt, String bodyHtml, String coverDisplayUrl,
                             RecipeView recipe, List<IngredientView> ingredients,
                             List<StepView> steps, List<DestinationView> destinations,
                             List<PhotoItemView> photos) {
    }

    /** Everything the admin edit form needs, including version tokens. */
    public record EditView(ContentEntry entry, ContentRevision draft, DetailView detail) {
    }
}
