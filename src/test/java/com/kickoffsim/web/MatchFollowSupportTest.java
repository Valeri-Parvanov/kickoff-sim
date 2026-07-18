package com.kickoffsim.web;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.model.User;
import com.kickoffsim.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchFollowSupportTest {

    @Mock private NotificationClient notificationClient;
    @Mock private UserService userService;

    @InjectMocks private MatchFollowSupport support;

    @Test
    void nullAuthentication_returnsEmpty() {
        assertThat(support.subscribedMatchIds(null)).isEmpty();
    }

    @Test
    void anonymousAuthentication_returnsEmpty() {
        Authentication anon = new AnonymousAuthenticationToken(
                "key", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThat(support.subscribedMatchIds(anon)).isEmpty();
    }

    @Test
    void notAuthenticated_returnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertThat(support.subscribedMatchIds(auth)).isEmpty();
    }

    @Test
    void authenticated_returnsMatchEntityIds() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        User user = new User();
        user.setId(userId);
        when(userService.findByUsername("alice")).thenReturn(user);

        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(UUID.randomUUID());
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(matchSub, teamSub));

        assertThat(support.subscribedMatchIds(auth)).containsExactly(matchId);
    }

    @Test
    void serviceThrows_returnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenThrow(new RuntimeException("down"));

        assertThat(support.subscribedMatchIds(auth)).isEmpty();
    }
}
