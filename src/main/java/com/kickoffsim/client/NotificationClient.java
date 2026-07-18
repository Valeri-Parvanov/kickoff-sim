package bg.softuni.footballleague.client;

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

    @RequestLine("GET /api/subscriptions/check?userId={userId}&entityId={entityId}")
    boolean isSubscribed(@Param("userId") UUID userId, @Param("entityId") UUID entityId);

    @RequestLine("POST /api/notifications/broadcast")
    void broadcast(BroadcastRequest request);

    @RequestLine("POST /api/notifications")
    void notifyUser(NotifyRequest request);

    @RequestLine("GET /api/notifications?userId={userId}")
    List<NotificationDto> getNotifications(@Param("userId") UUID userId);

    @RequestLine("GET /api/notifications/unread-count?userId={userId}")
    long getUnreadCount(@Param("userId") UUID userId);

    @RequestLine("PUT /api/notifications/{id}/read")
    void markRead(@Param("id") UUID id);

    @RequestLine("PUT /api/notifications/read-all?userId={userId}")
    void markAllRead(@Param("userId") UUID userId);

    @RequestLine("DELETE /api/notifications?userId={userId}")
    void clearAll(@Param("userId") UUID userId);
}
