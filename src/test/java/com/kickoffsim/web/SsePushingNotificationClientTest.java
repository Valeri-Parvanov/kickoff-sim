package com.kickoffsim.web;

import com.kickoffsim.client.BroadcastRequest;
import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.NotificationDto;
import com.kickoffsim.client.NotifyRequest;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsePushingNotificationClientTest {

    @Mock private NotificationClient delegate;
    @Mock private SseEmitterRegistry sseEmitterRegistry;

    private SsePushingNotificationClient client;

    @BeforeEach
    void setUp() {
        client = new SsePushingNotificationClient(delegate, sseEmitterRegistry);
    }

    @Test
    void subscribe_delegates() {
        SubscriptionRequest request = new SubscriptionRequest(UUID.randomUUID(), "TEAM", UUID.randomUUID());
        SubscriptionDto response = new SubscriptionDto();
        when(delegate.subscribe(request)).thenReturn(response);

        assertThat(client.subscribe(request)).isSameAs(response);
    }

    @Test
    void unsubscribe_delegates() {
        UUID id = UUID.randomUUID();
        client.unsubscribe(id);
        verify(delegate).unsubscribe(id);
    }

    @Test
    void getSubscriptions_delegates() {
        UUID userId = UUID.randomUUID();
        List<SubscriptionDto> subs = List.of(new SubscriptionDto());
        when(delegate.getSubscriptions(userId)).thenReturn(subs);

        assertThat(client.getSubscriptions(userId)).isSameAs(subs);
    }

    @Test
    void getSubscriptionsForEntities_delegates() {
        List<UUID> ids = List.of(UUID.randomUUID());
        List<SubscriptionDto> subs = List.of(new SubscriptionDto());
        when(delegate.getSubscriptionsForEntities(ids)).thenReturn(subs);

        assertThat(client.getSubscriptionsForEntities(ids)).isSameAs(subs);
    }

    @Test
    void isSubscribed_delegates() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(delegate.isSubscribed(userId, entityId)).thenReturn(true);

        assertThat(client.isSubscribed(userId, entityId)).isTrue();
    }

    @Test
    void broadcast_delegatesAndPushesToSse() {
        BroadcastRequest request = new BroadcastRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "GOAL for Home! Ivan Petrov 12' — Home 1:0 Away", "GOAL");
        List<UUID> notified = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(delegate.broadcast(request)).thenReturn(notified);

        List<UUID> result = client.broadcast(request);

        assertThat(result).isSameAs(notified);
        verify(sseEmitterRegistry).push(notified, request.getMessage(), request.getType());
    }

    @Test
    void notifyUser_delegates() {
        NotifyRequest request = new NotifyRequest(UUID.randomUUID(), UUID.randomUUID(), "hi", "MATCH_UPDATE");
        client.notifyUser(request);
        verify(delegate).notifyUser(request);
    }

    @Test
    void getNotifications_delegates() {
        UUID userId = UUID.randomUUID();
        List<NotificationDto> notifications = List.of(new NotificationDto());
        when(delegate.getNotifications(userId)).thenReturn(notifications);

        assertThat(client.getNotifications(userId)).isSameAs(notifications);
    }
}
