package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.ConflictException;
import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.common.MediaStatus;
import com.hubc.magnusblog.common.NotFoundException;
import com.hubc.magnusblog.common.TooManyRequestsException;
import com.hubc.magnusblog.common.VariantType;
import com.hubc.magnusblog.config.AppProperties;
import com.hubc.magnusblog.profile.SiteProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final MediaStorage storage;
    private final ImageProcessingService imageProcessing;
    private final MediaAssetRepository assetRepository;
    private final MediaVariantRepository variantRepository;
    private final RevisionMediaRefRepository refRepository;
    private final SiteProfileRepository profileRepository;
    private final TransactionTemplate transactionTemplate;
    private final Semaphore processingSlots;
    private final long minFreeBytes;

    public MediaService(MediaStorage storage,
                        ImageProcessingService imageProcessing,
                        MediaAssetRepository assetRepository,
                        MediaVariantRepository variantRepository,
                        RevisionMediaRefRepository refRepository,
                        SiteProfileRepository profileRepository,
                        TransactionTemplate transactionTemplate,
                        AppProperties properties) {
        this.storage = storage;
        this.imageProcessing = imageProcessing;
        this.assetRepository = assetRepository;
        this.variantRepository = variantRepository;
        this.refRepository = refRepository;
        this.profileRepository = profileRepository;
        this.transactionTemplate = transactionTemplate;
        this.processingSlots = new Semaphore(Math.max(1, properties.upload().concurrentJobs()));
        this.minFreeBytes = properties.storage().minFreeBytes();
    }

    public record UploadResult(UUID id, String previewUrl, String publishedUrl, int width, int height) {
    }

    public record Served(Path path, String mime, long sizeBytes, String etag) {
    }

    /**
     * Validate, process and store an uploaded image. Files are fully written
     * before the database rows are committed; leftovers are swept from tmp.
     */
    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("file", "请选择要上传的图片");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new InvalidInputException("file", "仅支持 JPEG 和 PNG 图片");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidInputException("file", "图片超过 10 MB 限制");
        }
        if (!processingSlots.tryAcquire()) {
            throw new TooManyRequestsException("图片处理繁忙，请稍后重试");
        }

        UUID mediaId = UUID.randomUUID();
        Path tmpDir = storage.tmpDir().resolve(mediaId.toString());
        Path finalDir = null;
        try {
            storage.init();
            if (storage.usableSpace() < minFreeBytes + file.getSize() * 3) {
                throw new InvalidInputException("file", "存储空间不足，无法上传");
            }
            Files.createDirectories(tmpDir);
            Path rawUpload = tmpDir.resolve("upload.bin");
            file.transferTo(rawUpload);

            var decoded = imageProcessing.decode(rawUpload);
            String sha256 = sha256(rawUpload);

            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            String relDir = "media/%04d/%02d/%s".formatted(now.getYear(), now.getMonthValue(), mediaId);
            finalDir = storage.resolve(relDir);
            Files.createDirectories(finalDir);

            String originalExt = decoded.format() == ImageProcessingService.Format.PNG ? "png" : "jpg";
            String originalRel = relDir + "/original." + originalExt;
            String displayRel = relDir + "/display.jpg";
            String thumbRel = relDir + "/thumb.jpg";

            imageProcessing.writeOriginal(decoded.image(), decoded.format(), storage.resolve(originalRel));
            int[] displaySize = imageProcessing.writeDisplay(decoded.image(), storage.resolve(displayRel));
            int[] thumbSize = imageProcessing.writeThumb(decoded.image(), storage.resolve(thumbRel));

            String displayName = safeDisplayName(originalName);
            long originalSize = Files.size(storage.resolve(originalRel));
            long displaySizeBytes = Files.size(storage.resolve(displayRel));
            long thumbSizeBytes = Files.size(storage.resolve(thumbRel));

            transactionTemplate.executeWithoutResult(tx -> {
                MediaAsset asset = new MediaAsset();
                asset.setId(mediaId);
                asset.setFilename(displayName.isBlank() ? "image" : displayName);
                asset.setMime(decoded.format().mime);
                asset.setSha256(sha256);
                asset.setWidth(decoded.image().getWidth());
                asset.setHeight(decoded.image().getHeight());
                asset.setStatus(MediaStatus.READY);
                assetRepository.save(asset);

                variantRepository.save(variant(mediaId, VariantType.ORIGINAL,
                        decoded.image().getWidth(), decoded.image().getHeight(),
                        originalRel, decoded.format().mime, originalSize));
                variantRepository.save(variant(mediaId, VariantType.DISPLAY,
                        displaySize[0], displaySize[1], displayRel, "image/jpeg", displaySizeBytes));
                variantRepository.save(variant(mediaId, VariantType.THUMB,
                        thumbSize[0], thumbSize[1], thumbRel, "image/jpeg", thumbSizeBytes));
            });
            return new UploadResult(mediaId, adminUrl(mediaId, VariantType.DISPLAY),
                    publicUrl(mediaId, VariantType.DISPLAY),
                    decoded.image().getWidth(), decoded.image().getHeight());
        } catch (InvalidInputException | TooManyRequestsException e) {
            deleteTreeQuietly(finalDir);
            throw e;
        } catch (Exception e) {
            log.error("media upload failed", e);
            deleteTreeQuietly(finalDir);
            throw new InvalidInputException("file", "图片处理失败，请重试");
        } finally {
            deleteTreeQuietly(tmpDir);
            processingSlots.release();
        }
    }

    /** Public access: display/thumb only, READY, and referenced by live published content (or the site avatar). */
    public Served loadForPublic(UUID mediaId, VariantType variant) {
        if (variant == VariantType.ORIGINAL) {
            throw new NotFoundException("not found");
        }
        MediaAsset asset = assetRepository.findById(mediaId)
                .filter(a -> a.getStatus() == MediaStatus.READY)
                .orElseThrow(() -> new NotFoundException("not found"));
        if (!isPubliclyVisible(mediaId)) {
            throw new NotFoundException("not found");
        }
        return serve(asset, variant);
    }

    /** Admin access: any variant, READY or not (the uploader may need to inspect). */
    public Served loadForAdmin(UUID mediaId, VariantType variant) {
        MediaAsset asset = assetRepository.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("not found"));
        return serve(asset, variant);
    }

    public boolean isPubliclyVisible(UUID mediaId) {
        if (refRepository.countPublicReferences(mediaId) > 0) {
            return true;
        }
        return profileRepository.findAll().stream()
                .anyMatch(p -> mediaId.equals(p.getAvatarMediaId()));
    }

    private Served serve(MediaAsset asset, VariantType variant) {
        MediaVariant v = variantRepository.findByMediaIdAndVariantType(asset.getId(), variant)
                .orElseThrow(() -> new NotFoundException("not found"));
        Path path = storage.resolve(v.getPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("not found");
        }
        return new Served(path, v.getMime(), v.getSizeBytes(), asset.getSha256() + "-" + variant.name());
    }

    /** Refuse to delete media still referenced by any revision or the site profile. */
    public void delete(UUID mediaId) {
        MediaAsset asset = assetRepository.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("not found"));
        if (refRepository.existsByMediaId(mediaId)) {
            throw new ConflictException("图片仍被内容引用，不能删除");
        }
        boolean isAvatar = profileRepository.findAll().stream()
                .anyMatch(p -> mediaId.equals(p.getAvatarMediaId()));
        if (isAvatar) {
            throw new ConflictException("图片正在用作头像，不能删除");
        }
        List<MediaVariant> variants = variantRepository.findByMediaId(mediaId);
        transactionTemplate.executeWithoutResult(tx -> assetRepository.delete(asset));
        for (MediaVariant v : variants) {
            storage.deleteQuietly(v.getPath());
        }
    }

    /** Assert all referenced media exist and are READY (used when saving/publishing). */
    public void assertReady(Set<UUID> mediaIds) {
        for (UUID id : mediaIds) {
            if (id == null) {
                continue;
            }
            MediaAsset asset = assetRepository.findById(id)
                    .orElseThrow(() -> new InvalidInputException("media", "引用的图片不存在"));
            if (asset.getStatus() != MediaStatus.READY) {
                throw new InvalidInputException("media", "引用的图片尚未处理完成");
            }
        }
    }

    public Page<MediaAsset> adminPage(Pageable pageable) {
        return assetRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public static String publicUrl(UUID mediaId, VariantType variant) {
        return "/media/" + mediaId + "/" + variant.name().toLowerCase(Locale.ROOT);
    }

    public static String adminUrl(UUID mediaId, VariantType variant) {
        return "/admin/media/" + mediaId + "/" + variant.name().toLowerCase(Locale.ROOT);
    }

    private static MediaVariant variant(UUID mediaId, VariantType type, int w, int h,
                                        String path, String mime, long size) {
        MediaVariant v = new MediaVariant();
        v.setId(UUID.randomUUID());
        v.setMediaId(mediaId);
        v.setVariantType(type);
        v.setWidth(w);
        v.setHeight(h);
        v.setPath(path);
        v.setMime(mime);
        v.setSizeBytes(size);
        return v;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Keep a human-readable name for display; never used in storage paths. */
    private static String safeDisplayName(String name) {
        String base = name.replaceAll("[\\r\\n/\\\\]", " ").trim();
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTreeQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
