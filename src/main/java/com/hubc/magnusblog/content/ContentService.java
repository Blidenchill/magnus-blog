package com.hubc.magnusblog.content;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hubc.magnusblog.common.ConflictException;
import com.hubc.magnusblog.common.ContentType;
import com.hubc.magnusblog.common.HtmlSanitizer;
import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.common.MediaRole;
import com.hubc.magnusblog.common.NotFoundException;
import com.hubc.magnusblog.common.RevisionKind;
import com.hubc.magnusblog.common.SlugUtil;
import com.hubc.magnusblog.common.VariantType;
import com.hubc.magnusblog.content.ContentViews.CardView;
import com.hubc.magnusblog.content.ContentViews.DestinationView;
import com.hubc.magnusblog.content.ContentViews.DetailView;
import com.hubc.magnusblog.content.ContentViews.EditView;
import com.hubc.magnusblog.content.ContentViews.IngredientView;
import com.hubc.magnusblog.content.ContentViews.PhotoItemView;
import com.hubc.magnusblog.content.ContentViews.RecipeView;
import com.hubc.magnusblog.content.ContentViews.StepView;
import com.hubc.magnusblog.content.food.RecipeDetail;
import com.hubc.magnusblog.content.food.RecipeDetailRepository;
import com.hubc.magnusblog.content.food.RecipeIngredient;
import com.hubc.magnusblog.content.food.RecipeIngredientRepository;
import com.hubc.magnusblog.content.food.RecipeStep;
import com.hubc.magnusblog.content.food.RecipeStepRepository;
import com.hubc.magnusblog.content.photo.PhotoItem;
import com.hubc.magnusblog.content.photo.PhotoItemRepository;
import com.hubc.magnusblog.content.travel.TravelDestination;
import com.hubc.magnusblog.content.travel.TravelDestinationRepository;
import com.hubc.magnusblog.media.MediaService;
import com.hubc.magnusblog.media.RevisionMediaRef;
import com.hubc.magnusblog.media.RevisionMediaRefRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Draft editing: create entries, save drafts, build views for admin and public pages. */
@Service
public class ContentService {

    public static final int MAX_TAGS = 8;
    public static final int MAX_TAG_LENGTH = 24;
    public static final int MAX_IMAGES_PER_ENTRY = 15;

