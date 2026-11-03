package com.hubc.magnusblog.content;

import com.hubc.magnusblog.common.ConflictException;
import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.common.MediaRole;
import com.hubc.magnusblog.common.NotFoundException;
import com.hubc.magnusblog.common.RevisionKind;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Publish lifecycle: publish (draft -> immutable public snapshot), unpublish,
 * trash, restore. All steps run in a single transaction so a half-published
 * state can never be committed.
 */
@Service
public class PublishingService {

    private final ContentEntryRepository entryRepository;
    private final ContentRevisionRepository revisionRepository;
    private final PhotoItemRepository photoItemRepository;
    private final RecipeDetailRepository recipeDetailRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final TravelDestinationRepository destinationRepository;
    private final RevisionMediaRefRepository refRepository;
    private final MediaService mediaService;

    public PublishingService(ContentEntryRepository entryRepository,
                             ContentRevisionRepository revisionRepository,
                             PhotoItemRepository photoItemRepository,
                             RecipeDetailRepository recipeDetailRepository,
                             RecipeIngredientRepository ingredientRepository,
                             RecipeStepRepository stepRepository,
                             TravelDestinationRepository destinationRepository,
                             RevisionMediaRefRepository refRepository,
                             MediaService mediaService) {
        this.entryRepository = entryRepository;
        this.revisionRepository = revisionRepository;
        this.photoItemRepository = photoItemRepository;
        this.recipeDetailRepository = recipeDetailRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.destinationRepository = destinationRepository;
        this.refRepository = refRepository;
        this.mediaService = mediaService;
    }

    @Transactional
    public void publish(UUID entryId, long entryVersion, long revisionVersion) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        if (entry.isDeleted()) {
            throw new ConflictException("回收站中的内容请先恢复再发布");
        }
        if (entry.getVersion() != entryVersion) {
            throw new ConflictException("内容已被其他窗口修改，请刷新后重试");
        }
        if (entry.getDraftRevisionId() == null) {
            throw new InvalidInputException("draft", "没有可发布的草稿");
        }
        ContentRevision draft = revisionRepository.findById(entry.getDraftRevisionId())
                .orElseThrow(() -> new NotFoundException("草稿不存在"));
        if (draft.getVersion() != revisionVersion) {
            throw new ConflictException("草稿已被其他窗口修改，请刷新后重试");
        }

        validateForPublish(entry, draft);

        // Immutable snapshot: new revision row, copies of all detail rows and refs.
        ContentRevision snapshot = new ContentRevision();
        snapshot.setId(UUID.randomUUID());
        snapshot.setEntryId(entry.getId());
        snapshot.setKind(RevisionKind.PUBLISHED);
        snapshot.setTitle(draft.getTitle());
        snapshot.setSummary(draft.getSummary());
        snapshot.setCategory(draft.getCategory());
        snapshot.setBodyHtml(draft.getBodyHtml());
        snapshot.setFeatured(draft.isFeatured());
        UUID coverMediaId = draft.getCoverMediaId();
        // Tags are attached AFTER the initial flush: persisting a @Version entity with a
        // non-empty @ElementCollection makes Hibernate issue a version-bump UPDATE on the
        // just-inserted row, whose verify step then fails with a spurious optimistic lock error.
        revisionRepository.saveAndFlush(snapshot);

