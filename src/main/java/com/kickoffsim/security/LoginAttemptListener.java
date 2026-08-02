package com.kickoffsim.security;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptListener {

    private final UserService userService;
    private final NotificationClient notificationClient;

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        userService.recordLoginFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        userService.recordLoginSuccess(username);
        markPreviousNotificationsRead(username);
    }

    private void markPreviousNotificationsRead(String username) {
        try {
            notificationClient.markAllRead(userService.findByUsername(username).getId());
        } catch (Exception e) {
            log.warn("Failed to mark notifications read on login for {}: {}", username, e.getMessage());
        }
    }
}
