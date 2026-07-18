package com.kickoffsim.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SortSupportTest {

    private final Map<String, String> allowed = Map.of("name", "name", "city", "city");
    private final Sort defaultSort = Sort.by("id");

    @Test
    void resolve_nullSortKey_returnsDefaultSort() {
        Sort result = SortSupport.resolve(null, null, allowed, defaultSort);

        assertThat(result).isEqualTo(defaultSort);
    }

    @Test
    void resolve_unknownSortKey_returnsDefaultSort() {
        Sort result = SortSupport.resolve("unknown", null, allowed, defaultSort);

        assertThat(result).isEqualTo(defaultSort);
    }

    @Test
    void resolve_knownSortKey_ascendingByDefault() {
        Sort result = SortSupport.resolve("name", null, allowed, defaultSort);

        assertThat(result.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void resolve_knownSortKey_explicitAsc() {
        Sort result = SortSupport.resolve("name", "asc", allowed, defaultSort);

        assertThat(result.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void resolve_knownSortKey_descDirection_caseInsensitive() {
        Sort result = SortSupport.resolve("city", "DESC", allowed, defaultSort);

        assertThat(result.getOrderFor("city").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