        switch (entry.getType()) {
            case PHOTO -> {
                List<PhotoItem> items = photoItemRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId());
                for (PhotoItem item : items) {
                    PhotoItem copy = new PhotoItem();
                    copy.setId(UUID.randomUUID());
                    copy.setRevisionId(snapshot.getId());
                    copy.setMediaId(item.getMediaId());
                    copy.setSortOrder(item.getSortOrder());
                    copy.setAltText(item.getAltText());
                    copy.setCaption(item.getCaption());
                    photoItemRepository.save(copy);
                }
                // Album cover defaults to the first photo when none was picked.
                if (coverMediaId == null && !items.isEmpty()) {
                    coverMediaId = items.get(0).getMediaId();
                }
            }
            case FOOD -> {
                recipeDetailRepository.findById(draft.getId()).ifPresent(rd -> {
                    RecipeDetail copy = new RecipeDetail();
                    copy.setRevisionId(snapshot.getId());
                    copy.setDifficulty(rd.getDifficulty());
                    copy.setDurationMinutes(rd.getDurationMinutes());
                    copy.setServings(rd.getServings());
                    copy.setIntro(rd.getIntro());
                    recipeDetailRepository.save(copy);
                });
                for (RecipeIngredient ing : ingredientRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId())) {
                    RecipeIngredient copy = new RecipeIngredient();
                    copy.setId(UUID.randomUUID());
                    copy.setRevisionId(snapshot.getId());
                    copy.setName(ing.getName());
                    copy.setAmount(ing.getAmount());
                    copy.setSortOrder(ing.getSortOrder());
                    ingredientRepository.save(copy);
                }
                for (RecipeStep step : stepRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId())) {
                    RecipeStep copy = new RecipeStep();
                    copy.setId(UUID.randomUUID());
                    copy.setRevisionId(snapshot.getId());
                    copy.setText(step.getText());
                    copy.setMediaId(step.getMediaId());
                    copy.setAltText(step.getAltText());
                    copy.setSortOrder(step.getSortOrder());
                    stepRepository.save(copy);
                }
            }
            case TRAVEL -> {
                for (TravelDestination dest : destinationRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId())) {
                    TravelDestination copy = new TravelDestination();
                    copy.setId(UUID.randomUUID());
                    copy.setRevisionId(snapshot.getId());
                    copy.setName(dest.getName());
                    copy.setRegion(dest.getRegion());
                    copy.setSummary(dest.getSummary());
                    copy.setRecommendReason(dest.getRecommendReason());
                    copy.setVisitDate(dest.getVisitDate());
                    copy.setMapUrl(dest.getMapUrl());
                    copy.setMediaId(dest.getMediaId());
                    copy.setAltText(dest.getAltText());
                    copy.setSortOrder(dest.getSortOrder());
                    destinationRepository.save(copy);
                }
            }
            case BLOG -> { /* body only */ }
        }

        snapshot.setCoverMediaId(coverMediaId);
        snapshot.setTags(new LinkedHashSet<>(draft.getTags()));
        revisionRepository.saveAndFlush(snapshot);

        Set<String> copiedRefs = new java.util.HashSet<>();
        for (RevisionMediaRef ref : refRepository.findByRevisionId(draft.getId())) {
            refRepository.save(new RevisionMediaRef(snapshot.getId(), ref.getMediaId(), ref.getRole()));
            copiedRefs.add(ref.getMediaId() + ":" + ref.getRole());
        }
        // Cover defaulted above must be recorded as a reference too (unless already copied).
        if (coverMediaId != null && !copiedRefs.contains(coverMediaId + ":" + MediaRole.COVER)) {
            refRepository.save(new RevisionMediaRef(snapshot.getId(), coverMediaId, MediaRole.COVER));
        }

        UUID oldPublishedId = entry.getPublishedRevisionId();
        entry.setPublishedRevisionId(snapshot.getId());
        if (entry.getFirstPublishedAt() == null) {
            entry.setFirstPublishedAt(Instant.now());
        }
        entryRepository.saveAndFlush(entry);

        if (oldPublishedId != null && !oldPublishedId.equals(snapshot.getId())) {
            revisionRepository.deleteById(oldPublishedId);
        }
    }

    @Transactional
    public void unpublish(UUID entryId, long entryVersion) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        if (entry.getVersion() != entryVersion) {
            throw new ConflictException("内容已被其他窗口修改，请刷新后重试");
        }
        UUID publishedId = entry.getPublishedRevisionId();
        entry.setPublishedRevisionId(null);
        entryRepository.saveAndFlush(entry);
        if (publishedId != null) {
            revisionRepository.deleteById(publishedId);
        }
    }

    @Transactional
    public void trash(UUID entryId, long entryVersion) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        if (entry.getVersion() != entryVersion) {
            throw new ConflictException("内容已被其他窗口修改，请刷新后重试");
        }
        UUID publishedId = entry.getPublishedRevisionId();
        entry.setPublishedRevisionId(null);
        entry.setDeletedAt(Instant.now());
        entryRepository.saveAndFlush(entry);
        if (publishedId != null) {
            revisionRepository.deleteById(publishedId);
        }
    }

    @Transactional
    public void restore(UUID entryId, long entryVersion) {
        ContentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("内容不存在"));
        if (entry.getVersion() != entryVersion) {
            throw new ConflictException("内容已被其他窗口修改，请刷新后重试");
        }
        if (!entry.isDeleted()) {
            throw new ConflictException("该内容不在回收站中");
        }
        entry.setDeletedAt(null);
        entryRepository.save(entry);
    }

    // ---------------------------------------------------------------- publish validation

    private void validateForPublish(ContentEntry entry, ContentRevision draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            errors.put("title", "发布前请填写标题");
        } else if (draft.getTitle().length() > 120) {
            errors.put("title", "标题最多 120 个字符");
        }

        Set<UUID> mediaToCheck = new LinkedHashSet<>();
        if (draft.getCoverMediaId() != null) {
            mediaToCheck.add(draft.getCoverMediaId());
        }

        switch (entry.getType()) {
            case BLOG -> {
                if (draft.getBodyHtml() == null || draft.getBodyHtml().isBlank()) {
                    errors.put("bodyHtml", "发布前请填写正文");
                }
            }
            case PHOTO -> {
                List<PhotoItem> items = photoItemRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId());
                if (items.isEmpty()) {
                    errors.put("items", "相册至少需要一张图片");
                }
                for (PhotoItem item : items) {
                    mediaToCheck.add(item.getMediaId());
                }
            }
            case FOOD -> {
                if (stepRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId()).isEmpty()) {
                    errors.put("steps", "菜谱至少需要一条制作步骤");
                }
                for (RecipeStep step : stepRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId())) {
                    if (step.getMediaId() != null) {
                        mediaToCheck.add(step.getMediaId());
                    }
                }
            }
            case TRAVEL -> {
                List<TravelDestination> dests = destinationRepository.findByRevisionIdOrderBySortOrderAsc(draft.getId());
                if (dests.isEmpty()) {
                    errors.put("destinations", "旅行清单至少需要一个景点");
                }
                for (TravelDestination dest : dests) {
                    if (dest.getMediaId() != null) {
                        mediaToCheck.add(dest.getMediaId());
                    }
                }
            }
        }

        // Body images are validated at sanitize time; collect current refs as well.
        for (RevisionMediaRef ref : refRepository.findByRevisionId(draft.getId())) {
            mediaToCheck.add(ref.getMediaId());
        }
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
        mediaService.assertReady(mediaToCheck);
    }
}
