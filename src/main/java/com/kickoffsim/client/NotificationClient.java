package com.kickoffsim.client;

import feign.Param;
import feign.RequestLine;
import feign.Headers;

import java.util.List;
import java.util.UUID;

@Headers("Content-Type: application/json")
public interface NotificationClient {

    @RequestLine("POST /api/subscriptions")
    SubscriptionDto subscribe(SubscriptionRequest request);

    @RequestLine("DELETE /api/subscriptions/{id}")
    void unsubscribe(@Param("id") UUID id);

    @RequestLine("GET /api/subscriptions?userId={userId}")
    List<SubscriptionDto> getSubscriptions(@Param("userId") UUID userId);

    @RequestLine("GET /api/subscriptions/by-entities?entityIds={entityIds}")
    List<SubscriptionDto> getSubscriptionsForEntities(@Param("entityIds") List<UUID> entityIds);

    @RequestLine("GET /api/subscriptions/check?userId={userId}&entityId={entityId}")
    boolean isSubscribed(@Param("userId") UUID userId, @Param("entityId") UUID entityId);

    @RequestLine("POST /api/notifications/broadcast")
    List<UUID> broadcast(BroadcastRequest request);

    @RequestLine("POST /api/notifications")
    void notifyUser(NotifyRequest request);

    @RequestLine("GET /api/notifications?userId={userId}")
    List<NotificationDto> getNotifications(@Param("userId") UUID userId);
}
