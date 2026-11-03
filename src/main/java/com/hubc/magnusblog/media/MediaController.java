package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.NotFoundException;
import com.hubc.magnusblog.common.VariantType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Media is never exposed as static files. Every request is authorized first:
 * the public endpoint only serves display/thumb variants that are referenced by
 * live published content; the admin endpoint requires the ADMIN session
 * (enforced by the security filter chain on /admin/**).
 */
@Controller
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/media/{id}/{variant}")
    public ResponseEntity<FileSystemResource> publicMedia(@PathVariable UUID id,
                                                          @PathVariable String variant,
                                                          org.springframework.web.context.request.WebRequest request) {
        VariantType type = parseVariant(variant);
        MediaService.Served served = mediaService.loadForPublic(id, type);
        return respond(served, request, CacheControl.noCache());
    }

    @GetMapping("/admin/media/{id}/{variant}")
    public ResponseEntity<FileSystemResource> adminMedia(@PathVariable UUID id,
                                                         @PathVariable String variant,
                                                         org.springframework.web.context.request.WebRequest request) {
        VariantType type = parseVariant(variant);
        MediaService.Served served = mediaService.loadForAdmin(id, type);
        return respond(served, request, CacheControl.noStore());
    }

    private static VariantType parseVariant(String variant) {
        try {
            return VariantType.fromPathSegment(variant);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("not found");
        }
    }

    private ResponseEntity<FileSystemResource> respond(MediaService.Served served,
                                                       org.springframework.web.context.request.WebRequest request,
                                                       CacheControl cacheControl) {
        String etag = "\"" + served.etag() + "\"";
        // Authorization already happened above; only then honor conditional GET.
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(cacheControl)
                    .eTag(etag)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .eTag(etag)
                .contentType(MediaType.parseMediaType(served.mime()))
                .contentLength(served.sizeBytes())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(served.path()));
    }
}
