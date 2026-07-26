package com.kickoffsim.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EagerCsrfTokenFilterTest {

    private final EagerCsrfTokenFilter filter = new EagerCsrfTokenFilter();

    @Test
    void doFilterInternal_tokenPresent_resolvesTokenBeforeContinuing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicInteger resolutions = new AtomicInteger();
        CsrfToken deferred = new CsrfToken() {
            @Override
            public String getHeaderName() {
                return "X-CSRF-TOKEN";
            }

            @Override
            public String getParameterName() {
                return "_csrf";
            }

            @Override
            public String getToken() {
                resolutions.incrementAndGet();
                return "resolved-token";
            }
        };
        request.setAttribute(CsrfToken.class.getName(), deferred);

        filter.doFilter(request, response, chain);

        assertThat(resolutions.get()).isEqualTo(1);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_concreteToken_continuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.setAttribute(CsrfToken.class.getName(),
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "abc"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_noToken_continuesChainWithoutFailing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(CsrfToken.class.getName())).isNull();
        verify(chain).doFilter(request, response);
    }
}
