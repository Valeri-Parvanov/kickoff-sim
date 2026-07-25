package com.kickoffsim.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock private NotFoundAccessDeniedHandler notFoundAccessDeniedHandler;
    @Mock private Authentication authentication;

    @Test
    void loginTimestampingSuccessHandler_stampsSessionAndRedirectsHome() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig(notFoundAccessDeniedHandler);
        AuthenticationSuccessHandler handler = securityConfig.loginTimestampingSuccessHandler();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        Object loginAt = request.getSession().getAttribute(SecurityConfig.LOGIN_AT_SESSION_ATTR);
        assertThat(loginAt).isInstanceOf(LocalDateTime.class);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void passwordEncoder_encodesAndMatches() {
        SecurityConfig securityConfig = new SecurityConfig(notFoundAccessDeniedHandler);
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String hash = encoder.encode("secret");

        assertThat(encoder.matches("secret", hash)).isTrue();
    }
}