    private final ContentEntryRepository entryRepository;
    private final ContentRevisionRepository revisionRepository;
    private final PhotoItemRepository photoItemRepository;
    private final RecipeDetailRepository recipeDetailRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final TravelDestinationRepository destinationRepository;
    private final RevisionMediaRefRepository refRepository;
    private final HtmlSanitizer sanitizer;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    public ContentService(ContentEntryRepository entryRepository,
                          ContentRevisionRepository revisionRepository,
                          PhotoItemRepository photoItemRepository,
                          RecipeDetailRepository recipeDetailRepository,
                          RecipeIngredientRepository ingredientRepository,
                          RecipeStepRepository stepRepository,
                          TravelDestinationRepository destinationRepository,
                          RevisionMediaRefRepository refRepository,
                          HtmlSanitizer sanitizer,
                          MediaService mediaService,
                          ObjectMapper objectMapper) {
        this.entryRepository = entryRepository;
        this.revisionRepository = revisionRepository;
        this.photoItemRepository = photoItemRepository;
        this.recipeDetailRepository = recipeDetailRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.destinationRepository = destinationRepository;
        this.refRepository = refRepository;
        this.sanitizer = sanitizer;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public UUID create(ContentType type) {
        ContentEntry entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setType(type);
        entry.setSlug(uniqueSlug(type.name().toLowerCase(java.util.Locale.ROOT)));
        entryRepository.save(entry);

        ContentRevision draft = new ContentRevision();
        draft.setId(UUID.randomUUID());
        draft.setEntryId(entry.getId());
        draft.setKind(RevisionKind.DRAFT);
        revisionRepository.save(draft);

        entry.setDraftRevisionId(draft.getId());
        entryRepository.save(entry);
        return entry.getId();
    }

    private String uniqueSlug(String prefix) {
        for (int i = 0; i < 10; i++) {
            String candidate = SlugUtil.randomSlug(prefix);
            if (!entryRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        return SlugUtil.randomSlug(prefix + UUID.randomUUID().toString().substring(0, 4));
    }

    // ---------------------------------------------------------------- save draft

    @Transactional
    public void saveDraft(UUID entryId, ContentForm form) {
        ContentEntry entry = loadEditableEntry(entryId);
        checkEntryVersion(entry, form.getEntryVersion());

        ContentRevision draft = loadDraft(entry);
        if (draft.getVersion() != form.getRevisionVersion()) {
            throw new ConflictException("草稿已被其他窗口修改，请刷新后再保存");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        validateLengths(form, errors);
        List<String> tags = form.parsedTags();
        if (tags.size() > MAX_TAGS) {
            errors.put("tags", "标签最多 " + MAX_TAGS + " 个");
        }
        for (String tag : tags) {
            if (tag.length() > MAX_TAG_LENGTH) {
                errors.put("tags", "单个标签最多 " + MAX_TAG_LENGTH + " 个字符");
                break;
            }
        }
        JsonNode detail = parseDetailJson(form.getDetailJson(), errors);
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }

        HtmlSanitizer.Sanitized sanitized = sanitizer.sanitize(form.getBodyHtml());

        // Slug may only change before first publish.
        if (entry.getFirstPublishedAt() == null && form.getSlug() != null && !form.getSlug().isBlank()) {
            String normalized = SlugUtil.normalize(form.getSlug());
            if (normalized == null || !SlugUtil.isValid(normalized)) {
                throw new InvalidInputException("slug", "地址标识只能包含小写字母、数字和连字符（1-80 字符）");
            }
            if (!normalized.equals(entry.getSlug()) && entryRepository.existsBySlug(normalized)) {
                throw new InvalidInputException("slug", "该地址标识已被占用");
            }
            entry.setSlug(normalized);
        }

        draft.setTitle(trimToNull(form.getTitle()) == null ? "" : form.getTitle().trim());
        draft.setSummary(trimToNull(form.getSummary()));
        draft.setCategory(trimToNull(form.getCategory()));
        draft.setBodyHtml(sanitized.html());
        draft.setFeatured(form.isFeatured());
        draft.setCoverMediaId(form.getCoverMediaId());
        draft.setTags(new LinkedHashSet<>(tags));
        draft.setUpdatedAt(Instant.now());

        Set<UUID> referencedMedia = new LinkedHashSet<>();
        if (form.getCoverMediaId() != null) {
            referencedMedia.add(form.getCoverMediaId());
        }
        referencedMedia.addAll(sanitized.mediaIds());

        switch (entry.getType()) {
            case PHOTO -> replacePhotoItems(draft.getId(), detail, referencedMedia);
            case FOOD -> replaceRecipe(draft.getId(), detail, referencedMedia);
            case TRAVEL -> replaceDestinations(draft.getId(), detail, referencedMedia);
            case BLOG -> { /* body only */ }
        }

        mediaService.assertReady(referencedMedia);
        revisionRepository.save(draft);
        syncRefs(draft.getId(), entry.getType(), form.getCoverMediaId(), sanitized.mediaIds(), detail);
        entryRepository.save(entry);
    }

    // ---------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public EditView loadForEdit(UUID entryId) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        if (entry.isDeleted()) {
            throw new ConflictException("回收站中的内容请先在回收站恢复");
        }
        ContentRevision draft = loadDraft(entry);
        DetailView detail = buildDetail(entry, draft, true);
        return new EditView(entry, draft, detail);
    }

    /** Draft view for the admin preview page. */
    @Transactional(readOnly = true)
    public DetailView loadDraftPreview(UUID entryId) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        ContentRevision draft = loadDraft(entry);
        return buildDetail(entry, draft, true);
    }

