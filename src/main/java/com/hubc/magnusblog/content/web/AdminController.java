package com.hubc.magnusblog.content.web;

import com.hubc.magnusblog.content.ContentEntryRepository;
import com.hubc.magnusblog.media.MediaAssetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    private final ContentEntryRepository entryRepository;
    private final MediaAssetRepository mediaAssetRepository;

    public AdminController(ContentEntryRepository entryRepository,
                           MediaAssetRepository mediaAssetRepository) {
        this.entryRepository = entryRepository;
        this.mediaAssetRepository = mediaAssetRepository;
    }

    @GetMapping("/admin")
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/admin/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("publishedCount", entryRepository.countPublished());
        model.addAttribute("draftCount",
                entryRepository.countByDeletedAtIsNull() - entryRepository.countPublished());
        model.addAttribute("trashCount", entryRepository.countTrashed());
        model.addAttribute("mediaCount", mediaAssetRepository.count());
        return "admin/dashboard";
    }
}
