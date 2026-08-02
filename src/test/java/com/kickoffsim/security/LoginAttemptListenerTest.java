package com.kickoffsim.security;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.model.User;
import com.kickoffsim.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptListenerTest {

    @Mock private UserService userService;
    @Mock private NotificationClient notificationClient;

    @InjectMocks
    private LoginAttemptListener listener;

    @Test
    void onFailure_delegatesToRecordLoginFailure() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "wrong");
        AuthenticationFailureBadCredentialsEvent event =
                new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("bad"));

        listener.onFailure(event);

        verify(userService).recordLoginFailure("alice");
        verifyNoInteractions(notificationClient);
    }

    @Test
    void onSuccess_recordsLoginAndMarksPreviousNotificationsRead() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userService.findByUsername("alice")).thenReturn(user);
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "correct");

        listener.onSuccess(new AuthenticationSuccessEvent(auth));

        verify(userService).recordLoginSuccess("alice");
        verify(notificationClient).markAllRead(userId);
    }

    @Test
    void onSuccess_swallowsNotificationServiceFailure() {
        when(userService.findByUsername("alice")).thenThrow(new IllegalStateException("service down"));
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "correct");

        assertThatCode(() -> listener.onSuccess(new AuthenticationSuccessEvent(auth)))
                .doesNotThrowAnyException();

        verify(userService).recordLoginSuccess("alice");
        verifyNoInteractions(notificationClient);
    }
}
