package com.kickoffsim.controller;

import com.kickoffsim.service.ChangeRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalModelAttributesQueryTest {

    private final GlobalModelAttributes attributes =
            new GlobalModelAttributes(mock(ChangeRequestService.class));

    @Test
    void currentPath_returnsRequestUri() {
        assertThat(attributes.currentPath(requestWith("/leagues/wizard", null)))
                .isEqualTo("/leagues/wizard");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void currentQuery_blankQuery_returnsNull(String query) {
        assertThat(attributes.currentQuery(requestWith("/matches", query))).isNull();
    }

    @Test
    void currentQuery_nullQuery_returnsNull() {
        assertThat(attributes.currentQuery(requestWith("/matches", null))).isNull();
    }

    @Test
    void currentQuery_keepsOtherParameters() {
        assertThat(attributes.currentQuery(requestWith("/matches", "format=10&page=2")))
                .isEqualTo("format=10&page=2");
    }

    @Test
    void currentQuery_dropsExistingLangParameter() {
        assertThat(attributes.currentQuery(requestWith("/matches", "format=10&lang=de")))
                .isEqualTo("format=10");
    }

    @Test
    void currentQuery_dropsRepeatedLangParameters() {
        assertThat(attributes.currentQuery(requestWith("/matches", "lang=en&format=8&lang=bg")))
                .isEqualTo("format=8");
    }

    @Test
    void currentQuery_onlyLangParameter_returnsNull() {
        assertThat(attributes.currentQuery(requestWith("/matches", "lang=de"))).isNull();
    }

    @Test
    void currentQuery_skipsEmptySegments() {
        assertThat(attributes.currentQuery(requestWith("/matches", "format=8&&page=1")))
                .isEqualTo("format=8&page=1");
    }

    @Test
    void currentQuery_keepsParameterMerelyStartingWithLang() {
        assertThat(attributes.currentQuery(requestWith("/matches", "language=de")))
                .isEqualTo("language=de");
    }

    private HttpServletRequest requestWith(String uri, String query) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getQueryString()).thenReturn(query);
        return request;
    }
}
