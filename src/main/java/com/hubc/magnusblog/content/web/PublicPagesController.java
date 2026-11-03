package com.hubc.magnusblog.content.web;

import com.hubc.magnusblog.common.ContentType;
import com.hubc.magnusblog.common.PageRequestParser;
import com.hubc.magnusblog.content.ContentService;
import com.hubc.magnusblog.content.SearchService;
import com.hubc.magnusblog.media.MediaService;
import com.hubc.magnusblog.common.VariantType;
import com.hubc.magnusblog.profile.ProfileService;
import com.hubc.magnusblog.profile.SiteProfile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Public, read-only pages. Only published snapshots are ever read here. */
@Controller
public class PublicPagesController {

    private static final int HOME_SECTION_SIZE = 4;

    private final ContentService contentService;
    private final SearchService searchService;
    private final ProfileService profileService;

    public PublicPagesController(ContentService contentService,
                                 SearchService searchService,
                                 ProfileService profileService) {
        this.contentService = contentService;
        this.searchService = searchService;
        this.profileService = profileService;
    }

    @GetMapping("/")
    public String home(Model model) {
        SiteProfile profile = profileService.getOrCreate();
        model.addAttribute("profile", profile);
        model.addAttribute("avatarUrl", profile.getAvatarMediaId() == null ? null
                : MediaService.publicUrl(profile.getAvatarMediaId(), VariantType.DISPLAY));
        Pageable top = PageRequest.of(0, HOME_SECTION_SIZE);
        model.addAttribute("photos", contentService.publicPage(ContentType.PHOTO, null, null, top).getContent());
        model.addAttribute("blogs", contentService.publicPage(ContentType.BLOG, null, null, top).getContent());
        model.addAttribute("foods", contentService.publicPage(ContentType.FOOD, null, null, top).getContent());
        model.addAttribute("travels", contentService.publicPage(ContentType.TRAVEL, null, null, top).getContent());
        model.addAttribute("pageTitle", profile.getName());
        return "public/home";
    }

    @GetMapping("/photos")
    public String photos(@RequestParam(required = false) String page,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String tag,
                         Model model) {
        Pageable pageable = PageRequestParser.parse(page, size);
        model.addAttribute("page", contentService.publicPage(ContentType.PHOTO, tag, null, pageable));
        model.addAttribute("tag", tag);
        model.addAttribute("pageTitle", "相册");
        return "public/photos";
    }

    @GetMapping("/photos/{slug}")
    public String photoDetail(@PathVariable String slug, Model model) {
        var detail = contentService.loadPublicDetail(ContentType.PHOTO, slug);
        model.addAttribute("content", detail);
        model.addAttribute("pageTitle", "相册");
        model.addAttribute("pageDescription", detail.summary());
        return "public/photo-detail";
    }

    @GetMapping("/blog")
    public String blog(@RequestParam(required = false) String page,
                       @RequestParam(required = false) String size,
                       @RequestParam(required = false) String tag,
                       Model model) {
        Pageable pageable = PageRequestParser.parse(page, size);
        model.addAttribute("page", contentService.publicPage(ContentType.BLOG, tag, null, pageable));
        model.addAttribute("tag", tag);
        model.addAttribute("pageTitle", "Blog");
        return "public/blog-list";
    }

    @GetMapping("/blog/{slug}")
    public String blogDetail(@PathVariable String slug, Model model) {
        var detail = contentService.loadPublicDetail(ContentType.BLOG, slug);
        model.addAttribute("content", detail);
        model.addAttribute("pageTitle", detail.title());
        model.addAttribute("pageDescription", detail.summary());
        return "public/blog-detail";
    }

    @GetMapping("/life/food")
    public String food(@RequestParam(required = false) String page,
                       @RequestParam(required = false) String size,
                       @RequestParam(required = false) String tag,
                       @RequestParam(required = false) String category,
                       Model model) {
        Pageable pageable = PageRequestParser.parse(page, size);
        model.addAttribute("page", contentService.publicPage(ContentType.FOOD, tag, category, pageable));
        model.addAttribute("tag", tag);
        model.addAttribute("category", category);
        model.addAttribute("pageTitle", "美食菜谱");
        return "public/food-list";
    }

    @GetMapping("/life/food/{slug}")
    public String foodDetail(@PathVariable String slug, Model model) {
        var detail = contentService.loadPublicDetail(ContentType.FOOD, slug);
        model.addAttribute("content", detail);
        model.addAttribute("pageTitle", detail.title());
        model.addAttribute("pageDescription", detail.summary());
        return "public/food-detail";
    }

    @GetMapping("/life/travel")
    public String travel(@RequestParam(required = false) String page,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String tag,
                         @RequestParam(required = false) String region,
                         Model model) {
        Pageable pageable = PageRequestParser.parse(page, size);
        model.addAttribute("page", contentService.publicPage(ContentType.TRAVEL, tag, null, pageable));
        model.addAttribute("tag", tag);
        model.addAttribute("region", region);
        model.addAttribute("pageTitle", "旅行");
        return "public/travel-list";
    }

    @GetMapping("/life/travel/{slug}")
    public String travelDetail(@PathVariable String slug, Model model) {
        var detail = contentService.loadPublicDetail(ContentType.TRAVEL, slug);
        model.addAttribute("content", detail);
        model.addAttribute("pageTitle", detail.title());
        model.addAttribute("pageDescription", detail.summary());
        return "public/travel-detail";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String tag,
                         @RequestParam(required = false) String page,
                         @RequestParam(required = false) String size,
                         Model model) {
        Pageable pageable = PageRequestParser.parse(page, size);
        ContentType contentType = null;
        if (type != null && !type.isBlank()) {
            try {
                contentType = ContentType.from(type);
            } catch (IllegalArgumentException e) {
                throw new com.hubc.magnusblog.common.InvalidInputException("type", "未知的内容类型");
            }
        }
        model.addAttribute("page", searchService.search(q, contentType, tag, pageable));
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        model.addAttribute("tag", tag);
        model.addAttribute("pageTitle", "搜索");
        return "public/search";
    }
}
