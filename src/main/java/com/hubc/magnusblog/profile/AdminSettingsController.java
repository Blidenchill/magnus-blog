package com.hubc.magnusblog.profile;

import com.hubc.magnusblog.common.ConflictException;
import com.hubc.magnusblog.common.InvalidInputException;
import com.hubc.magnusblog.common.VariantType;
import com.hubc.magnusblog.media.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

@Controller
public class AdminSettingsController {

    private final ProfileService profileService;

    public AdminSettingsController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/admin/settings")
    public String settings(Model model) {
        SiteProfile profile = profileService.getOrCreate();
        model.addAttribute("profile", profile);
        model.addAttribute("avatarUrl", profile.getAvatarMediaId() == null ? null
                : MediaService.adminUrl(profile.getAvatarMediaId(), VariantType.DISPLAY));
        return "admin/settings";
    }

    @PostMapping("/admin/settings")
    public String save(@RequestParam String name,
                       @RequestParam(required = false) String bio,
                       @RequestParam(required = false) String contact,
                       @RequestParam(required = false) UUID avatarMediaId,
                       @RequestParam long version,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            profileService.update(name, bio, contact, avatarMediaId, version);
        } catch (InvalidInputException e) {
            return withErrors(model, e.getFieldErrors(), HttpStatus.UNPROCESSABLE_CONTENT);
        } catch (ConflictException e) {
            return withErrors(model, Map.of("_", e.getMessage()), HttpStatus.CONFLICT);
        }
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/admin/settings";
    }

    private String withErrors(Model model, Map<String, String> errors, HttpStatus status) {
        SiteProfile profile = profileService.getOrCreate();
        model.addAttribute("profile", profile);
        model.addAttribute("avatarUrl", profile.getAvatarMediaId() == null ? null
                : MediaService.adminUrl(profile.getAvatarMediaId(), VariantType.DISPLAY));
        model.addAttribute("errors", errors);
        model.addAttribute("errorStatus", status.value());
        return "admin/settings";
    }
}
