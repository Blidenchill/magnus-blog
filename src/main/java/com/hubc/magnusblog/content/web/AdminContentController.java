package com.hubc.magnusblog.content.web;

import com.hubc.magnusblog.common.ConflictException;
import com.hubc.magnusblog.common.ContentType;
import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.common.PageRequestParser;
import com.hubc.magnusblog.content.ContentForm;
import com.hubc.magnusblog.content.ContentService;
import com.hubc.magnusblog.content.ContentViews;
import com.hubc.magnusblog.content.PublishingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

@Controller
public class AdminContentController {

    private final ContentService contentService;
    private final PublishingService publishingService;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public AdminContentController(ContentService contentService, PublishingService publishingService,
                                  tools.jackson.databind.ObjectMapper objectMapper) {
        this.contentService = contentService;
        this.publishingService = publishingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/admin/contents")
    public String list(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String size,
                       Model model) {
        ContentType contentType = parseTypeOrNull(type);
        model.addAttribute("page",
                contentService.adminPage(contentType, status, PageRequestParser.parse(page, size)));
        model.addAttribute("type", type);
        model.addAttribute("status", status == null ? "ALL" : status);
        return "admin/contents";
    }

    @GetMapping("/admin/contents/new")
    public String newForm(@RequestParam(required = false) String type, Model model) {
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("types", ContentType.values());
        return "admin/content-new";
    }

    @PostMapping("/admin/contents/new")
    public String create(@RequestParam String type) {
        UUID id = contentService.create(ContentType.from(type));
        return "redirect:/admin/contents/" + id + "/edit";
    }

    @GetMapping("/admin/contents/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        ContentViews.EditView view = contentService.loadForEdit(id);
        model.addAttribute("view", view);
        model.addAttribute("detailJson", objectMapper.writeValueAsString(view.detail()));
        return "admin/content-edit";
    }

    @PostMapping("/admin/contents/{id}/edit")
    public String save(@PathVariable UUID id,
                       @ModelAttribute ContentForm form,
                       Model model,
                       HttpServletResponse response,
                       RedirectAttributes redirectAttributes) {
        try {
            contentService.saveDraft(id, form);
        } catch (InvalidInputException e) {
            return editWithErrors(id, model, response, e.getFieldErrors(), HttpStatus.UNPROCESSABLE_CONTENT);
        } catch (ConflictException e) {
            return editWithErrors(id, model, response, Map.of("_", e.getMessage()), HttpStatus.CONFLICT);
        }
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/admin/contents/" + id + "/edit";
    }

    /** Autosave endpoint: same draft save, JSON response so the editor can stay on the page. */
    @PostMapping("/admin/contents/{id}/autosave")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> autosave(@PathVariable UUID id,
                                                                                 @ModelAttribute ContentForm form) {
        try {
            contentService.saveDraft(id, form);
        } catch (InvalidInputException e) {
            return org.springframework.http.ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(Map.of("error", e.getFieldErrors().values().stream().findFirst().orElse("校验失败")));
        } catch (ConflictException e) {
            return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
        ContentViews.EditView view = contentService.loadForEdit(id);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ok", true);
        body.put("entryVersion", view.entry().getVersion());
        body.put("revisionVersion", view.draft().getVersion());
        return org.springframework.http.ResponseEntity.ok(body);
    }

    @GetMapping("/admin/contents/{id}/preview")
    public String preview(@PathVariable UUID id, Model model) {
        model.addAttribute("content", contentService.loadDraftPreview(id));
        return "admin/content-preview";
    }

    @PostMapping("/admin/contents/{id}/publish")
    public String publish(@PathVariable UUID id,
                          @RequestParam long entryVersion,
                          @RequestParam long revisionVersion,
                          Model model,
                          HttpServletResponse response,
                          RedirectAttributes redirectAttributes) {
        try {
            publishingService.publish(id, entryVersion, revisionVersion);
        } catch (InvalidInputException e) {
            return editWithErrors(id, model, response, e.getFieldErrors(), HttpStatus.UNPROCESSABLE_CONTENT);
        } catch (ConflictException e) {
            return editWithErrors(id, model, response, Map.of("_", e.getMessage()), HttpStatus.CONFLICT);
        }
        redirectAttributes.addFlashAttribute("published", true);
        return "redirect:/admin/contents/" + id + "/edit";
    }

    @PostMapping("/admin/contents/{id}/unpublish")
    public String unpublish(@PathVariable UUID id,
                            @RequestParam long entryVersion,
                            Model model,
                            HttpServletResponse response,
                            RedirectAttributes redirectAttributes) {
        try {
            publishingService.unpublish(id, entryVersion);
        } catch (ConflictException e) {
            return editWithErrors(id, model, response, Map.of("_", e.getMessage()), HttpStatus.CONFLICT);
        }
        redirectAttributes.addFlashAttribute("unpublished", true);
        return "redirect:/admin/contents/" + id + "/edit";
    }

    @PostMapping("/admin/contents/{id}/trash")
    public String trash(@PathVariable UUID id,
                        @RequestParam long entryVersion,
                        RedirectAttributes redirectAttributes) {
        publishingService.trash(id, entryVersion);
        redirectAttributes.addFlashAttribute("trashed", true);
        return "redirect:/admin/contents";
    }

    @GetMapping("/admin/trash")
    public String trashList(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String page,
                            @RequestParam(required = false) String size,
                            Model model) {
        ContentType contentType = parseTypeOrNull(type);
        model.addAttribute("page",
                contentService.trashPage(contentType, PageRequestParser.parse(page, size)));
        model.addAttribute("type", type);
        return "admin/trash";
    }

    @PostMapping("/admin/contents/{id}/restore")
    public String restore(@PathVariable UUID id,
                          @RequestParam long entryVersion,
                          RedirectAttributes redirectAttributes) {
        publishingService.restore(id, entryVersion);
        redirectAttributes.addFlashAttribute("restored", true);
        return "redirect:/admin/contents/" + id + "/edit";
    }

    private String editWithErrors(UUID id, Model model, HttpServletResponse response,
                                  Map<String, String> errors, HttpStatus status) {
        response.setStatus(status.value());
        try {
            ContentViews.EditView view = contentService.loadForEdit(id);
            model.addAttribute("view", view);
            model.addAttribute("detailJson", objectMapper.writeValueAsString(view.detail()));
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException(e);
        }
        model.addAttribute("errors", errors);
        model.addAttribute("errorStatus", status.value());
        return "admin/content-edit";
    }

    private static ContentType parseTypeOrNull(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return ContentType.from(type);
    }
}
