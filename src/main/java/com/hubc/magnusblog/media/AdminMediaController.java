package com.hubc.magnusblog.media;

import com.hubc.magnusblog.common.ConflictException;
import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.common.NotFoundException;
import com.hubc.magnusblog.common.PageRequestParser;
import com.hubc.magnusblog.common.TooManyRequestsException;
import com.hubc.magnusblog.common.VariantType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class AdminMediaController {

    private final MediaService mediaService;

    public AdminMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/admin/media")
    public String list(@RequestParam(required = false) String page,
                       @RequestParam(required = false) String size,
                       Model model) {
        var assets = mediaService.adminPage(PageRequestParser.parse(page, size));
        Map<UUID, String> thumbs = new LinkedHashMap<>();
        for (MediaAsset asset : assets.getContent()) {
            thumbs.put(asset.getId(), MediaService.adminUrl(asset.getId(), VariantType.THUMB));
        }
        model.addAttribute("page", assets);
        model.addAttribute("thumbs", thumbs);
        return "admin/media";
    }

    /** JSON upload endpoint used by the editors; CSRF token arrives as a header. */
    @PostMapping("/admin/media/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            MediaService.UploadResult result = mediaService.upload(file);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", result.id().toString());
            body.put("previewUrl", result.previewUrl());
            body.put("publishedUrl", result.publishedUrl());
            body.put("width", result.width());
            body.put("height", result.height());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (InvalidInputException e) {
            return jsonError(HttpStatus.UNPROCESSABLE_CONTENT, firstMessage(e));
        } catch (TooManyRequestsException e) {
            return jsonError(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
    }

    @PostMapping("/admin/media/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            mediaService.delete(id);
            redirectAttributes.addFlashAttribute("deleted", true);
        } catch (ConflictException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (NotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "图片不存在");
        }
        return "redirect:/admin/media";
    }

    private static String firstMessage(InvalidInputException e) {
        return e.getFieldErrors().values().stream().findFirst().orElse(e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
