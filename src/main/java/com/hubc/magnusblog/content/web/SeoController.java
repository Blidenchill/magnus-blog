package com.hubc.magnusblog.content.web;

import com.hubc.magnusblog.common.ContentType;
import com.hubc.magnusblog.config.AppProperties;
import com.hubc.magnusblog.content.ContentEntry;
import com.hubc.magnusblog.content.ContentEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** robots.txt and sitemap.xml expose public content only. */
@RestController
public class SeoController {

    private final ContentEntryRepository entryRepository;
    private final String baseUrl;

    public SeoController(ContentEntryRepository entryRepository, AppProperties properties) {
        this.entryRepository = entryRepository;
        this.baseUrl = stripTrailingSlash(properties.site().baseUrl());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Disallow: /admin
                Disallow: /admin/
                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : List.of("/", "/photos", "/blog", "/life/food", "/life/travel", "/search")) {
            xml.append("  <url><loc>").append(baseUrl).append(path).append("</loc></url>\n");
        }
        for (ContentType type : ContentType.values()) {
            // sitemap size is bounded by content volume; page through in chunks
            var page = entryRepository.findPublicPage(type, null, null, PageRequest.of(0, 1000));
            for (ContentEntry entry : page.getContent()) {
                xml.append("  <url><loc>").append(baseUrl).append(detailPath(type, entry.getSlug()))
                        .append("</loc></url>\n");
            }
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    static String detailPath(ContentType type, String slug) {
        return switch (type) {
            case PHOTO -> "/photos/" + slug;
            case BLOG -> "/blog/" + slug;
            case FOOD -> "/life/food/" + slug;
            case TRAVEL -> "/life/travel/" + slug;
        };
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
