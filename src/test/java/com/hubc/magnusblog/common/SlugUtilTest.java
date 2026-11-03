package com.hubc.magnusblog.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilTest {

    @Test
    void acceptsValidSlugs() {
        assertThat(SlugUtil.isValid("my-first-post")).isTrue();
        assertThat(SlugUtil.isValid("a")).isTrue();
        assertThat(SlugUtil.isValid("photo-2026")).isTrue();
    }

    @Test
    void rejectsInvalidSlugs() {
        assertThat(SlugUtil.isValid(null)).isFalse();
        assertThat(SlugUtil.isValid("")).isFalse();
        assertThat(SlugUtil.isValid("UPPER")).isFalse();
        assertThat(SlugUtil.isValid("-lead")).isFalse();
        assertThat(SlugUtil.isValid("trail-")).isFalse();
        assertThat(SlugUtil.isValid("has space")).isFalse();
        assertThat(SlugUtil.isValid("中文")).isFalse();
        assertThat(SlugUtil.isValid("x".repeat(81))).isFalse();
        assertThat(SlugUtil.isValid("a/b")).isFalse();
    }

    @Test
    void normalizesUserInput() {
        assertThat(SlugUtil.normalize("Hello World")).isEqualTo("hello-world");
        assertThat(SlugUtil.normalize("  --Weird__Input-- ")).isEqualTo("weird-input");
        assertThat(SlugUtil.normalize("没有拉丁字母")).isNull();
    }

    @Test
    void randomSlugIsValidAndUniqueEnough() {
        String a = SlugUtil.randomSlug("blog");
        String b = SlugUtil.randomSlug("blog");
        assertThat(SlugUtil.isValid(a)).isTrue();
        assertThat(SlugUtil.isValid(b)).isTrue();
        assertThat(a).isNotEqualTo(b);
    }
}