    /** Published snapshot for public detail pages; 404 when not publicly visible. */
    @Transactional(readOnly = true)
    public DetailView loadPublicDetail(ContentType type, String slug) {
        ContentEntry entry = entryRepository.findPublicBySlug(type, slug)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        ContentRevision published = revisionRepository.findById(entry.getPublishedRevisionId())
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        return buildDetail(entry, published, false);
    }

    @Transactional(readOnly = true)
    public Page<CardView> publicPage(ContentType type, String tag, String category, Pageable pageable) {
        Page<ContentEntry> page = entryRepository.findPublicPage(type, blankToNull(tag), blankToNull(category), pageable);
        List<UUID> revIds = page.getContent().stream().map(ContentEntry::getPublishedRevisionId).toList();
        Map<UUID, ContentRevision> revisions = revisionsById(revIds);
        return page.map(e -> toCard(e, revisions.get(e.getPublishedRevisionId())));
    }

    @Transactional(readOnly = true)
    public Page<ContentViews.AdminRow> adminPage(ContentType type, String status, Pageable pageable) {
        Page<ContentEntry> page = entryRepository.findAdminPage(type, status == null ? "ALL" : status, pageable);
        List<UUID> revIds = new ArrayList<>();
        for (ContentEntry e : page.getContent()) {
            if (e.getDraftRevisionId() != null) {
                revIds.add(e.getDraftRevisionId());
            }
        }
        Map<UUID, ContentRevision> drafts = revisionsById(revIds);
        return page.map(e -> {
            ContentRevision draft = drafts.get(e.getDraftRevisionId());
            String statusLabel = e.isDeleted() ? "回收站"
                    : (e.isPublished() ? "已发布" : "草稿");
            return new ContentViews.AdminRow(e.getId(), e.getType(), e.getSlug(),
                    draft != null ? draft.getTitle() : "",
                    statusLabel, e.getFirstPublishedAt(),
                    draft != null ? draft.getUpdatedAt() : e.getCreatedAt(),
                    e.getVersion(), draft != null ? draft.getVersion() : 0,
                    e.getDraftRevisionId() != null);
        });
    }

    @Transactional(readOnly = true)
    public Page<ContentViews.AdminRow> trashPage(ContentType type, Pageable pageable) {
        Page<ContentEntry> page = entryRepository.findTrashPage(type, pageable);
        List<UUID> revIds = new ArrayList<>();
        for (ContentEntry e : page.getContent()) {
            if (e.getDraftRevisionId() != null) {
                revIds.add(e.getDraftRevisionId());
            }
        }
        Map<UUID, ContentRevision> drafts = revisionsById(revIds);
        return page.map(e -> {
            ContentRevision draft = drafts.get(e.getDraftRevisionId());
            return new ContentViews.AdminRow(e.getId(), e.getType(), e.getSlug(),
                    draft != null ? draft.getTitle() : "", "回收站", e.getFirstPublishedAt(),
                    e.getDeletedAt(), e.getVersion(), 0, false);
        });
    }

    // ---------------------------------------------------------------- helpers used by PublishingService

