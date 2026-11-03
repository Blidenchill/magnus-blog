package com.hubc.magnusblog.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-side allowlist sanitizer for rich text bodies authored with Quill.
 * Quill owns input experience only; this component owns safety.
 *
 * Allowed: paragraphs, headings h2-h4, bold/italic/underline/strike, lists,
 * blockquote, links (http/https only, rel enforced), images that reference this
 * site's own media endpoint, pre/code. No scripts, events, iframes, inline
 * styles, data URLs or external images.
 */
@Component
public class HtmlSanitizer {

    public static final int MAX_BODY_BYTES = 200 * 1024;

    private static final Pattern MEDIA_URL =
            Pattern.compile("^/media/([0-9a-fA-F-]{36})/display$");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "b", "strong", "i", "em", "u", "s",
                    "blockquote", "ul", "ol", "li",
                    "h2", "h3", "h4", "a", "img", "figure", "figcaption",
                    "pre", "code", "sub", "sup", "span")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt").onElements("img")
            .allowUrlProtocols("http", "https")
            .requireRelNofollowOnLinks()
            .toFactory();

    public record Sanitized(String html, Set<UUID> mediaIds) {
    }

    /**
     * Sanitize raw editor HTML. Returns clean HTML plus the set of media ids
     * referenced through {@code /media/{id}/display} image URLs.
     */
    public Sanitized sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Sanitized("", Set.of());
        }
        if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new InvalidInputException("bodyHtml", "正文超出大小限制（200 KiB）");
        }
        String cleaned = POLICY.sanitize(raw);

        // Post-process with a real DOM parser: enforce same-site media URLs on
        // images, force safe link attributes, extract referenced media ids.
        Document doc = Jsoup.parseBodyFragment(cleaned);
        Set<UUID> mediaIds = new LinkedHashSet<>();

        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            var matcher = MEDIA_URL.matcher(src);
            if (!matcher.matches()) {
                img.remove();
                continue;
            }
            mediaIds.add(UUID.fromString(matcher.group(1)));
            if (!img.hasAttr("alt")) {
                img.attr("alt", "");
            }
            img.attr("loading", "lazy");
        }
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (!(href.startsWith("http://") || href.startsWith("https://"))) {
                a.unwrap();
                continue;
            }
            a.attr("rel", "nofollow noopener noreferrer");
            a.attr("target", "_blank");
        }
        return new Sanitized(doc.body().html(), mediaIds);
    }
}
