package com.hubc.magnusblog.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void stripsScriptsAndEventHandlers() {
        var result = sanitizer.sanitize(
                "<p onclick=\"alert(1)\">hi<script>alert(2)</script></p>");
        assertThat(result.html()).doesNotContain("script");
        assertThat(result.html()).doesNotContain("onclick");
        assertThat(result.html()).contains("hi");
    }

    @Test
    void removesExternalImagesButKeepsOwnMedia() {
        UUID id = UUID.randomUUID();
        var result = sanitizer.sanitize(
                "<p><img src=\"https://evil.example/x.png\">"
                        + "<img src=\"/media/" + id + "/display\" alt=\"ok\"></p>");
        assertThat(result.html()).doesNotContain("evil.example");
        assertThat(result.html()).contains("/media/" + id + "/display");
        assertThat(result.mediaIds()).containsExactly(id);
    }

    @Test
    void forcesSafeLinks() {
        var result = sanitizer.sanitize(
                "<a href=\"https://example.com\">ok</a><a href=\"javascript:alert(1)\">bad</a>");
        assertThat(result.html()).contains("rel=\"nofollow noopener noreferrer\"");
        assertThat(result.html()).doesNotContain("javascript:");
    }

    @Test
    void rejectsOversizeBody() {
        String big = "<p>" + "x".repeat(HtmlSanitizer.MAX_BODY_BYTES) + "</p>";
        assertThatThrownBy(() -> sanitizer.sanitize(big))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void keepsBasicFormatting() {
        var result = sanitizer.sanitize(
                "<h2>T</h2><blockquote><p>q</p></blockquote><ul><li>a</li></ul><p><strong>b</strong></p>");
        assertThat(result.html()).contains("<h2>");
        assertThat(result.html()).contains("<blockquote>");
        assertThat(result.html()).contains("<li>a</li>");
        assertThat(result.html()).contains("<strong>b</strong>");
    }
}
