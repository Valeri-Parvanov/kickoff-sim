package com.kickoffsim.web;

import com.kickoffsim.client.BroadcastRequest;
import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.NotificationDto;
import com.kickoffsim.client.NotifyRequest;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class SsePushingNotificationClient implements NotificationClient {

    private final NotificationClient delegate;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public SubscriptionDto subscribe(SubscriptionRequest request) {
        return delegate.subscribe(request);
    }

    @Override
    public void unsubscribe(UUID id) {
        delegate.unsubscribe(id);
    }

    @Override
    public List<SubscriptionDto> getSubscriptions(UUID userId) {
        return delegate.getSubscriptions(userId);
    }

    @Override
    public List<SubscriptionDto> getSubscriptionsForEntities(List<UUID> entityIds) {
        return delegate.getSubscriptionsForEntities(entityIds);
    }

    @Override
    public boolean isSubscribed(UUID userId, UUID entityId) {
        return delegate.isSubscribed(userId, entityId);
    }

    @Override
    public List<UUID> broadcast(BroadcastRequest request) {
        List<UUID> notifiedUserIds = delegate.broadcast(request);
        sseEmitterRegistry.push(notifiedUserIds, request.getMessage(), request.getType());
        return notifiedUserIds;
    }

    @Override
    public void notifyUser(NotifyRequest request) {
        delegate.notifyUser(request);
    }

    @Override
    public List<NotificationDto> getNotifications(UUID userId) {
        return delegate.getNotifications(userId);
    }
}
