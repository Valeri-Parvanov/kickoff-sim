package com.kickoffsim.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    public static final String LOGIN_AT_SESSION_ATTR = "loginAt";

    private final NotFoundAccessDeniedHandler notFoundAccessDeniedHandler;

    public SecurityConfig(NotFoundAccessDeniedHandler notFoundAccessDeniedHandler) {
        this.notFoundAccessDeniedHandler = notFoundAccessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler loginTimestampingSuccessHandler() {
        SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler("/");
        delegate.setAlwaysUseDefaultTargetUrl(true);
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            request.getSession().setAttribute(LOGIN_AT_SESSION_ATTR, LocalDateTime.now());
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/css/**", "/js/**", "/teams/*/logo").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(loginTimestampingSuccessHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(notFoundAccessDeniedHandler));

        return http.build();
    }
}
