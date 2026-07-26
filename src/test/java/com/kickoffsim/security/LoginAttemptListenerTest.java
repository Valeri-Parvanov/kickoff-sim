package com.kickoffsim.security;

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

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAttemptListenerTest {

    @Mock private UserService userService;

    @InjectMocks
    private LoginAttemptListener listener;

    @Test
    void onFailure_delegatesToRecordLoginFailure() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "wrong");
        AuthenticationFailureBadCredentialsEvent event =
                new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("bad"));

        listener.onFailure(event);

        verify(userService).recordLoginFailure("alice");
    }

    @Test
    void onSuccess_delegatesToRecordLoginSuccess() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "correct");
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        listener.onSuccess(event);

        verify(userService).recordLoginSuccess("alice");
    }
}
