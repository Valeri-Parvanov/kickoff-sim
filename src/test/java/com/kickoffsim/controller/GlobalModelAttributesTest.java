package com.kickoffsim.controller;

import com.kickoffsim.service.ChangeRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalModelAttributesTest {

    @Mock
    private ChangeRequestService changeRequestService;

    @InjectMocks
    private GlobalModelAttributes globalModelAttributes;

    private Authentication authWith(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void pendingChangeCount_returnsNull_whenServiceThrows() {
        when(changeRequestService.countPending()).thenThrow(new RuntimeException("DB down"));

        assertNull(globalModelAttributes.pendingChangeCount(authWith("ROLE_ADMIN")));
    }

    @Test
    void myPendingCount_returnsNull_whenServiceThrows() {
        when(changeRequestService.countMyPending(any(Authentication.class)))
                .thenThrow(new RuntimeException("DB down"));

        assertNull(globalModelAttributes.myPendingCount(authWith("ROLE_USER")));
    }

    @Test
    void pendingChangeCount_returnsNull_whenNotAuthenticated() {
        assertNull(globalModelAttributes.pendingChangeCount(null));
        assertNull(globalModelAttributes.myPendingCount(null));
    }

    @Test
    void pendingChangeCount_admin_returnsValue() {
        when(changeRequestService.countPending()).thenReturn(4L);

        org.junit.jupiter.api.Assertions.assertEquals(4L,
                globalModelAttributes.pendingChangeCount(authWith("ROLE_ADMIN")));
    }

    @Test
    void pendingChangeCount_userRole_returnsNullWithoutQuery() {
        assertNull(globalModelAttributes.pendingChangeCount(authWith("ROLE_USER")));
    }

    @Test
    void myPendingCount_admin_returnsNullWithoutQuery() {
        assertNull(globalModelAttributes.myPendingCount(authWith("ROLE_ADMIN")));
    }

    @Test
    void myPendingCount_user_returnsValue() {
        when(changeRequestService.countMyPending(any(Authentication.class))).thenReturn(2L);

        org.junit.jupiter.api.Assertions.assertEquals(2L,
                globalModelAttributes.myPendingCount(authWith("ROLE_USER")));
    }

    @Test
    void pendingChangeCount_notAuthenticated_returnsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertNull(globalModelAttributes.pendingChangeCount(auth));
    }

    @Test
    void myPendingCount_notAuthenticated_returnsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertNull(globalModelAttributes.myPendingCount(auth));
    }
}
