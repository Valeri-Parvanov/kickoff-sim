package com.kickoffsim.security;

import com.kickoffsim.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    private final UserService userService;

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        userService.recordLoginFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        userService.recordLoginSuccess(event.getAuthentication().getName());
    }
}