    ContentEntry loadEditableEntry(UUID entryId) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        if (entry.isDeleted()) {
            throw new ConflictException("回收站中的内容不能编辑");
        }
        return entry;
    }

    void checkEntryVersion(ContentEntry entry, long expected) {
        if (entry.getVersion() != expected) {
            throw new ConflictException("内容已被其他窗口修改，请刷新后重试");
        }
    }

    ContentRevision loadDraft(ContentEntry entry) {
        if (entry.getDraftRevisionId() == null) {
            ContentRevision draft = new ContentRevision();
            draft.setId(UUID.randomUUID());
            draft.setEntryId(entry.getId());
            draft.setKind(RevisionKind.DRAFT);
            revisionRepository.save(draft);
            entry.setDraftRevisionId(draft.getId());
            entryRepository.save(entry);
            return draft;
        }
        return revisionRepository.findById(entry.getDraftRevisionId())
                .orElseThrow(() -> new NotFoundException("草稿不存在"));
    }

    Map<UUID, ContentRevision> revisionsById(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ContentRevision> map = new LinkedHashMap<>();
        for (ContentRevision r : revisionRepository.findAllById(ids)) {
            map.put(r.getId(), r);
        }
        return map;
    }

    CardView toCard(ContentEntry e, ContentRevision r) {
        if (r == null) {
            return new CardView(e.getType(), e.getSlug(), "", "", null, Set.of(),
                    e.getFirstPublishedAt(), null, false);
        }
        String thumb = r.getCoverMediaId() == null ? null
                : MediaService.publicUrl(r.getCoverMediaId(), VariantType.THUMB);
        return new CardView(e.getType(), e.getSlug(), r.getTitle(), r.getSummary(), r.getCategory(),
                Set.copyOf(r.getTags()), e.getFirstPublishedAt(), thumb, r.isFeatured());
    }

    DetailView buildDetail(ContentEntry entry, ContentRevision revision, boolean adminUrls) {
        String mediaBase = adminUrls ? "/admin/media" : "/media";
        String cover = revision.getCoverMediaId() == null ? null
                : mediaBase + "/" + revision.getCoverMediaId() + "/display";
        String body = revision.getBodyHtml();
        if (adminUrls && body != null) {
            body = body.replace("src=\"/media/", "src=\"/admin/media/");
        }

        RecipeView recipe = null;
        List<IngredientView> ingredients = List.of();
        List<StepView> steps = List.of();
        List<DestinationView> destinations = List.of();
        List<PhotoItemView> photos = List.of();

        switch (entry.getType()) {
            case PHOTO -> {
                List<PhotoItemView> list = new ArrayList<>();
                for (PhotoItem item : photoItemRepository.findByRevisionIdOrderBySortOrderAsc(revision.getId())) {
                    list.add(new PhotoItemView(item.getMediaId(),
                            mediaBase + "/" + item.getMediaId() + "/display",
                            mediaBase + "/" + item.getMediaId() + "/thumb",
                            item.getAltText(), item.getCaption(), item.getSortOrder()));
                }
                photos = list;
            }
            case FOOD -> {
                RecipeDetail rd = recipeDetailRepository.findById(revision.getId()).orElse(null);
                if (rd != null) {
                    recipe = new RecipeView(rd.getDifficulty(), rd.getDurationMinutes(), rd.getServings(), rd.getIntro());
                }
                List<IngredientView> ings = new ArrayList<>();
                for (RecipeIngredient i : ingredientRepository.findByRevisionIdOrderBySortOrderAsc(revision.getId())) {
                    ings.add(new IngredientView(i.getName(), i.getAmount(), i.getSortOrder()));
                }
                ingredients = ings;
                List<StepView> stps = new ArrayList<>();
                for (RecipeStep s : stepRepository.findByRevisionIdOrderBySortOrderAsc(revision.getId())) {
                    stps.add(new StepView(s.getText(), s.getMediaId(),
                            s.getMediaId() == null ? null : mediaBase + "/" + s.getMediaId() + "/display",
                            s.getAltText(), s.getSortOrder()));
                }
                steps = stps;
            }
            case TRAVEL -> {
                List<DestinationView> dests = new ArrayList<>();
                for (TravelDestination d : destinationRepository.findByRevisionIdOrderBySortOrderAsc(revision.getId())) {
                    dests.add(new DestinationView(d.getName(), d.getRegion(), d.getSummary(),
                            d.getRecommendReason(), d.getVisitDate(), d.getMapUrl(), d.getMediaId(),
                            d.getMediaId() == null ? null : mediaBase + "/" + d.getMediaId() + "/display",
                            d.getAltText(), d.getSortOrder()));
                }
                destinations = dests;
            }
            case BLOG -> { /* body only */ }
        }

        return new DetailView(entry.getType(), entry.getSlug(), revision.getTitle(), revision.getSummary(),
                revision.getCategory(), Set.copyOf(revision.getTags()), revision.isFeatured(),
                entry.getFirstPublishedAt(), body, cover, recipe, ingredients, steps, destinations, photos);
    }

    // ---------------------------------------------------------------- module detail replacement

    private void replacePhotoItems(UUID revisionId, JsonNode detail, Set<UUID> referencedMedia) {
        photoItemRepository.deleteByRevisionId(revisionId);
        JsonNode items = detail.path("items");
        if (!items.isArray()) {
            return;
        }
        if (items.size() > MAX_IMAGES_PER_ENTRY) {
            throw new InvalidInputException("items", "每篇内容最多 " + MAX_IMAGES_PER_ENTRY + " 张图片");
        }
        int order = 0;
        for (JsonNode node : items) {
            UUID mediaId = uuid(node, "mediaId");
            if (mediaId == null) {
                continue;
            }
            PhotoItem item = new PhotoItem();
            item.setId(UUID.randomUUID());
            item.setRevisionId(revisionId);
            item.setMediaId(mediaId);
            item.setAltText(text(node, "altText", 300));
            item.setCaption(text(node, "caption", 500));
            item.setSortOrder(order++);
            photoItemRepository.save(item);
            referencedMedia.add(mediaId);
        }
    }

    private void replaceRecipe(UUID revisionId, JsonNode detail, Set<UUID> referencedMedia) {
        recipeDetailRepository.deleteById(revisionId);
        ingredientRepository.deleteByRevisionId(revisionId);
        stepRepository.deleteByRevisionId(revisionId);

        RecipeDetail rd = new RecipeDetail();
        rd.setRevisionId(revisionId);
        rd.setDifficulty(text(detail, "difficulty", 20));
        rd.setDurationMinutes(detail.hasNonNull("durationMinutes") && detail.get("durationMinutes").canConvertToInt()
                ? detail.get("durationMinutes").asInt() : null);
        rd.setServings(text(detail, "servings", 30));
        rd.setIntro(text(detail, "intro", 10000));
        recipeDetailRepository.save(rd);

        JsonNode ingredients = detail.path("ingredients");
        if (ingredients.isArray()) {
            int order = 0;
            for (JsonNode node : ingredients) {
                String name = text(node, "name", 120);
                if (name == null) {
                    continue;
                }
                RecipeIngredient ing = new RecipeIngredient();
                ing.setId(UUID.randomUUID());
                ing.setRevisionId(revisionId);
                ing.setName(name);
                ing.setAmount(text(node, "amount", 120));
                ing.setSortOrder(order++);
                ingredientRepository.save(ing);
            }
        }

        JsonNode steps = detail.path("steps");
        if (steps.isArray()) {
            int order = 0;
            for (JsonNode node : steps) {
                RecipeStep step = new RecipeStep();
                step.setId(UUID.randomUUID());
                step.setRevisionId(revisionId);
                step.setText(text(node, "text", 5000));
                UUID mediaId = uuid(node, "mediaId");
                step.setMediaId(mediaId);
                step.setAltText(text(node, "altText", 300));
                step.setSortOrder(order++);
                stepRepository.save(step);
                if (mediaId != null) {
                    referencedMedia.add(mediaId);
                }
            }
        }
    }

    private void replaceDestinations(UUID revisionId, JsonNode detail, Set<UUID> referencedMedia) {
        destinationRepository.deleteByRevisionId(revisionId);
        JsonNode destinations = detail.path("destinations");
        if (!destinations.isArray()) {
            return;
        }
        int order = 0;
        for (JsonNode node : destinations) {
            String name = text(node, "name", 160);
            if (name == null) {
                continue;
            }
            TravelDestination dest = new TravelDestination();
            dest.setId(UUID.randomUUID());
            dest.setRevisionId(revisionId);
            dest.setName(name);
            dest.setRegion(text(node, "region", 120));
            dest.setSummary(text(node, "summary", 5000));
            dest.setRecommendReason(text(node, "recommendReason", 5000));
            String visitDate = node.path("visitDate").asText(null);
            if (visitDate != null && !visitDate.isBlank()) {
                try {
                    dest.setVisitDate(LocalDate.parse(visitDate.trim()));
                } catch (Exception e) {
                    throw new InvalidInputException("visitDate", "日期格式应为 YYYY-MM-DD");
                }
            }
            dest.setMapUrl(validatedMapUrl(text(node, "mapUrl", 500)));
            UUID mediaId = uuid(node, "mediaId");
            dest.setMediaId(mediaId);
            dest.setAltText(text(node, "altText", 300));
            dest.setSortOrder(order++);
            destinationRepository.save(dest);
            if (mediaId != null) {
                referencedMedia.add(mediaId);
            }
        }
    }

    private void syncRefs(UUID revisionId, ContentType type, UUID coverMediaId,
                          Set<UUID> bodyMediaIds, JsonNode detail) {
        refRepository.deleteByRevisionId(revisionId);
        List<RevisionMediaRef> refs = new ArrayList<>();
        if (coverMediaId != null) {
            refs.add(new RevisionMediaRef(revisionId, coverMediaId, MediaRole.COVER));
        }
        for (UUID id : bodyMediaIds) {
            refs.add(new RevisionMediaRef(revisionId, id, MediaRole.BODY));
        }
        switch (type) {
            case PHOTO -> {
                for (JsonNode node : detail.path("items")) {
                    UUID id = uuid(node, "mediaId");
                    if (id != null) {
                        refs.add(new RevisionMediaRef(revisionId, id, MediaRole.PHOTO));
                    }
                }
            }
            case FOOD -> {
                for (JsonNode node : detail.path("steps")) {
                    UUID id = uuid(node, "mediaId");
                    if (id != null) {
                        refs.add(new RevisionMediaRef(revisionId, id, MediaRole.STEP));
                    }
                }
            }
            case TRAVEL -> {
                for (JsonNode node : detail.path("destinations")) {
                    UUID id = uuid(node, "mediaId");
                    if (id != null) {
                        refs.add(new RevisionMediaRef(revisionId, id, MediaRole.DESTINATION));
                    }
                }
            }
            case BLOG -> { /* body only */ }
        }
        // de-duplicate by (mediaId, role)
        Set<String> seen = new LinkedHashSet<>();
        List<RevisionMediaRef> unique = new ArrayList<>();
        for (RevisionMediaRef ref : refs) {
            if (seen.add(ref.getMediaId() + ":" + ref.getRole())) {
                unique.add(ref);
            }
        }
        refRepository.saveAll(unique);
    }

    // ---------------------------------------------------------------- misc helpers

    private static void validateLengths(ContentForm form, Map<String, String> errors) {
        if (form.getTitle() != null && form.getTitle().trim().length() > 120) {
            errors.put("title", "标题最多 120 个字符");
        }
        if (form.getSummary() != null && form.getSummary().length() > 500) {
            errors.put("summary", "摘要最多 500 个字符");
        }
        if (form.getCategory() != null && form.getCategory().length() > 60) {
            errors.put("category", "分类最多 60 个字符");
        }
    }

    private JsonNode parseDetailJson(String json, Map<String, String> errors) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            errors.put("detail", "结构化数据格式不正确");
            return objectMapper.createObjectNode();
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field, int maxLength) {
        String value = node.path(field).asText(null);
        if (value == null) {
            return null;
        }
        value = value.strip();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** External map links must be plain http(s) URLs — anything else is rejected (REQ-007 外链校验). */
    private static String validatedMapUrl(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new InvalidInputException("mapUrl", "地图链接必须是 http:// 或 https:// 开头的网址");
        }
        return url;
    }

    static String blankToNull(String s) {
        return trimToNull(s);
    }
}
