package com.hubc.magnusblog.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    @Test
    void escapesLikeWildcards() {
        assertThat(SearchService.escapeLike("100%")).isEqualTo("100\\%");
        assertThat(SearchService.escapeLike("a_b")).isEqualTo("a\\_b");
        assertThat(SearchService.escapeLike("back\\slash")).isEqualTo("back\\\\slash");
    }

    @Test
    void blankBecomesNull() {
        assertThat(SearchService.escapeLike(null)).isNull();
        assertThat(SearchService.escapeLike("   ")).isNull();
    }
}
